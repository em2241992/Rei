package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.ai.AdhdExecutiveBriefing
import com.example.ai.BrainDumpGateType
import com.example.ai.BrainDumpPreCheckGate
import com.example.ai.ClarifyingQuestionItem
import com.example.data.local.CommsStrategyLens
import com.example.data.local.ReiTask
import com.example.ui.theme.AmberSteadyRisk
import com.example.ui.theme.CoralQ1Urgent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldAiCore
import com.example.ui.theme.SlateParkedQ4
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.VioletQ2Build

/**
 * Renders markdown-style `**bold**` segments cleanly in Compose without showing raw asterisks.
 */
@Composable
fun BoldAnchoredText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    val annotated = remember(text) {
        buildAnnotatedString {
            val parts = text.split("**")
            parts.forEachIndexed { index, part ->
                if (index % 2 == 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(part)
                    }
                } else {
                    append(part)
                }
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier
    )
}

/**
 * STEP 1 (Clarification Check), STEP 2 (Northwell Calendar Review), & STEP 6 (Frustration / Low-Energy Gate) Dialog.
 * Enforces the rule: "If any vague tasks or massive projects exist, STOP. Ask the user ONE specific, short question
 * to find the actual next physical step. Do not proceed to step 2/3 until clarified."
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClarifyWithReiDialog(
    sourceTitleOrText: String,
    targetTask: ReiTask?,
    questions: List<ClarifyingQuestionItem>,
    preCheckGate: BrainDumpPreCheckGate? = null,
    onDismiss: () -> Unit,
    onApplyClarification: (String) -> Unit,
    onSkipGateAndOrganizeAsIs: (() -> Unit)? = null
) {
    val selectedAnswers = remember(questions, preCheckGate) { mutableStateListOf<String>() }
    var extraDetailText by remember(questions, preCheckGate) { mutableStateOf("") }
    val gateType = preCheckGate?.gateType ?: BrainDumpGateType.STEP_1_VAGUE_OR_MASSIVE_CLARIFY

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                val badgeText = when (gateType) {
                    BrainDumpGateType.STEP_2_CALENDAR_NORTHWELL_REVIEW ->
                        "📅 STEP 2 • CONDITIONAL CALENDAR REVIEW"
                    BrainDumpGateType.STEP_6_FRUSTRATION_WALL_DETECTED ->
                        "🛑 STEP 6 • PROACTIVE STALL / FRUSTRATION DETECTION"
                    BrainDumpGateType.STEP_6_LOW_ENERGY_OVERRIDE ->
                        "🟢 STEP 6 • QUICK WIN ENERGY OVERRIDE"
                    else ->
                        "🛑 STEP 1 • THE CLARIFICATION GATE (STOP BEFORE MATRIX)"
                }
                val headlineText = when (gateType) {
                    BrainDumpGateType.STEP_2_CALENDAR_NORTHWELL_REVIEW ->
                        "Do any of these need to be tied to existing tasks or generate new ones?"
                    BrainDumpGateType.STEP_6_FRUSTRATION_WALL_DETECTED ->
                        "Hitting a wall on this? Tap Smaller Step to shrink it, or I'm Stuck to leave a breadcrumb and switch tasks."
                    BrainDumpGateType.STEP_6_LOW_ENERGY_OVERRIDE ->
                        "Low Bandwidth — Surface 1 Easy Quick Win (⏱️ 1-2 min)"
                    else ->
                        "This looks broad. What is the single next physical action to start it?"
                }
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CoralQ1Urgent
                )
                Text(
                    text = headlineText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Flagged Vague/Massive Task or Input Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (targetTask != null) "TARGET TASK:" else "FLAGGED IN BRAIN DUMP:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelemetryCyan
                        )
                        Text(
                            text = (preCheckGate?.flaggedItemText?.takeIf { it.isNotBlank() } ?: sourceTitleOrText)
                                .ifBlank { "(Enter or pick the concrete physical action below)" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Step 2 Conditional: Chronological Northwell Meetings List
                if (gateType == BrainDumpGateType.STEP_2_CALENDAR_NORTHWELL_REVIEW &&
                    preCheckGate != null &&
                    preCheckGate.calendarMeetings.isNotEmpty()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElectricIndigo.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "📅 CHRONOLOGICAL NORTHWELL MEETINGS:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElectricIndigo
                            )
                            preCheckGate.calendarMeetings.forEach { m ->
                                Text(
                                    text = "• **${m.timeSlot}** — ${m.title} (${m.stakeholders.joinToString(", ")})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Render the ONE specific question (and optional deadline/waiting-on tag)
                questions.forEach { qItem ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = qItem.dimensionLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElectricIndigo
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BoldAnchoredText(
                                text = qItem.question,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                qItem.quickChips.forEach { chip ->
                                    val selected = selectedAnswers.contains(chip)
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (selected) {
                                                selectedAnswers.remove(chip)
                                            } else {
                                                selectedAnswers.add(chip)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = chip,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricIndigo.copy(alpha = 0.25f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = extraDetailText,
                    onValueChange = { extraDetailText = it },
                    label = { Text("Or type your exact physical next step / 📅 Due Date / ⏳ Waiting On") },
                    placeholder = { Text("e.g., Call mechanic to book oil change 📅 Due: Tomorrow") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clarify_extra_detail_input")
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                if (onSkipGateAndOrganizeAsIs != null) {
                    TextButton(onClick = onSkipGateAndOrganizeAsIs) {
                        Text("Skip & Add Anyway")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val combinedClarification = buildString {
                        if (selectedAnswers.isNotEmpty()) {
                            append(selectedAnswers.joinToString(" • "))
                        }
                        if (extraDetailText.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(extraDetailText.trim())
                        }
                    }.ifBlank {
                        questions.firstOrNull()?.quickChips?.firstOrNull()
                            ?: "Open note & write the first 1-line physical action (⏱️ Time to complete: 2 minutes)"
                    }
                    onApplyClarification(combinedClarification)
                },
                modifier = Modifier.testTag("confirm_clarify_with_rei_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (targetTask != null) "Apply Physical Step" else "Clarify & Build Matrix")
            }
        }
    )
}

/**
 * Displays the concise, visual-anchor formatted ADHD Executive Briefing right after
 * organizing a Brain Dump or running an existing prompt on a task, so the user always sees
 * immediate, zero-fluff feedback.
 */
@Composable
fun AdhdExecutiveBriefingDialog(
    briefing: AdhdExecutiveBriefing,
    onDismiss: () -> Unit,
    onShrinkTopTask: (() -> Unit)? = null,
    onOpenCommsDraft: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "⚡ REI ADHD EXECUTIVE ASSISTANT • ZERO-FRICTION BRIEF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldAiCore
                )
                Text(
                    text = briefing.protocolHeadline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!briefing.emergencyNotice.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AmberSteadyRisk.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, AmberSteadyRisk)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            BoldAnchoredText(
                                text = briefing.emergencyNotice,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Step 4 & Step 5: The Top Task, Ridiculously Small Micro-Step, & Time Estimate
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElectricIndigo.copy(alpha = 0.14f),
                    border = BorderStroke(1.5.dp, ElectricIndigo)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎯 STEP 4 & 5 • START HERE RIGHT NOW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricIndigo
                        )
                        BoldAnchoredText(
                            text = "**Top Task**: ${briefing.topTaskTitle}",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        BoldAnchoredText(
                            text = "**First Physical Micro-Step**: ${briefing.topTaskMicroStep}",
                            color = EmeraldAiCore
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldAiCore.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = briefing.timeEstimateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAiCore,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Step 3: Eisenhower Matrix Bullets
                if (briefing.matrixBullets.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "📊 EISENHOWER MATRIX & VISUAL ANCHORS:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TelemetryCyan
                            )
                            briefing.matrixBullets.forEach { bullet ->
                                BoldAnchoredText(
                                    text = "• $bullet",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Auto-Generated Waiting-On Follow-Ups
                if (briefing.waitingOnAutoFollowUps.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TelemetryCyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "⏳ AUTO-CREATED 'WAITING ON' FOLLOW-UPS:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TelemetryCyan
                            )
                            briefing.waitingOnAutoFollowUps.forEach { item ->
                                BoldAnchoredText(
                                    text = "• $item",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Rough First Draft Preview (if Draft It was triggered)
                if (!briefing.roughDraftPreview.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = VioletQ2Build.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, VioletQ2Build)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "📝 ROUGH FIRST DRAFT (EDIT LOGIC & INTENT ONLY):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = VioletQ2Build
                            )
                            Text(
                                text = briefing.roughDraftPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (onShrinkTopTask != null) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onShrinkTopTask()
                    }
                ) {
                    Text("🔬 Shrink Even Smaller")
                }
            } else if (onOpenCommsDraft != null) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenCommsDraft()
                    }
                ) {
                    Text("Open Full Comms Studio")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_adhd_briefing_button")
            ) {
                Text("Got It — Let's Go")
            }
        }
    )
}

/**
 * Dialog that lets the user run ANY of Rei's ADHD Executive Assistant Prompts & Emergency Commands
 * on any existing task (including splitting a clumped multi-bullet task into separate Matrix tasks).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RunPromptOnTaskDialog(
    initialTask: ReiTask?,
    allTasks: List<ReiTask>,
    onDismiss: () -> Unit,
    onAskMoreDetailForTask: (ReiTask) -> Unit,
    onSplitTaskIntoMultipleTasks: (ReiTask) -> Unit,
    onShrinkTask: (ReiTask) -> Unit,
    onStuckBreadcrumbForTask: (ReiTask) -> Unit,
    onTagWaitingOnPersonForTask: (ReiTask, String) -> Unit,
    onSnoozeTask: (ReiTask, String) -> Unit,
    onRabbitHoleStopForTask: (ReiTask) -> Unit,
    onVerify99ForTask: (ReiTask) -> Unit,
    onReOrganizeTaskIntoSecondBrain: (ReiTask) -> Unit,
    onPrepMeetingForTask: (ReiTask) -> Unit,
    onRunCommsLensForTask: (ReiTask, CommsStrategyLens) -> Unit
) {
    val openTasks = remember(allTasks) {
        allTasks.ifEmpty { listOfNotNull(initialTask) }
    }
    var selectedTask by remember(initialTask, openTasks) {
        mutableStateOf(initialTask ?: openTasks.firstOrNull())
    }
    var showTaskPicker by remember { mutableStateOf(initialTask == null) }
    var showWaitingOnPicker by remember { mutableStateOf(false) }
    var customWaitingPerson by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "⚡ RUN ADHD EXECUTIVE PROMPTS ON EXISTING TASK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Select Protocol or Command",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeTask = selectedTask
                if (activeTask == null) {
                    Text(
                        text = "No existing tasks found. Add a task first using the Quick Dump Bar.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // Target Task Selector Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElectricIndigo.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ElectricIndigo),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showTaskPicker = !showTaskPicker }
                            .testTag("run_prompt_target_task_selector")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TARGET TASK (${activeTask.quadrant.shortCode})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricIndigo
                                )
                                if (openTasks.size > 1) {
                                    Text(
                                        text = if (showTaskPicker) "Hide List ▲" else "Switch Task (${openTasks.size}) ▼",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeTask.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (showTaskPicker && openTasks.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "TAP TO SWITCH TARGET TASK:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                openTasks.forEach { t ->
                                    val isCurrent = t.id == activeTask.id
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) ElectricIndigo.copy(alpha = 0.2f) else Color.Transparent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedTask = t
                                                showTaskPicker = false
                                            }
                                    ) {
                                        Text(
                                            text = "${t.quadrant.shortCode} • ${t.title}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // 1. Step 1: The Clarification Check (1-Question Physical Step Probe)
                    PromptOptionRowCard(
                        badge = "🛑 STEP 1 • THE CLARIFICATION CHECK",
                        title = "Ask 1 Question to Find Actual Next Physical Step",
                        subtitle = "Stops & probes vague or massive tasks so you have a concrete <2m physical starter action",
                        icon = Icons.Default.HelpOutline,
                        accentColor = CoralQ1Urgent,
                        testTag = "run_prompt_clarify_task",
                        onClick = { onAskMoreDetailForTask(activeTask) }
                    )

                    // 2. Split Combined/Bullet Task into Separate Matrix Tasks
                    PromptOptionRowCard(
                        badge = "🔀 MULTI-TASK SPLITTER",
                        title = "Split Bullets / Clauses into Separate Matrix Tasks",
                        subtitle = "Unpacks a combined task or Second Brain list into individual Q1–Q4 tasks with 📅 Due & ⏱️ Time estimates",
                        icon = Icons.Default.CallSplit,
                        accentColor = EmeraldAiCore,
                        testTag = "run_prompt_split_task",
                        onClick = { onSplitTaskIntoMultipleTasks(activeTask) }
                    )

                    // 3. Step 6: "Shrink It" (Smaller Physical Action + Time Estimate)
                    PromptOptionRowCard(
                        badge = "🔬 STEP 6 • 'SHRINK IT'",
                        title = "Break Current Micro-Step into Even Smaller Action",
                        subtitle = "Creates a frictionless 1-minute physical starter step + shows ADHD Executive Briefing",
                        icon = Icons.Default.Compress,
                        accentColor = TelemetryCyan,
                        testTag = "run_prompt_shrink_task",
                        onClick = { onShrinkTask(activeTask) }
                    )

                    // 4. Step 6: "I'm Stuck" / Breadcrumb Deferral
                    PromptOptionRowCard(
                        badge = "🍞 STEP 6 • 'I'M STUCK' (BREADCRUMB DEFERRAL)",
                        title = "Ask 'Why Are You Stuck?', Log Roadblock & Defer",
                        subtitle = "Logs your exact roadblock in memory, defers this task, and points to a fresh task",
                        icon = Icons.Default.BookmarkAdded,
                        accentColor = AmberSteadyRisk,
                        testTag = "run_prompt_breadcrumb_task",
                        onClick = { onStuckBreadcrumbForTask(activeTask) }
                    )

                    // 5. Step 3: "⏳ Waiting On: [Person]" + Auto Follow-Up with "Close the loop"
                    PromptOptionRowCard(
                        badge = "⏳ STEP 3 • 'WAITING ON: [PERSON]' + AUTO FOLLOW-UP",
                        title = "Tag Waiting On & Create 'Close the Loop' Follow-Up",
                        subtitle = "Tags task as ⏳ Waiting On and auto-creates a deferred follow-up ending in 'Close the loop'",
                        icon = Icons.Default.HourglassTop,
                        accentColor = VioletQ2Build,
                        testTag = "run_prompt_waiting_on_task",
                        onClick = { showWaitingOnPicker = !showWaitingOnPicker }
                    )

                    if (showWaitingOnPicker) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VioletQ2Build.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, VioletQ2Build)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "WHO ARE YOU WAITING ON?",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VioletQ2Build
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Corrie", "Debbie", "Wayne", "Lauren", "Diana").forEach { person ->
                                        FilterChip(
                                            selected = false,
                                            onClick = { onTagWaitingOnPersonForTask(activeTask, person) },
                                            label = { Text("⏳ @$person", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customWaitingPerson,
                                        onValueChange = { customWaitingPerson = it },
                                        label = { Text("Or type name") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            if (customWaitingPerson.isNotBlank()) {
                                                onTagWaitingOnPersonForTask(activeTask, customWaitingPerson.trim())
                                            }
                                        },
                                        enabled = customWaitingPerson.isNotBlank()
                                    ) {
                                        Text("Apply")
                                    }
                                }
                            }
                        }
                    }

                    // 6. Step 6: "Draft It" (Rough First Draft)
                    PromptOptionRowCard(
                        badge = "📝 STEP 6 • 'DRAFT IT'",
                        title = "Generate Rough First Draft Immediately",
                        subtitle = "Provides a ready-to-send email/chat draft so you only edit logic and intent",
                        icon = Icons.Default.RateReview,
                        accentColor = ElectricIndigo,
                        testTag = "run_prompt_draft_it_task",
                        onClick = {
                            onRunCommsLensForTask(activeTask, CommsStrategyLens.GROUNDED_COMMS)
                        }
                    )

                    // 7. Step 6: "Rabbit Hole" ("Good Enough" Stop & Redirect)
                    PromptOptionRowCard(
                        badge = "🐰 STEP 6 • 'RABBIT HOLE' BREAKER",
                        title = "Declare Work 'Good Enough' & Redirect to Top Task",
                        subtitle = "Stops hyper-focusing on minor details, marks current polish step done, and redirects focus",
                        icon = Icons.Default.StopCircle,
                        accentColor = CoralQ1Urgent,
                        testTag = "run_prompt_rabbit_hole_task",
                        onClick = { onRabbitHoleStopForTask(activeTask) }
                    )

                    // 8. Step 6: "Remind Me / Snooze"
                    PromptOptionRowCard(
                        badge = "⏰ STEP 6 • 'REMIND ME / SNOOZE'",
                        title = "Pull Off Active Visual List & Snooze for Later",
                        subtitle = "Moves to 🅿️ Parked / Snoozed queue with resurface timestamp so your visual list stays clean",
                        icon = Icons.Default.AccessTime,
                        accentColor = SlateParkedQ4,
                        testTag = "run_prompt_snooze_task",
                        onClick = { onSnoozeTask(activeTask, "Tomorrow 9:00 AM") }
                    )

                    // 9. Step 6: The 99% Check ("Finish Line")
                    PromptOptionRowCard(
                        badge = "🏁 STEP 6 • THE 99% CHECK ('FINISH LINE')",
                        title = "Did You Physically Hit Send, Save, or Close Tab?",
                        subtitle = "Verifies the loop is permanently closed before marking complete",
                        icon = Icons.Default.TaskAlt,
                        accentColor = EmeraldAiCore,
                        testTag = "run_prompt_verify99_task",
                        onClick = { onVerify99ForTask(activeTask) }
                    )

                    // 10. Step 4: Meeting Prep & Additional Studio Lenses
                    PromptOptionRowCard(
                        badge = "📅 MEETING PREP • 30S GLANCE CARD",
                        title = "Synthesize 30s Meeting Brief for Stakeholders",
                        subtitle = "Builds 4-part SQLite + AI Meeting Brief for ${activeTask.peopleTags.firstOrNull()?.let { "@$it" } ?: activeTask.topicTags.firstOrNull()?.let { "#$it" } ?: "@Corrie"}",
                        icon = Icons.Default.Groups,
                        accentColor = VioletQ2Build,
                        testTag = "run_prompt_meeting_prep_task",
                        onClick = { onPrepMeetingForTask(activeTask) }
                    )

                    PromptOptionRowCard(
                        badge = "✨ SECOND BRAIN • AUTO-BRIEF",
                        title = "Generate Structured Second Brain Card from Task",
                        subtitle = "Creates a linked Second Brain summary card and shows the Executive Briefing",
                        icon = Icons.Default.EventNote,
                        accentColor = TelemetryCyan,
                        testTag = "run_prompt_reorganize_task",
                        onClick = { onReOrganizeTaskIntoSecondBrain(activeTask) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PromptOptionRowCard(
    badge: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
