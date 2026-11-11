package com.emaratta.rei.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.emaratta.rei.ai.NorthwellMeetingCandidate
import com.emaratta.rei.data.local.EntityContextWeight
import com.emaratta.rei.data.local.KnowledgeCard
import com.emaratta.rei.data.local.ReiTask
import com.emaratta.rei.data.local.TaskQuadrant
import com.emaratta.rei.data.local.TaskStatus
import com.emaratta.rei.ui.components.EntityPillsRow
import com.emaratta.rei.ui.theme.AmberSteadyRisk
import com.emaratta.rei.ui.theme.CoralQ1Urgent
import com.emaratta.rei.ui.theme.ElectricIndigo
import com.emaratta.rei.ui.theme.EmeraldAiCore
import com.emaratta.rei.ui.theme.SlateParkedQ4
import com.emaratta.rei.ui.theme.TelemetryCyan
import com.emaratta.rei.ui.theme.VioletQ2Build

enum class ReiCommandViewMode(val label: String, val badge: String) {
    ACTION_DECK("Action Deck (Q1–Q4)", "🎯 Tactical"),
    DELIVERABLE_RUNWAY("Deliverable Runway", "📊 Smart-Gantt")
}

enum class DeliverableHorizon(
    val index: Int,
    val shortLabel: String,
    val columnHeader: String,
    val defaultDueNote: String,
    val mappedQuadrant: TaskQuadrant
) {
    NOW_TODAY(0, "Now / Today", "NOW", "Today • Immediate Cutover", TaskQuadrant.Q1_DO_NOW),
    THIS_WEEK(1, "This Week", "THIS WK", "This Week • Active Sprint", TaskQuadrant.Q2_PROTECT_BUILD),
    NEXT_UP(2, "Next Up", "NEXT UP", "Next Up • Queued Pipeline", TaskQuadrant.Q3_BATCH_CONTAIN),
    SHIPPED_99(3, "99% / Done", "SHIPPED", "99% Verified • Complete", TaskQuadrant.Q2_PROTECT_BUILD)
}

enum class DeliverableRagStatus(val label: String, val shortBadge: String) {
    RED_SLA_RISK("SLA Risk / Stalled", "🔴 At Risk"),
    AMBER_BLOCKED("Paused / Dependency", "🟡 Blocked"),
    GREEN_ON_TRACK("On Track", "🟢 On Track")
}

enum class CriticalPathStage(val stepIndex: Int, val label: String) {
    SCOPE(0, "Scope"),
    UNBLOCK(1, "Unblock"),
    EXECUTE(2, "Execute"),
    VERIFY_99(3, "99% Check"),
    SHIPPED(4, "Shipped")
}

data class DeliverableTaskNode(
    val task: ReiTask,
    val horizon: DeliverableHorizon,
    val stepCompletionRatio: Float,
    val completedSteps: Int,
    val totalSteps: Int
)

data class InitiativeSwimlaneModel(
    val topicName: String,
    val roleOrContext: String,
    val ragStatus: DeliverableRagStatus,
    val criticalStage: CriticalPathStage,
    val completionPercent: Int,
    val completedStepCount: Int,
    val totalStepCount: Int,
    val nodes: List<DeliverableTaskNode>,
    val linkedCardsCount: Int,
    val hasCardRiskFlag: Boolean,
    val activeBlockers: List<String>,
    val peopleInvolved: List<String>
)

object DeliverableRunwayBuilder {

    fun classifyHorizon(task: ReiTask): DeliverableHorizon {
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.VERIFY_99) {
            return DeliverableHorizon.SHIPPED_99
        }
        val lowerDue = task.dueDateNote.lowercase()
        val lowerTitle = task.title.lowercase()
        return when {
            lowerDue.contains("99%") || lowerDue.contains("shipped") || lowerDue.contains("complete") ->
                DeliverableHorizon.SHIPPED_99
            lowerDue.contains("today") || lowerDue.contains("now") || lowerDue.contains("urgent") ||
                lowerDue.contains("48") || lowerDue.contains("immediate") || lowerTitle.contains("urgent") ->
                DeliverableHorizon.NOW_TODAY
            lowerDue.contains("week") || lowerDue.contains("thursday") || lowerDue.contains("friday") ||
                lowerDue.contains("monday") || lowerDue.contains("tuesday") || lowerDue.contains("wednesday") ||
                lowerDue.contains("sprint") ->
                DeliverableHorizon.THIS_WEEK
            lowerDue.contains("next") || lowerDue.contains("later") || lowerDue.contains("parked") ||
                lowerDue.contains("month") || lowerDue.contains("someday") ->
                DeliverableHorizon.NEXT_UP
            task.quadrant == TaskQuadrant.Q1_DO_NOW -> DeliverableHorizon.NOW_TODAY
            task.quadrant == TaskQuadrant.Q2_PROTECT_BUILD -> DeliverableHorizon.THIS_WEEK
            else -> DeliverableHorizon.NEXT_UP
        }
    }

    fun buildInitiativeSwimlanes(
        tasks: List<ReiTask>,
        cards: List<KnowledgeCard>,
        entities: List<EntityContextWeight>
    ): List<InitiativeSwimlaneModel> {
        val entityMap = entities.associateBy { it.entityName.removePrefix("#").removePrefix("@").lowercase() }

        // Collect all distinct topics from tasks and cards
        val allTopicNames = LinkedHashSet<String>()
        tasks.flatMap { it.topicTags }.forEach { raw ->
            val clean = raw.removePrefix("#").trim()
            if (clean.isNotBlank()) allTopicNames.add(clean)
        }
        cards.flatMap { it.topicTags }.forEach { raw ->
            val clean = raw.removePrefix("#").trim()
            if (clean.isNotBlank()) allTopicNames.add(clean)
        }

        val unassignedTasks = tasks.filter { it.topicTags.isEmpty() }
        if (unassignedTasks.isNotEmpty()) {
            allTopicNames.add("General Executive Ops")
        }

        val swimlanes = mutableListOf<InitiativeSwimlaneModel>()

        for (topic in allTopicNames) {
            val topicTasks = if (topic == "General Executive Ops") {
                unassignedTasks
            } else {
                tasks.filter { task ->
                    task.topicTags.any { it.removePrefix("#").trim().equals(topic, ignoreCase = true) }
                }
            }
            val topicCards = cards.filter { card ->
                card.topicTags.any { it.removePrefix("#").trim().equals(topic, ignoreCase = true) }
            }

            if (topicTasks.isEmpty() && topicCards.isEmpty()) continue

            val nodes = topicTasks.map { task ->
                val steps = task.microSteps
                val total = steps.size.coerceAtLeast(1)
                val done = if (task.status == TaskStatus.DONE) {
                    total
                } else {
                    steps.count { it.isCompleted }
                }
                val ratio = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                DeliverableTaskNode(
                    task = task,
                    horizon = classifyHorizon(task),
                    stepCompletionRatio = ratio,
                    completedSteps = done,
                    totalSteps = total
                )
            }.sortedWith(compareBy<DeliverableTaskNode> { it.horizon.index }.thenBy { it.task.quadrant.ordinal })

            val totalSteps = nodes.sumOf { it.totalSteps }.coerceAtLeast(1)
            val completedSteps = nodes.sumOf { it.completedSteps }
            val completionPercent = if (nodes.isEmpty()) {
                0
            } else {
                ((completedSteps.toFloat() / totalSteps.toFloat()) * 100f).toInt().coerceIn(0, 100)
            }

            val hasCardRisk = topicCards.any { it.hasRiskOrBlocker }
            val hasStalledTask = topicTasks.any { it.isStalled && it.status != TaskStatus.DONE }
            val hasBreadcrumbBlocker = topicTasks.any {
                it.status == TaskStatus.PAUSED_BREADCRUMB && it.breadcrumbBlocker.isNotBlank()
            }

            val ragStatus = when {
                hasCardRisk || hasStalledTask -> DeliverableRagStatus.RED_SLA_RISK
                hasBreadcrumbBlocker -> DeliverableRagStatus.AMBER_BLOCKED
                else -> DeliverableRagStatus.GREEN_ON_TRACK
            }

            val criticalStage = when {
                nodes.isNotEmpty() && nodes.all { it.task.status == TaskStatus.DONE } ->
                    CriticalPathStage.SHIPPED
                completionPercent >= 85 || nodes.any { it.task.status == TaskStatus.VERIFY_99 } ->
                    CriticalPathStage.VERIFY_99
                hasBreadcrumbBlocker || hasStalledTask ->
                    CriticalPathStage.UNBLOCK
                completedSteps > 0 ->
                    CriticalPathStage.EXECUTE
                else ->
                    CriticalPathStage.SCOPE
            }

            val blockers = topicTasks
                .filter { it.status == TaskStatus.PAUSED_BREADCRUMB && it.breadcrumbBlocker.isNotBlank() }
                .map { "${it.title}: ${it.breadcrumbBlocker}" }

            val people = (topicTasks.flatMap { it.peopleTags } + topicCards.flatMap { it.peopleTags })
                .map { it.removePrefix("@").trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val contextWeight = entityMap[topic.lowercase()]
            val roleOrContext = contextWeight?.roleOrContext
                ?: if (topicCards.isNotEmpty()) {
                    topicCards.first().tldrSummary.take(90)
                } else {
                    "Active executive deliverable stream"
                }

            swimlanes.add(
                InitiativeSwimlaneModel(
                    topicName = topic,
                    roleOrContext = roleOrContext,
                    ragStatus = ragStatus,
                    criticalStage = criticalStage,
                    completionPercent = completionPercent,
                    completedStepCount = completedSteps,
                    totalStepCount = totalSteps,
                    nodes = nodes,
                    linkedCardsCount = topicCards.size,
                    hasCardRiskFlag = hasCardRisk,
                    activeBlockers = blockers,
                    peopleInvolved = people
                )
            )
        }

        return swimlanes.sortedWith(
            compareBy<InitiativeSwimlaneModel> { it.ragStatus.ordinal }
                .thenByDescending { it.nodes.size }
        )
    }
}

@Composable
fun ReiCommandViewSwitcherBar(
    selectedMode: ReiCommandViewMode,
    deliverableCount: Int,
    atRiskCount: Int,
    onSelectMode: (ReiCommandViewMode) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .testTag("rei_command_view_switcher"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReiCommandViewMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val tag = if (mode == ReiCommandViewMode.ACTION_DECK) {
                    "toggle_view_action_deck"
                } else {
                    "toggle_view_deliverable_runway"
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectMode(mode) }
                        .testTag(tag),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${mode.badge} • ${mode.label}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (mode == ReiCommandViewMode.DELIVERABLE_RUNWAY) {
                                val subText = if (atRiskCount > 0) {
                                    "$deliverableCount lanes • $atRiskCount at risk"
                                } else {
                                    "$deliverableCount active lanes"
                                }
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    text = "1 Thing Now + Q1–Q4",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliverableRunwaySection(
    swimlanes: List<InitiativeSwimlaneModel>,
    upcomingCalendarMeetings: List<NorthwellMeetingCandidate> = emptyList(),
    onSyncCalendar: () -> Unit = {},
    onSpawnTaskFromMeeting: (NorthwellMeetingCandidate) -> Unit = {},
    onToggleMicroStep: (ReiTask, String) -> Unit,
    onShiftTaskHorizon: (ReiTask, DeliverableHorizon) -> Unit,
    onOpenBreadcrumbModal: (ReiTask) -> Unit,
    onDraftItForTask: (ReiTask) -> Unit,
    onOpenVerify99Modal: (ReiTask) -> Unit,
    onEditTask: (ReiTask) -> Unit,
    onEntityClick: (String) -> Unit
) {
    val context = LocalContext.current
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onSyncCalendar()
    }
    var selectedHorizonFilter by remember { mutableStateOf<DeliverableHorizon?>(null) }
    var selectedTopicFilter by remember { mutableStateOf<String?>(null) }
    val expandedLanes = remember { mutableStateMapOf<String, Boolean>() }

    val totalInitiatives = swimlanes.size
    val redCount = swimlanes.count { it.ragStatus == DeliverableRagStatus.RED_SLA_RISK }
    val amberCount = swimlanes.count { it.ragStatus == DeliverableRagStatus.AMBER_BLOCKED }
    val greenCount = swimlanes.count { it.ragStatus == DeliverableRagStatus.GREEN_ON_TRACK }
    val overallStepsDone = swimlanes.sumOf { it.completedStepCount }
    val overallStepsTotal = swimlanes.sumOf { it.totalStepCount }.coerceAtLeast(1)
    val overallPct = ((overallStepsDone.toFloat() / overallStepsTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deliverable_runway_container"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 0. Upcoming Device Calendar Runway Strip
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .testTag("runway_calendar_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Calendar Runway",
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📅 UPCOMING CALENDAR RUNWAY (NEXT 7 DAYS)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TelemetryCyan
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CALENDAR
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                onSyncCalendar()
                            } else {
                                runCatching {
                                    calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }.onFailure {
                                    onSyncCalendar()
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("runway_sync_calendar_button")
                    ) {
                        Text("Sync Calendar", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (upcomingCalendarMeetings.isEmpty()) {
                    Text(
                        text = "No upcoming calendar events loaded. Tap 'Sync Calendar' to read device events via CalendarContract.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        upcomingCalendarMeetings.take(4).forEach { meeting ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${meeting.timeSlot} • ${meeting.title}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (meeting.stakeholders.isNotEmpty()) {
                                            Text(
                                                text = meeting.stakeholders.joinToString(" ") { s -> "@${s.removePrefix("@")}" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { onSpawnTaskFromMeeting(meeting) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("+ Prep Task", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 1. Portfolio Radar & Smart-Gantt Legend Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, ElectricIndigo.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📊 EXECUTIVE DELIVERABLE RUNWAY (SMART-GANTT)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$totalInitiatives Active Initiatives • $overallPct% Micro-Steps Complete",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Auto-structured from #Topics, @People dependencies & <2m micro-steps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    RagCircularProgressRing(
                        percentage = overallPct,
                        ragStatus = when {
                            redCount > 0 -> DeliverableRagStatus.RED_SLA_RISK
                            amberCount > 0 -> DeliverableRagStatus.AMBER_BLOCKED
                            else -> DeliverableRagStatus.GREEN_ON_TRACK
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // RAG Portfolio Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PortfolioKpiPill(
                        label = "🔴 $redCount At Risk",
                        color = CoralQ1Urgent,
                        modifier = Modifier.weight(1f)
                    )
                    PortfolioKpiPill(
                        label = "🟡 $amberCount Blocked",
                        color = AmberSteadyRisk,
                        modifier = Modifier.weight(1f)
                    )
                    PortfolioKpiPill(
                        label = "🟢 $greenCount On Track",
                        color = EmeraldAiCore,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Horizon Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedHorizonFilter == null,
                        onClick = { selectedHorizonFilter = null },
                        label = { Text("All Horizons", style = MaterialTheme.typography.labelSmall) }
                    )
                    DeliverableHorizon.entries.forEach { horizon ->
                        val active = selectedHorizonFilter == horizon
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedHorizonFilter = if (active) null else horizon
                            },
                            label = { Text(horizon.shortLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricIndigo.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier.testTag("horizon_filter_${horizon.name.lowercase()}")
                        )
                    }
                }

                // Topic Filter Chips Row
                if (swimlanes.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedTopicFilter == null,
                            onClick = { selectedTopicFilter = null },
                            label = { Text("All #Initiatives", style = MaterialTheme.typography.labelSmall) }
                        )
                        swimlanes.forEach { lane ->
                            val active = selectedTopicFilter == lane.topicName
                            FilterChip(
                                selected = active,
                                onClick = {
                                    selectedTopicFilter = if (active) null else lane.topicName
                                },
                                label = { Text("#${lane.topicName}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Swimlane Cards per #Initiative
        val filteredLanes = swimlanes.filter { lane ->
            val matchesTopic = selectedTopicFilter == null || lane.topicName == selectedTopicFilter
            val matchesHorizon = selectedHorizonFilter == null || lane.nodes.any { it.horizon == selectedHorizonFilter }
            matchesTopic && matchesHorizon
        }

        if (filteredLanes.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "No deliverables match the active horizon filter.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap 'All Horizons' above or dump a new thought below to auto-build a swimlane.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            filteredLanes.forEach { lane ->
                val isExpanded = expandedLanes[lane.topicName] ?: true
                InitiativeSwimlaneCard(
                    lane = lane,
                    selectedHorizonFilter = selectedHorizonFilter,
                    isExpanded = isExpanded,
                    onToggleExpand = { expandedLanes[lane.topicName] = !isExpanded },
                    onToggleMicroStep = onToggleMicroStep,
                    onShiftTaskHorizon = onShiftTaskHorizon,
                    onOpenBreadcrumbModal = onOpenBreadcrumbModal,
                    onDraftItForTask = onDraftItForTask,
                    onOpenVerify99Modal = onOpenVerify99Modal,
                    onEditTask = onEditTask,
                    onEntityClick = onEntityClick
                )
            }
        }
    }
}

@Composable
private fun PortfolioKpiPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RagCircularProgressRing(
    percentage: Int,
    ragStatus: DeliverableRagStatus
) {
    val ringColor = when (ragStatus) {
        DeliverableRagStatus.RED_SLA_RISK -> CoralQ1Urgent
        DeliverableRagStatus.AMBER_BLOCKED -> AmberSteadyRisk
        DeliverableRagStatus.GREEN_ON_TRACK -> EmeraldAiCore
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(50.dp)) {
            val strokeWidth = 5.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = (percentage.coerceIn(0, 100) / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = ringColor
        )
    }
}

@Composable
private fun InitiativeSwimlaneCard(
    lane: InitiativeSwimlaneModel,
    selectedHorizonFilter: DeliverableHorizon?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleMicroStep: (ReiTask, String) -> Unit,
    onShiftTaskHorizon: (ReiTask, DeliverableHorizon) -> Unit,
    onOpenBreadcrumbModal: (ReiTask) -> Unit,
    onDraftItForTask: (ReiTask) -> Unit,
    onOpenVerify99Modal: (ReiTask) -> Unit,
    onEditTask: (ReiTask) -> Unit,
    onEntityClick: (String) -> Unit
) {
    val ragColor = when (lane.ragStatus) {
        DeliverableRagStatus.RED_SLA_RISK -> CoralQ1Urgent
        DeliverableRagStatus.AMBER_BLOCKED -> AmberSteadyRisk
        DeliverableRagStatus.GREEN_ON_TRACK -> EmeraldAiCore
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .testTag("swimlane_card_${lane.topicName.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, ragColor.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Swimlane Header: RAG Ring + #Initiative Title + Context + Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RagCircularProgressRing(
                        percentage = lane.completionPercent,
                        ragStatus = lane.ragStatus
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ragColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = lane.ragStatus.shortBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ragColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${lane.completedStepCount}/${lane.totalStepCount} steps • ${lane.linkedCardsCount} cards",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "#${lane.topicName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { onEntityClick("#${lane.topicName}") }
                        )
                        Text(
                            text = lane.roleOrContext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand or Collapse Swimlane",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5-Stage Critical Path Stepper (Scope -> Unblock -> Execute -> 99% Check -> Shipped)
            CriticalPathStepperStrip(
                activeStage = lane.criticalStage,
                ragColor = ragColor
            )

            // People Stakeholders Involved Row
            if (lane.peopleInvolved.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                EntityPillsRow(
                    peopleTags = lane.peopleInvolved,
                    topicTags = emptyList(),
                    hasRiskOrBlocker = lane.hasCardRiskFlag,
                    onEntityClick = onEntityClick
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Smart-Gantt 4-Column Horizon Header Strip
                    SmartGanttColumnHeaderRow()

                    Spacer(modifier = Modifier.height(8.dp))

                    val visibleNodes = if (selectedHorizonFilter == null) {
                        lane.nodes
                    } else {
                        lane.nodes.filter { it.horizon == selectedHorizonFilter }
                    }

                    if (visibleNodes.isEmpty()) {
                        Text(
                            text = "No deliverables in this horizon column for #${lane.topicName}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            visibleNodes.forEach { node ->
                                SmartGanttDeliverableRow(
                                    node = node,
                                    onToggleMicroStep = onToggleMicroStep,
                                    onShiftTaskHorizon = onShiftTaskHorizon,
                                    onOpenBreadcrumbModal = onOpenBreadcrumbModal,
                                    onDraftItForTask = onDraftItForTask,
                                    onOpenVerify99Modal = onOpenVerify99Modal,
                                    onEditTask = onEditTask,
                                    onEntityClick = onEntityClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CriticalPathStepperStrip(
    activeStage: CriticalPathStage,
    ragColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CriticalPathStage.entries.forEach { stage ->
                val isCompleted = stage.stepIndex < activeStage.stepIndex
                val isCurrent = stage == activeStage
                val pillColor = when {
                    isCurrent -> ragColor
                    isCompleted -> EmeraldAiCore
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(pillColor)
                    )
                    Text(
                        text = stage.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent || isCompleted) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartGanttColumnHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DeliverableHorizon.entries.forEach { horizon ->
            val colColor = horizonAccentColor(horizon)
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                color = colColor.copy(alpha = 0.12f)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = horizon.columnHeader,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartGanttDeliverableRow(
    node: DeliverableTaskNode,
    onToggleMicroStep: (ReiTask, String) -> Unit,
    onShiftTaskHorizon: (ReiTask, DeliverableHorizon) -> Unit,
    onOpenBreadcrumbModal: (ReiTask) -> Unit,
    onDraftItForTask: (ReiTask) -> Unit,
    onOpenVerify99Modal: (ReiTask) -> Unit,
    onEditTask: (ReiTask) -> Unit,
    onEntityClick: (String) -> Unit
) {
    val task = node.task
    val barColor = when {
        task.isStalled -> CoralQ1Urgent
        task.status == TaskStatus.PAUSED_BREADCRUMB -> AmberSteadyRisk
        node.horizon == DeliverableHorizon.SHIPPED_99 -> EmeraldAiCore
        else -> horizonAccentColor(node.horizon)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, barColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gantt_deliverable_node_${task.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Top Row: Deliverable Title + Horizon Nudge Controls (◀ / ▶) + Edit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${task.quadrant.shortCode} • ${node.horizon.shortLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                        Text(
                            text = "(${node.completedSteps}/${node.totalSteps} steps)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.status == TaskStatus.DONE) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 1-Tap Timeline Nudge (Slide Left / Slide Right across Gantt Columns) + Edit
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val canShiftEarlier = node.horizon.index > 0
                    val canShiftLater = node.horizon.index < DeliverableHorizon.entries.lastIndex

                    IconButton(
                        onClick = {
                            if (canShiftEarlier) {
                                val target = DeliverableHorizon.entries[node.horizon.index - 1]
                                onShiftTaskHorizon(task, target)
                            }
                        },
                        enabled = canShiftEarlier,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("shift_horizon_left_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Move Deliverable Earlier",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (canShiftLater) {
                                val target = DeliverableHorizon.entries[node.horizon.index + 1]
                                onShiftTaskHorizon(task, target)
                            }
                        },
                        enabled = canShiftLater,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("shift_horizon_right_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Move Deliverable Later",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { onEditTask(task) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Edit Deliverable",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Visual 4-Column Smart-Gantt Span Track (Tap any column to move deliverable directly)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DeliverableHorizon.entries.forEach { colHorizon ->
                    val isActiveColumn = colHorizon == node.horizon
                    val isPassedColumn = colHorizon.index < node.horizon.index &&
                        node.horizon == DeliverableHorizon.SHIPPED_99

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isActiveColumn -> barColor.copy(alpha = 0.22f)
                                    isPassedColumn -> EmeraldAiCore.copy(alpha = 0.12f)
                                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                }
                            )
                            .clickable { onShiftTaskHorizon(task, colHorizon) }
                            .testTag("gantt_cell_${task.id}_${colHorizon.name.lowercase()}"),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isActiveColumn) {
                            // Inner Progress Fill inside the active Gantt Bar
                            val fillFraction = node.stepCompletionRatio.coerceAtLeast(0.18f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fillFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(barColor)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(node.stepCompletionRatio * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (task.status == TaskStatus.DONE) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else if (task.isStalled || task.status == TaskStatus.PAUSED_BREADCRUMB) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Blocker / Dependency Callout Banner (if Paused w/ Breadcrumb or Stalled)
            if (task.status == TaskStatus.PAUSED_BREADCRUMB && task.breadcrumbBlocker.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberSteadyRisk.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AmberSteadyRisk.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⛓️ DEPENDENCY BLOCKER: ${task.breadcrumbBlocker}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberSteadyRisk,
                                fontWeight = FontWeight.Bold
                            )
                            if (task.breadcrumbResumeStep.isNotBlank()) {
                                Text(
                                    text = "Next Unblock Move: ${task.breadcrumbResumeStep}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            text = "Ping / Unblock",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricIndigo,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onDraftItForTask(task) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            } else if (task.isStalled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ Deferred ${task.deferralCount}x — Risk of timeline slip",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoralQ1Urgent
                )
            }

            // Next Uncompleted Micro-Step inline checkable row
            val nextStep = task.microSteps.firstOrNull { !it.isCompleted }
            if (nextStep != null && task.status != TaskStatus.DONE) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleMicroStep(task, nextStep.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = nextStep.isCompleted,
                        onCheckedChange = { onToggleMicroStep(task, nextStep.id) },
                        colors = CheckboxDefaults.colors(checkedColor = EmeraldAiCore)
                    )
                    Text(
                        text = "Next <2m Milestone: ${nextStep.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stakeholder Pills + Quick Actions
            if (task.peopleTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.peopleTags.forEach { person ->
                            val clean = person.removePrefix("@").trim()
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElectricIndigo.copy(alpha = 0.14f),
                                modifier = Modifier.clickable { onEntityClick("@$clean") }
                            ) {
                                Text(
                                    text = "👤 @$clean",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Defer",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan,
                            modifier = Modifier
                                .clickable { onOpenBreadcrumbModal(task) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                        Text(
                            text = "Verify & Close",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAiCore,
                            modifier = Modifier
                                .clickable { onOpenVerify99Modal(task) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun horizonAccentColor(horizon: DeliverableHorizon): Color = when (horizon) {
    DeliverableHorizon.NOW_TODAY -> CoralQ1Urgent
    DeliverableHorizon.THIS_WEEK -> VioletQ2Build
    DeliverableHorizon.NEXT_UP -> TelemetryCyan
    DeliverableHorizon.SHIPPED_99 -> EmeraldAiCore
}
