package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CardCategory
import com.example.data.local.EnergyLoad
import com.example.data.local.EntityContextWeight
import com.example.data.local.EntityType
import com.example.data.local.KnowledgeCard
import com.example.data.local.MicroStep
import com.example.data.local.OutlineSection
import com.example.data.local.ReiTask
import com.example.data.local.TaskQuadrant
import com.example.ui.VoiceSamplePreset
import com.example.ui.theme.AmberSteadyRisk
import com.example.ui.theme.CoralQ1Urgent
import com.example.ui.theme.ElectricIndigo
import com.example.ui.theme.EmeraldAiCore
import com.example.ui.theme.TelemetryCyan

// 1. UNIFIED ENTITY HUB SHEET (@Person / #Topic Bi-Directional Relational View)
@Composable
fun EntityHubDialog(
    entityTag: String,
    allEntities: List<EntityContextWeight>,
    allCards: List<KnowledgeCard>,
    allTasks: List<ReiTask>,
    onDismiss: () -> Unit,
    onJumpToMeetingPrep: (String) -> Unit,
    onOpenCard: (KnowledgeCard) -> Unit,
    onOpenTask: (ReiTask) -> Unit,
    onSaveEntityWeight: (String, EntityType, String, TaskQuadrant?, EnergyLoad?) -> Unit
) {
    val cleanName = entityTag.removePrefix("@").removePrefix("#").trim()
    val entityWeight = allEntities.find { it.entityName.equals(cleanName, ignoreCase = true) }
    val relatedCards = allCards.filter { card ->
        card.peopleTags.any { it.equals(cleanName, ignoreCase = true) } ||
            card.topicTags.any { it.equals(cleanName, ignoreCase = true) }
    }
    val relatedTasks = allTasks.filter { task ->
        task.peopleTags.any { it.equals(cleanName, ignoreCase = true) } ||
            task.topicTags.any { it.equals(cleanName, ignoreCase = true) }
    }

    var selectedBias by remember(entityWeight) {
        mutableStateOf(entityWeight?.learnedQuadrantBias ?: TaskQuadrant.Q1_DO_NOW)
    }
    var roleText by remember(entityWeight) {
        mutableStateOf(entityWeight?.roleOrContext ?: "Executive Stakeholder / Initiative")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RELATIONAL ENTITY HUB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${entityWeight?.entityType?.prefix ?: "@"}$cleanName",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onJumpToMeetingPrep(cleanName) },
                    modifier = Modifier.testTag("entity_hub_prep_button")
                ) {
                    Text("30s Meeting Prep", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = roleText,
                    onValueChange = { roleText = it },
                    label = { Text("Role / Strategic Context") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Learned Priority Bias (Auto-sorts future Brain Dumps):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskQuadrant.entries.forEach { q ->
                        FilterChip(
                            selected = selectedBias == q,
                            onClick = {
                                selectedBias = q
                                onSaveEntityWeight(
                                    cleanName,
                                    entityWeight?.entityType ?: EntityType.PERSON,
                                    roleText,
                                    q,
                                    entityWeight?.learnedEnergyBias ?: EnergyLoad.STEADY_WORK
                                )
                            },
                            label = { Text(q.shortCode, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }

                HorizontalDivider()
                Text(
                    text = "LINKED REITASKS (${relatedTasks.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAiCore
                )
                if (relatedTasks.isEmpty()) {
                    Text("No tasks tagged with $cleanName.", style = MaterialTheme.typography.bodySmall)
                } else {
                    relatedTasks.forEach { task ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenTask(task)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${task.quadrant.shortCode} • ${task.title}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${task.status.label} • ${task.dueDateNote}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = "LINKED KNOWLEDGE CARDS (${relatedCards.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan
                )
                if (relatedCards.isEmpty()) {
                    Text("No Knowledge Cards tagged with $cleanName.", style = MaterialTheme.typography.bodySmall)
                } else {
                    relatedCards.forEach { card ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenCard(card)
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = card.tldrSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSaveEntityWeight(
                        cleanName,
                        entityWeight?.entityType ?: EntityType.PERSON,
                        roleText,
                        selectedBias,
                        entityWeight?.learnedEnergyBias ?: EnergyLoad.STEADY_WORK
                    )
                    onDismiss()
                }
            ) {
                Text("Save & Close")
            }
        }
    )
}

// 2. BREADCRUMB DEFERRAL MODAL ("🍞 I'm Stuck" — Single Short Question + Breadcrumb Memory)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreadcrumbDeferralDialog(
    task: ReiTask,
    onDismiss: () -> Unit,
    onConfirmBreadcrumb: (String, String, String) -> Unit
) {
    var stoppedAt by remember(task) {
        mutableStateOf(
            task.breadcrumbStoppedAt.ifBlank {
                task.microSteps.lastOrNull { it.isCompleted }?.text ?: "Opened task & reviewed initial context"
            }
        )
    }
    var blocker by remember(task) {
        mutableStateOf(
            task.breadcrumbBlocker.ifBlank {
                val p = task.peopleTags.firstOrNull()?.let { "@$it" } ?: "stakeholder"
                "Waiting on $p confirmation / missing key input"
            }
        )
    }
    var resumeStep by remember(task) {
        mutableStateOf(
            task.breadcrumbResumeStep.ifBlank {
                task.microSteps.firstOrNull { !it.isCompleted }?.text
                    ?: "Send 1-line check-in ping & update tracker"
            }
        )
    }

    val quickRoadblockChips = remember(task) {
        val person = task.peopleTags.firstOrNull()?.let { "@$it" } ?: "@Corrie"
        listOf(
            "Waiting on $person reply",
            "Missing file / data link",
            "Step feels too big right now",
            "System / login blocked"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "🍞 STEP 6 • BREADCRUMB DEFERRAL ('I'M STUCK')",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TelemetryCyan
                )
                Text(
                    text = "What specifically isn't working or why are you stuck?",
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
                Text(
                    text = "Rei will save your answer as a 🍞 Breadcrumb on '${task.title}', defer it off the active slot, and switch you to the next Top Task:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickRoadblockChips.forEach { chip ->
                        FilterChip(
                            selected = blocker == chip,
                            onClick = { blocker = chip },
                            label = {
                                Text(
                                    text = chip,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                OutlinedTextField(
                    value = blocker,
                    onValueChange = { blocker = it },
                    label = { Text("What specifically isn't working or why are you stuck?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("breadcrumb_blocker_input")
                )

                OutlinedTextField(
                    value = stoppedAt,
                    onValueChange = { stoppedAt = it },
                    label = { Text("Where you left off (optional context)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("breadcrumb_stopped_at_input")
                )

                OutlinedTextField(
                    value = resumeStep,
                    onValueChange = { resumeStep = it },
                    label = { Text("Next 2-min step when this resurfaces") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("breadcrumb_resume_step_input")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmBreadcrumb(stoppedAt, blocker, resumeStep) },
                modifier = Modifier.testTag("confirm_breadcrumb_button")
            ) {
                Text("Save 🍞 Breadcrumb & Switch Task")
            }
        }
    )
}

// 3. "99% CHECK" FINISH-LINE VERIFICATION MODAL ("🏁 Finish Line")
@Composable
fun Verify99FinishLineDialog(
    task: ReiTask,
    onDismiss: () -> Unit,
    onConfirmDone: () -> Unit
) {
    var checkSent by remember { mutableStateOf(true) }
    var checkCommitted by remember { mutableStateOf(true) }
    var checkTrackerUpdated by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = EmeraldAiCore
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "🏁 STEP 6 • THE 99% CHECK ('FINISH LINE')",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldAiCore
                    )
                    Text(
                        text = "99% Check: Did you physically hit send, save the file, or update the tracker?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Do not mark '${task.title}' complete until you confirm the final loop is closed:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checkSent = !checkSent }
                ) {
                    Checkbox(
                        checked = checkSent,
                        onCheckedChange = { checkSent = it },
                        colors = CheckboxDefaults.colors(checkedColor = EmeraldAiCore)
                    )
                    Text(
                        text = "Physically hit Send on the message / email (not left in drafts)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checkCommitted = !checkCommitted }
                ) {
                    Checkbox(
                        checked = checkCommitted,
                        onCheckedChange = { checkCommitted = it },
                        colors = CheckboxDefaults.colors(checkedColor = EmeraldAiCore)
                    )
                    Text(
                        text = "Saved / committed the file or deliverable",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checkTrackerUpdated = !checkTrackerUpdated }
                ) {
                    Checkbox(
                        checked = checkTrackerUpdated,
                        onCheckedChange = { checkTrackerUpdated = it },
                        colors = CheckboxDefaults.colors(checkedColor = EmeraldAiCore)
                    )
                    Text(
                        text = "Updated the tracker & closed the loop permanently",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not Yet — Keep Open") }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDone,
                enabled = checkSent && checkCommitted && checkTrackerUpdated,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAiCore),
                modifier = Modifier.testTag("confirm_99_check_done_button")
            ) {
                Text("Confirm & Mark Done")
            }
        }
    )
}

// 4. TAP-TO-EDIT KNOWLEDGE CARD & NATURAL OUTLINE SHEET (Zero Markdown Formatting)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditKnowledgeCardDialog(
    card: KnowledgeCard,
    onDismiss: () -> Unit,
    onSave: (String, String, CardCategory, Boolean, List<OutlineSection>, List<String>, List<String>) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(card) { mutableStateOf(card.title) }
    var tldr by remember(card) { mutableStateOf(card.tldrSummary) }
    var category by remember(card) { mutableStateOf(card.category) }
    var hasRisk by remember(card) { mutableStateOf(card.hasRiskOrBlocker) }
    var peopleCsv by remember(card) { mutableStateOf(card.peopleTags.joinToString(", ")) }
    var topicsCsv by remember(card) { mutableStateOf(card.topicTags.joinToString(", ")) }
    val sectionsState = remember(card) {
        mutableStateListOf<OutlineSection>().apply { addAll(card.outlineSections) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap-to-Edit Knowledge Card",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Card",
                        tint = CoralQ1Urgent
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Card Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = tldr,
                    onValueChange = { tldr = it },
                    label = { Text("2-Sentence Executive Summary") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Flag SLA / Escalation Risk", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = hasRisk, onCheckedChange = { hasRisk = it })
                }

                OutlinedTextField(
                    value = peopleCsv,
                    onValueChange = { peopleCsv = it },
                    label = { Text("@People Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = topicsCsv,
                    onValueChange = { topicsCsv = it },
                    label = { Text("#Topic Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text(
                    text = "NATURAL OUTLINE SECTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                sectionsState.forEachIndexed { idx, section ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            OutlinedTextField(
                                value = section.heading,
                                onValueChange = { newHeading ->
                                    sectionsState[idx] = section.copy(heading = newHeading)
                                },
                                label = { Text("Section ${idx + 1} Heading") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = section.bullets.joinToString("\n"),
                                onValueChange = { lines ->
                                    sectionsState[idx] = section.copy(
                                        bullets = lines.lines().map { it.trim() }.filter { it.isNotBlank() }
                                    )
                                },
                                label = { Text("Bullets (1 per line — zero Markdown needed)") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pList = peopleCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val tList = topicsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(title, tldr, category, hasRisk, sectionsState.toList(), pList, tList)
                }
            ) {
                Text("Save Card")
            }
        }
    )
}

// 5. TAP-TO-EDIT REITASK & MICRO-STEPS SHEET
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditReiTaskDialog(
    task: ReiTask,
    onDismiss: () -> Unit,
    onSave: (String, TaskQuadrant, EnergyLoad, String, List<MicroStep>, List<String>, List<String>) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(task) { mutableStateOf(task.title) }
    var quadrant by remember(task) { mutableStateOf(task.quadrant) }
    var energy by remember(task) { mutableStateOf(task.energyLoad) }
    var dueNote by remember(task) { mutableStateOf(task.dueDateNote) }
    var peopleCsv by remember(task) { mutableStateOf(task.peopleTags.joinToString(", ")) }
    var topicsCsv by remember(task) { mutableStateOf(task.topicTags.joinToString(", ")) }
    val stepsState = remember(task) {
        mutableStateListOf<MicroStep>().apply {
            if (task.microSteps.isNotEmpty()) {
                addAll(task.microSteps)
            } else {
                add(MicroStep("s1", "Starter step (< 2 min)", false, 1))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap-to-Edit ReiTask",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Task",
                        tint = CoralQ1Urgent
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Eisenhower Quadrant (Updates VIP Learning):", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskQuadrant.entries.forEach { q ->
                        FilterChip(
                            selected = quadrant == q,
                            onClick = { quadrant = q },
                            label = { Text(q.shortCode) }
                        )
                    }
                }

                Text("Mental Bandwidth Load:", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EnergyLoad.entries.forEach { load ->
                        FilterChip(
                            selected = energy == load,
                            onClick = { energy = load },
                            label = { Text(load.badge, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = dueNote,
                    onValueChange = { dueNote = it },
                    label = { Text("Due / Horizon Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = peopleCsv,
                    onValueChange = { peopleCsv = it },
                    label = { Text("@People Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = topicsCsv,
                    onValueChange = { topicsCsv = it },
                    label = { Text("#Topic Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()
                Text(
                    text = "MICRO-STEPS (STEP 1 < 2 MIN)",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAiCore
                )
                stepsState.forEachIndexed { idx, step ->
                    OutlinedTextField(
                        value = step.text,
                        onValueChange = { newText ->
                            stepsState[idx] = step.copy(text = newText)
                        },
                        label = { Text("Step ${idx + 1}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pList = peopleCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val tList = topicsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(title, quadrant, energy, dueNote, stepsState.toList(), pList, tList)
                }
            ) {
                Text("Save Task")
            }
        }
    )
}

// 6. VIP CONTEXT & LEARNED PRIORITY RULES MANAGER SHEET
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VipContextRulesDialog(
    entities: List<EntityContextWeight>,
    onDismiss: () -> Unit,
    onSaveEntityRule: (String, EntityType, String, TaskQuadrant?, EnergyLoad?) -> Unit,
    onDeleteEntityRule: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(EntityType.PERSON) }
    var newRole by remember { mutableStateOf("") }
    var newQuad by remember { mutableStateOf(TaskQuadrant.Q1_DO_NOW) }
    var newEnergy by remember { mutableStateOf(EnergyLoad.DEEP_WORK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "SELF-LEARNED & VIP PRIORITY RULES (${entities.size} ACTIVE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Edit or Add Learned Rules",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tap Q1–Q4 on any rule below to change its learned priority bias immediately, tap a rule to edit its context, or add a new @Person / #Topic rule.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Add or Update VIP Entity Form
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (newName.isBlank()) "➕ ADD NEW RULE" else "✏️ EDITING RULE: $newName",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ElectricIndigo
                            )
                            if (newName.isNotBlank() || newRole.isNotBlank()) {
                                Text(
                                    text = "Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.clickable {
                                        newName = ""
                                        newRole = ""
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Name (e.g., Corrie, Epic SBO)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vip_rule_name_input")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = newRole,
                            onValueChange = { newRole = it },
                            label = { Text("Role or Strategic Context") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vip_rule_role_input")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EntityType.entries.forEach { t ->
                                FilterChip(
                                    selected = newType == t,
                                    onClick = { newType = t },
                                    label = { Text("${t.prefix} ${t.name}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TaskQuadrant.entries.forEach { q ->
                                FilterChip(
                                    selected = newQuad == q,
                                    onClick = { newQuad = q },
                                    label = { Text("Bias: ${q.shortCode}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EnergyLoad.entries.forEach { e ->
                                FilterChip(
                                    selected = newEnergy == e,
                                    onClick = { newEnergy = e },
                                    label = { Text(e.badge, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onSaveEntityRule(newName, newType, newRole, newQuad, newEnergy)
                                    newName = ""
                                    newRole = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_vip_rule_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (newName.isBlank()) "Add / Save Rule" else "Save '$newName' Rule")
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = "ACTIVE SELF-LEARNED & VIP RULES (TAP Q1–Q4 TO CHANGE)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                entities.forEach { entity ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                newName = entity.entityName
                                newType = entity.entityType
                                newRole = entity.roleOrContext
                                newQuad = entity.learnedQuadrantBias ?: TaskQuadrant.Q2_PROTECT_BUILD
                                newEnergy = entity.learnedEnergyBias ?: EnergyLoad.STEADY_WORK
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${entity.entityType.prefix}${entity.entityName} • ${entity.learnedEnergyBias?.badge ?: "🟡 Steady"}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = entity.roleOrContext,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Referenced ${entity.interactionCount}x • Tap card to edit role",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelemetryCyan
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteEntityRule(entity.entityName) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete VIP Rule",
                                        tint = CoralQ1Urgent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Inline 1-Tap Quadrant Bias Changer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Learned Priority:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TaskQuadrant.entries.forEach { q ->
                                        val isCurrent = (entity.learnedQuadrantBias ?: TaskQuadrant.Q2_PROTECT_BUILD) == q
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCurrent) ElectricIndigo.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isCurrent) ElectricIndigo else MaterialTheme.colorScheme.outline
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    onSaveEntityRule(
                                                        entity.entityName,
                                                        entity.entityType,
                                                        entity.roleOrContext,
                                                        q,
                                                        entity.learnedEnergyBias ?: EnergyLoad.STEADY_WORK
                                                    )
                                                }
                                        ) {
                                            Text(
                                                text = q.shortCode,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

// 7. 1-TAP LOCAL JSON & SQLITE BACKUP & RESTORE BRIDGE + START FRESH MODE SHEET
@Composable
fun LocalBackupRestoreDialog(
    cardCount: Int,
    taskCount: Int,
    entityCount: Int,
    onDismiss: () -> Unit,
    onExportJsonUri: (Uri) -> Unit,
    onImportJsonUri: (Uri) -> Unit,
    onExportSqliteUri: (Uri) -> Unit,
    onRestoreSqliteUri: (Uri) -> Unit,
    onStartFreshKeepVipRules: () -> Unit,
    onResetSeedData: () -> Unit
) {
    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportJsonUri(uri) }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportJsonUri(uri) }

    val createSqliteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) onExportSqliteUri(uri) }

    val openSqliteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onRestoreSqliteUri(uri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "100% LOCAL DATA PORTABILITY BRIDGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan
                )
                Text(
                    text = "Backup, Restore & Start Fresh",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Current Local DB: $cardCount Knowledge Cards • $taskCount ReiTasks • $entityCount VIP Rules",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // MVP Step 3: Start Fresh Mode (Clear Demo Cards & Tasks while Keeping VIP Rules)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldAiCore.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, EmeraldAiCore.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🚀 DAILY-DRIVER START FRESH MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAiCore
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Wipe the demo Knowledge Cards & Tasks to start logging your real day, while keeping all $entityCount VIP Context Rules (@Corrie, @Debbie, @Wayne, #Epic SBO, etc.) intact.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onStartFreshKeepVipRules()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAiCore),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_fresh_keep_vip_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Demo Data • Keep My VIP Rules")
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = "Option A • Structured JSON Snapshot (Human-Readable)",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { createJsonLauncher.launch("rei_second_brain_backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON")
                    }
                    OutlinedButton(
                        onClick = { openJsonLauncher.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore JSON")
                    }
                }

                HorizontalDivider()
                Text(
                    text = "Option B • Raw Room SQLite Database File (.db)",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { createSqliteLauncher.launch("rei_second_brain.db") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export .db")
                    }
                    OutlinedButton(
                        onClick = { openSqliteLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore .db")
                    }
                }

                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        onResetSeedData()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore Default Executive Operational Seed Dataset")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// 8. SAMPLE EXECUTIVE VOICE DICTATION PRESET SELECTOR
@Composable
fun VoicePresetSelectorDialog(
    presets: List<VoiceSamplePreset>,
    onDismiss: () -> Unit,
    onSelectPreset: (String, Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = ElectricIndigo
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "RAPID VOICE DICTATION & PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricIndigo
                    )
                    Text(
                        text = "Select Executive Voice Dump",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tap any executive voice transcript below to test instant AI Auto-Structuring (Knowledge Card + Natural Outline + @Person/#Topic pills + ReiTasks):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presets.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preset.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricIndigo
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"${preset.transcript}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onSelectPreset(preset.transcript, false) }) {
                                    Text("Load into Bar")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = { onSelectPreset(preset.transcript, true) },
                                    modifier = Modifier.testTag("auto_organize_${preset.id}")
                                ) {
                                    Text("Auto-Organize Now")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// 9. EXPANDED TYPE / PASTE / SHARE-SHEET BRAIN DUMP COMPOSER DIALOG
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypeAndPasteBrainDumpDialog(
    text: String,
    sharedSourceLabel: String?,
    detectedPeople: List<String>,
    detectedTopics: List<String>,
    allEntities: List<EntityContextWeight>,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onOrganizeNow: () -> Unit,
    onParkItNow: () -> Unit,
    onSwitchToVoicePresets: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = sharedSourceLabel?.uppercase() ?: "TYPE, PASTE OR SHARE BRAIN DUMP",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sharedSourceLabel != null) EmeraldAiCore else ElectricIndigo
                )
                Text(
                    text = "Zero-Markdown Brain Dump Composer",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Type a 1-line thought, paste a raw Teams/Outlook thread, or tap any @VIP chip below to insert it. Rei automatically formats it into a Knowledge Card + ReiTasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick Clipboard Paste & Clear Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!clipText.isNullOrBlank()) {
                                val next = if (text.isBlank()) clipText else "$text\n$clipText"
                                onTextChange(next)
                            }
                        },
                        modifier = Modifier.testTag("composer_paste_clipboard_button")
                    ) {
                        Text("📋 Paste Clipboard", style = MaterialTheme.typography.labelMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onSwitchToVoicePresets) {
                            Text("🎙️ Voice Presets", style = MaterialTheme.typography.labelMedium)
                        }
                        if (text.isNotBlank()) {
                            TextButton(onClick = { onTextChange("") }) {
                                Text("Clear", style = MaterialTheme.typography.labelMedium, color = CoralQ1Urgent)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Type or Paste Brain Dump / Meeting Notes") },
                    placeholder = {
                        Text("e.g., Sync with @Corrie and @Wayne on #Epic SBO: need to lock the 8am-1pm collector ring-fence before Thursday 2pm…")
                    },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("type_composer_input")
                )

                // 1-Tap Quick-Insert VIP Stakeholder / Topic Chips while typing
                Text(
                    text = "TAP TO INSERT @STAKEHOLDER OR #INITIATIVE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allEntities.take(8).forEach { entity ->
                        val token = "${entity.entityType.prefix}${entity.entityName}"
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    val spacer = if (text.isEmpty() || text.endsWith(" ")) "" else " "
                                    onTextChange("$text$spacer$token ")
                                }
                        ) {
                            Text(
                                text = "+ $token",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Live Entity Auto-Detection Preview
                if (detectedPeople.isNotEmpty() || detectedTopics.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElectricIndigo.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "LIVE RELATIONAL TAGS DETECTED:",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricIndigo
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            EntityPillsRow(
                                peopleTags = detectedPeople,
                                topicTags = detectedTopics,
                                onEntityClick = {}
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onParkItNow,
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("composer_park_it_button")
            ) {
                Text("Park It (Q4)")
            }
        },
        confirmButton = {
            Button(
                onClick = onOrganizeNow,
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("composer_organize_button")
            ) {
                Text("Auto-Organize Now")
            }
        }
    )
}

// 10. IN-APP S25U AICORE SELF-TEST & SIDE-BY-SIDE RAW OUTPUT INSPECTOR DIALOG
@Composable
fun AiCoreSelfTestInspectorDialog(
    diagnostics: com.example.ai.AiEngineDiagnostics,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onRunLiveProbe: () -> Unit,
    onRunMalformedStressTest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "S25 ULTRA AICORE • ON-DEVICE DIAGNOSTICS",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldAiCore
                )
                Text(
                    text = "AICore Self-Test & Raw Output Inspector",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hardware & Latency Summary Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(11.dp)) {
                        Text(
                            text = "${diagnostics.status.badgeText} • ${diagnostics.lastSkillExecuted}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "FeatureStatus: ${diagnostics.rawFeatureStatusCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Latency: ${diagnostics.lastLatencyMs} ms • Prompt Est: ~${diagnostics.lastPromptTokenEstimate} tokens (<1,024 limit)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                        if (diagnostics.lastContextHeaderPreview.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diagnostics.lastContextHeaderPreview,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 1-Tap Self-Test & Stress-Test Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onRunLiveProbe,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("run_live_aicore_probe_button")
                    ) {
                        Text("⚡ Live AICore Probe", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onRunMalformedStressTest,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("run_malformed_repair_test_button")
                    ) {
                        Text("🧪 Test Fence/Comma Repair", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Schema Repair Audit Notes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (diagnostics.schemaRepairApplied) {
                        AmberSteadyRisk.copy(alpha = 0.14f)
                    } else {
                        EmeraldAiCore.copy(alpha = 0.14f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (diagnostics.schemaRepairApplied) AmberSteadyRisk else EmeraldAiCore
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (diagnostics.schemaRepairApplied) {
                                "🛠️ AICORESCHEMAREPAIR APPLIED (${diagnostics.repairNotes.size} STEP(S))"
                            } else {
                                "✅ SCHEMA VALIDATION PASSED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diagnostics.schemaRepairApplied) AmberSteadyRisk else EmeraldAiCore
                        )
                        diagnostics.repairNotes.forEach { note ->
                            Text(
                                text = "• $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // SIDE-BY-SIDE RAW GEMINI NANO TEXT VS REPAIRED JSON
                Text(
                    text = "SIDE-BY-SIDE: RAW GEMINI NANO TEXT VS. REPAIRED JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("side_by_side_inspector_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left Pane: Raw Gemini Nano Text
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.55f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(9.dp)) {
                            Text(
                                text = "RAW MODEL TEXT",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricIndigo
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diagnostics.lastRawModelOutput.ifBlank { "(Tap 'Live AICore Probe' above)" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("inspector_raw_output_text")
                            )
                        }
                    }

                    // Right Pane: Repaired & Validated JSON
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, EmeraldAiCore.copy(alpha = 0.55f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(9.dp)) {
                            Text(
                                text = "REPAIRED JSON",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAiCore
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = diagnostics.lastRepairedJsonOutput.ifBlank { "(Tap 'Live AICore Probe' above)" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("inspector_repaired_json_text")
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close Inspector")
            }
        }
    )
}


