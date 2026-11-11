package com.emaratta.rei.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emaratta.rei.ai.NorthwellMeetingCandidate
import com.emaratta.rei.data.local.EnergyBandwidthState
import com.emaratta.rei.data.local.EnergyLoad
import com.emaratta.rei.data.local.EntityContextWeight
import com.emaratta.rei.data.local.KnowledgeCard
import com.emaratta.rei.data.local.ReiTask
import com.emaratta.rei.data.local.TaskQuadrant
import com.emaratta.rei.data.local.TaskStatus
import com.emaratta.rei.ui.components.EnergyBandwidthSelectorBar
import com.emaratta.rei.ui.components.EntityPillsRow
import com.emaratta.rei.ui.theme.AmberSteadyRisk
import com.emaratta.rei.ui.theme.CoralQ1Urgent
import com.emaratta.rei.ui.theme.ElectricIndigo
import com.emaratta.rei.ui.theme.EmeraldAiCore
import com.emaratta.rei.ui.theme.SlateParkedQ4
import com.emaratta.rei.ui.theme.TelemetryCyan
import com.emaratta.rei.ui.theme.VioletQ2Build

private enum class TaskListFilter {
    ALL,
    Q1,
    Q2,
    Q3,
    Q4,
    PAUSED,
    STALLED,
    DONE
}

@Composable
fun ReiCommandTab(
    tasks: List<ReiTask>,
    cards: List<KnowledgeCard>,
    entities: List<EntityContextWeight>,
    viewMode: ReiCommandViewMode,
    heroTask: ReiTask?,
    heroReason: String,
    currentBandwidth: EnergyBandwidthState,
    learnedRhythmHint: String,
    lastOrganizedBannerTitle: String?,
    onSelectViewMode: (ReiCommandViewMode) -> Unit,
    onDismissOrganizedBanner: () -> Unit,
    onSelectBandwidth: (EnergyBandwidthState) -> Unit,
    onToggleMicroStep: (ReiTask, String) -> Unit,
    onShrinkTask: (ReiTask) -> Unit,
    onOpenBreadcrumbModal: (ReiTask) -> Unit,
    onDraftItForTask: (ReiTask) -> Unit,
    onOpenVerify99Modal: (ReiTask) -> Unit,
    onChangeQuadrant: (ReiTask, TaskQuadrant) -> Unit,
    onShiftTaskHorizon: (ReiTask, DeliverableHorizon) -> Unit,
    onEditTask: (ReiTask) -> Unit,
    onEntityClick: (String) -> Unit,
    onReopenTask: (ReiTask) -> Unit,
    onOpenVipRules: () -> Unit = {},
    onOpenRunPromptForTask: (ReiTask?) -> Unit = {},
    onAskDetailForTask: (ReiTask) -> Unit = {},
    onRabbitHoleStop: (ReiTask) -> Unit = {},
    onSnoozeTask: (ReiTask, String) -> Unit = { _, _ -> },
    upcomingCalendarMeetings: List<NorthwellMeetingCandidate> = emptyList(),
    onSyncCalendar: () -> Unit = {},
    onSpawnTaskFromMeeting: (NorthwellMeetingCandidate) -> Unit = {}
) {
    // Keep default height compact (~160dp) so the Eisenhower Matrix below is always visible without scrolling
    var heroExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(TaskListFilter.ALL) }
    val expandedTaskIds = remember { mutableStateMapOf<Long, Boolean>() }

    val swimlanes = remember(tasks, cards, entities) {
        DeliverableRunwayBuilder.buildInitiativeSwimlanes(tasks, cards, entities)
    }

    // Pull snoozed tasks ("⏰ Snoozed") off the active visual list so they don't cause visual overwhelm
    val allNonDoneTasks = remember(tasks) { tasks.filter { it.status != TaskStatus.DONE } }
    val activeVisualTasks = remember(allNonDoneTasks) {
        allNonDoneTasks.filter { !it.dueDateNote.startsWith("⏰ Snoozed") }
    }
    val doneTasks = remember(tasks) { tasks.filter { it.status == TaskStatus.DONE } }

    val q1Count = remember(activeVisualTasks) { activeVisualTasks.count { it.quadrant == TaskQuadrant.Q1_DO_NOW } }
    val q2Count = remember(activeVisualTasks) { activeVisualTasks.count { it.quadrant == TaskQuadrant.Q2_PROTECT_BUILD } }
    val q3Count = remember(activeVisualTasks) { activeVisualTasks.count { it.quadrant == TaskQuadrant.Q3_BATCH_CONTAIN } }
    val q4Count = remember(allNonDoneTasks) { allNonDoneTasks.count { it.quadrant == TaskQuadrant.Q4_PARKED_LOT } }
    val pausedCount = remember(allNonDoneTasks) { allNonDoneTasks.count { it.status == TaskStatus.PAUSED_BREADCRUMB } }
    val stalledCount = remember(activeVisualTasks) { activeVisualTasks.count { it.isStalled } }

    val filteredTasks = remember(activeVisualTasks, allNonDoneTasks, doneTasks, selectedFilter, currentBandwidth) {
        when (selectedFilter) {
            TaskListFilter.ALL -> {
                if (currentBandwidth == EnergyBandwidthState.FRIED) {
                    // Quick Win Override: surface easiest low-drain tasks first
                    activeVisualTasks.sortedWith(
                        compareBy<ReiTask> { if (it.energyLoad == EnergyLoad.LOW_DRAIN) 0 else 1 }
                            .thenBy { it.microSteps.firstOrNull { s -> !s.isCompleted }?.estMinutes ?: 2 }
                    )
                } else {
                    activeVisualTasks.sortedWith(
                        compareBy<ReiTask> { it.quadrant.ordinal }.thenByDescending { it.isStalled }
                    )
                }
            }
            TaskListFilter.Q1 -> activeVisualTasks.filter { it.quadrant == TaskQuadrant.Q1_DO_NOW }
            TaskListFilter.Q2 -> activeVisualTasks.filter { it.quadrant == TaskQuadrant.Q2_PROTECT_BUILD }
            TaskListFilter.Q3 -> activeVisualTasks.filter { it.quadrant == TaskQuadrant.Q3_BATCH_CONTAIN }
            TaskListFilter.Q4 -> allNonDoneTasks.filter { it.quadrant == TaskQuadrant.Q4_PARKED_LOT }
            TaskListFilter.PAUSED -> allNonDoneTasks.filter { it.status == TaskStatus.PAUSED_BREADCRUMB }
            TaskListFilter.STALLED -> activeVisualTasks.filter { it.isStalled }
            TaskListFilter.DONE -> doneTasks
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("rei_command_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Sleek Single-Line Bandwidth Row: `[ ⚡ Peak ]  [ ● Standard ]  [ 🟢 Quick Win ]`
        item {
            EnergyBandwidthSelectorBar(
                selectedBandwidth = currentBandwidth,
                learnedRhythmHint = learnedRhythmHint,
                onSelectBandwidth = onSelectBandwidth
            )
        }

        if (lastOrganizedBannerTitle != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldAiCore.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✨ AI AUTO-STRUCTURED INTO MATRIX & BRAIN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAiCore
                            )
                            Text(
                                text = lastOrganizedBannerTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldAiCore,
                            modifier = Modifier
                                .clickable { onDismissOrganizedBanner() }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }

        // 2. Compact Top Task Hero Card (`🎯 TOP TASK — START HERE`, ~160dp height)
        if (viewMode == ReiCommandViewMode.ACTION_DECK) {
            item {
                HeroOneThingNowCard(
                    heroTask = heroTask,
                    currentBandwidth = currentBandwidth,
                    isExpanded = heroExpanded,
                    onToggleExpand = { heroExpanded = !heroExpanded },
                    onToggleMicroStep = onToggleMicroStep,
                    onShrinkTask = onShrinkTask,
                    onOpenBreadcrumbModal = onOpenBreadcrumbModal,
                    onDraftItForTask = onDraftItForTask,
                    onOpenVerify99Modal = onOpenVerify99Modal,
                    onRabbitHoleStop = onRabbitHoleStop,
                    onSnoozeTask = onSnoozeTask,
                    onOpenRunPromptForTask = { onOpenRunPromptForTask(it) },
                    onAskDetailForTask = onAskDetailForTask,
                    onEditTask = onEditTask,
                    onEntityClick = onEntityClick
                )
            }
        }

        // 3. Compact View Switcher (Tactical | Smart-Gantt) + Horizontal Scrollable Filter Chips
        item {
            FilterChipsAndViewToggleStrip(
                viewMode = viewMode,
                selectedFilter = selectedFilter,
                allOpenCount = activeVisualTasks.size,
                q1Count = q1Count,
                q2Count = q2Count,
                q3Count = q3Count,
                q4Count = q4Count,
                pausedCount = pausedCount,
                stalledCount = stalledCount,
                doneCount = doneTasks.size,
                onSelectViewMode = onSelectViewMode,
                onSelectFilter = { selectedFilter = it }
            )
        }

        // 4. Workspace Content: Either Smart-Gantt Deliverable Runway or Unified Flat Task List
        if (viewMode == ReiCommandViewMode.DELIVERABLE_RUNWAY) {
            item {
                DeliverableRunwaySection(
                    swimlanes = swimlanes,
                    upcomingCalendarMeetings = upcomingCalendarMeetings,
                    onSyncCalendar = onSyncCalendar,
                    onSpawnTaskFromMeeting = onSpawnTaskFromMeeting,
                    onToggleMicroStep = onToggleMicroStep,
                    onShiftTaskHorizon = onShiftTaskHorizon,
                    onOpenBreadcrumbModal = onOpenBreadcrumbModal,
                    onDraftItForTask = onDraftItForTask,
                    onOpenVerify99Modal = onOpenVerify99Modal,
                    onEditTask = onEditTask,
                    onEntityClick = onEntityClick
                )
            }
        } else {
            if (filteredTasks.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No tasks in this filter",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Switch filter chips above or use the bottom bar to unload a thought.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    val isTileExpanded = expandedTaskIds[task.id] ?: false
                    val accentColor = quadrantAccentColor(task.quadrant)
                    MatrixTaskCard(
                        task = task,
                        accentColor = accentColor,
                        isExpanded = isTileExpanded,
                        onToggleExpand = {
                            expandedTaskIds[task.id] = !isTileExpanded
                        },
                        onToggleMicroStep = onToggleMicroStep,
                        onShrinkTask = onShrinkTask,
                        onOpenBreadcrumbModal = onOpenBreadcrumbModal,
                        onDraftItForTask = onDraftItForTask,
                        onOpenVerify99Modal = onOpenVerify99Modal,
                        onRabbitHoleStop = onRabbitHoleStop,
                        onSnoozeTask = onSnoozeTask,
                        onOpenRunPromptForTask = { onOpenRunPromptForTask(it) },
                        onAskDetailForTask = onAskDetailForTask,
                        onChangeQuadrant = onChangeQuadrant,
                        onEditTask = onEditTask,
                        onEntityClick = onEntityClick,
                        onReopenTask = onReopenTask
                    )
                }
            }

            if (selectedFilter != TaskListFilter.DONE && doneTasks.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedFilter = TaskListFilter.DONE },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏁 ${doneTasks.size} Completed (99% Verified)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldAiCore
                            )
                            Text(
                                text = "View Done →",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsAndViewToggleStrip(
    viewMode: ReiCommandViewMode,
    selectedFilter: TaskListFilter,
    allOpenCount: Int,
    q1Count: Int,
    q2Count: Int,
    q3Count: Int,
    q4Count: Int,
    pausedCount: Int,
    stalledCount: Int,
    doneCount: Int,
    onSelectViewMode: (ReiCommandViewMode) -> Unit,
    onSelectFilter: (TaskListFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isTactical = viewMode == ReiCommandViewMode.ACTION_DECK
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isTactical) ElectricIndigo.copy(alpha = 0.24f) else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelectViewMode(ReiCommandViewMode.ACTION_DECK) }
                        .testTag("view_mode_action_deck")
                ) {
                    Text(
                        text = "Matrix",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isTactical) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTactical) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                val isGantt = viewMode == ReiCommandViewMode.DELIVERABLE_RUNWAY
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isGantt) TelemetryCyan.copy(alpha = 0.24f) else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelectViewMode(ReiCommandViewMode.DELIVERABLE_RUNWAY) }
                        .testTag("view_mode_deliverable_runway")
                ) {
                    Text(
                        text = "Gantt",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isGantt) FontWeight.Bold else FontWeight.Medium,
                        color = if (isGantt) TelemetryCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (viewMode == ReiCommandViewMode.ACTION_DECK) {
            FilterChip(
                selected = selectedFilter == TaskListFilter.ALL,
                onClick = { onSelectFilter(TaskListFilter.ALL) },
                label = { Text("All ($allOpenCount)", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("filter_chip_all")
            )
            FilterChip(
                selected = selectedFilter == TaskListFilter.Q1,
                onClick = { onSelectFilter(TaskListFilter.Q1) },
                label = { Text("🔴 Q1 ($q1Count)", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CoralQ1Urgent.copy(alpha = 0.22f),
                    selectedLabelColor = CoralQ1Urgent
                ),
                modifier = Modifier.testTag("filter_chip_q1")
            )
            FilterChip(
                selected = selectedFilter == TaskListFilter.Q2,
                onClick = { onSelectFilter(TaskListFilter.Q2) },
                label = { Text("🟢 Q2 ($q2Count)", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VioletQ2Build.copy(alpha = 0.22f),
                    selectedLabelColor = VioletQ2Build
                ),
                modifier = Modifier.testTag("filter_chip_q2")
            )
            FilterChip(
                selected = selectedFilter == TaskListFilter.Q3,
                onClick = { onSelectFilter(TaskListFilter.Q3) },
                label = { Text("⏳ Q3 ($q3Count)", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                    selectedLabelColor = TelemetryCyan
                ),
                modifier = Modifier.testTag("filter_chip_q3")
            )
            FilterChip(
                selected = selectedFilter == TaskListFilter.Q4,
                onClick = { onSelectFilter(TaskListFilter.Q4) },
                label = { Text("🅿️ Parked ($q4Count)", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("filter_chip_q4")
            )
            FilterChip(
                selected = selectedFilter == TaskListFilter.PAUSED,
                onClick = { onSelectFilter(TaskListFilter.PAUSED) },
                label = { Text("🍞 Stuck/Snoozed ($pausedCount)", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                    selectedLabelColor = TelemetryCyan
                ),
                modifier = Modifier.testTag("filter_chip_paused")
            )
            if (stalledCount > 0) {
                FilterChip(
                    selected = selectedFilter == TaskListFilter.STALLED,
                    onClick = { onSelectFilter(TaskListFilter.STALLED) },
                    label = { Text("⚠️ Stalled ($stalledCount)", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberSteadyRisk.copy(alpha = 0.22f),
                        selectedLabelColor = AmberSteadyRisk
                    ),
                    modifier = Modifier.testTag("filter_chip_stalled")
                )
            }
            if (doneCount > 0) {
                FilterChip(
                    selected = selectedFilter == TaskListFilter.DONE,
                    onClick = { onSelectFilter(TaskListFilter.DONE) },
                    label = { Text("🏁 Done ($doneCount)", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldAiCore.copy(alpha = 0.22f),
                        selectedLabelColor = EmeraldAiCore
                    ),
                    modifier = Modifier.testTag("filter_chip_done")
                )
            }
        }
    }
}

/**
 * 3. COMPACT TOP TASK HERO CARD (`🎯 TOP TASK — START HERE`, ~160dp height):
 * - Header: `🎯 START HERE` on the left, `⏱️ 2 min` + `🔴 Urgent` (or `🟢 Quick Win`) on the right.
 * - Body: Bold Task Title + ONLY the current active Micro-Step checkbox (hide remaining steps until checked or expanded).
 *   If resurfacing from a breadcrumb, show a subtle `🍞 Left off: [exact reason]` banner.
 * - Clean 1-Row Action Footer:
 *   - Primary button: `[ 🏁 Finish Line ]`
 *   - Quick Action Chips: `[ 🤏 Smaller Step ]` and `[ 🍞 I'm Stuck ]`
 *   - Overflow (`•••`): Houses `✍️ Draft It`, `🛑 Good Enough (Rabbit Hole)`, and `⏰ Snooze`.
 */
@Composable
private fun HeroOneThingNowCard(
    heroTask: ReiTask?,
    currentBandwidth: EnergyBandwidthState,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleMicroStep: (ReiTask, String) -> Unit,
    onShrinkTask: (ReiTask) -> Unit,
    onOpenBreadcrumbModal: (ReiTask) -> Unit,
    onDraftItForTask: (ReiTask) -> Unit,
    onOpenVerify99Modal: (ReiTask) -> Unit,
    onRabbitHoleStop: (ReiTask) -> Unit,
    onSnoozeTask: (ReiTask, String) -> Unit,
    onOpenRunPromptForTask: (ReiTask) -> Unit,
    onAskDetailForTask: (ReiTask) -> Unit,
    onEditTask: (ReiTask) -> Unit,
    onEntityClick: (String) -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .testTag("hero_one_thing_now_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (heroTask == null) {
                Text(
                    text = "🎯 START HERE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "All active tasks clear! Unload a thought in the bottom bar to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val steps = heroTask.microSteps
                val nextStep = steps.firstOrNull { !it.isCompleted } ?: steps.firstOrNull()
                val estMins = nextStep?.estMinutes ?: 2
                val isQuickWinMode = currentBandwidth == EnergyBandwidthState.FRIED ||
                    heroTask.energyLoad == EnergyLoad.LOW_DRAIN

                // HEADER: `🎯 START HERE` on the left, `⏱️ 2 min` + `🔴 Urgent` (or `🟢 Quick Win`) on the right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElectricIndigo.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "🎯 START HERE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Explicit Time Estimate Badge (Step 5: Combat Time Blindness)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldAiCore.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "⏱️ $estMins min",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAiCore,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        // Priority / Quick Win Visual Anchor Badge
                        val anchorColor = if (isQuickWinMode) EmeraldAiCore else CoralQ1Urgent
                        val anchorText = if (isQuickWinMode) "🟢 Quick Win" else "🔴 Urgent"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = anchorColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = anchorText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = anchorColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand or Collapse Top Task Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // If resurfacing from a breadcrumb, show a subtle `🍞 Left off: [exact reason]` banner right at the top
                if (heroTask.breadcrumbBlocker.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TelemetryCyan.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🍞 Left off: ${heroTask.breadcrumbBlocker}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TelemetryCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // BODY: Bold Task Title
                Text(
                    text = heroTask.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { onToggleExpand() }
                )

                // ONLY the current active Micro-Step checkbox visible by default
                if (nextStep != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleMicroStep(heroTask, nextStep.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = nextStep.isCompleted,
                                onCheckedChange = { onToggleMicroStep(heroTask, nextStep.id) },
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldAiCore),
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = nextStep.text,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (nextStep.isCompleted) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Optional Expanded Drawer (Remaining steps & entity pills hidden until expanded)
                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        if (steps.size > 1) {
                            Text(
                                text = "UPCOMING MICRO-STEPS (HIDDEN BY DEFAULT TO PREVENT OVERWHELM):",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            steps.forEach { step ->
                                if (step.id != nextStep?.id) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { onToggleMicroStep(heroTask, step.id) }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = step.isCompleted,
                                            onCheckedChange = { onToggleMicroStep(heroTask, step.id) },
                                            colors = CheckboxDefaults.colors(checkedColor = EmeraldAiCore),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = step.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            textDecoration = if (step.isCompleted) TextDecoration.LineThrough else null,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (heroTask.peopleTags.isNotEmpty() || heroTask.topicTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            EntityPillsRow(
                                peopleTags = heroTask.peopleTags,
                                topicTags = heroTask.topicTags,
                                hasRiskOrBlocker = heroTask.isStalled,
                                onEntityClick = onEntityClick
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // CLEAN 1-ROW ACTION FOOTER:
                // Primary: `[ 🏁 Finish Line ]` | Quick Chips: `[ 🤏 Smaller Step ]` & `[ 🍞 I'm Stuck ]` | Overflow (`•••`)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Primary Button: `[ 🏁 Verify & Close ]` (Triggers 99% Check modal)
                    Button(
                        onClick = { onOpenVerify99Modal(heroTask) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldAiCore,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("chip_99_check_${heroTask.id}")
                    ) {
                        Text(
                            text = "🏁 Verify & Close",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Quick Action Chip 1: `[ 🤏 Smaller Step ]`
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onShrinkTask(heroTask) }
                            .testTag("chip_shrink_it_${heroTask.id}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🤏 Smaller Step",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }

                    // Quick Action Chip 2: `[ 🍞 Defer ]`
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onOpenBreadcrumbModal(heroTask) }
                            .testTag("chip_breadcrumb_${heroTask.id}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🍞 Defer",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TelemetryCyan,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Overflow (`•••`): Houses `✍️ Draft It`, `🛑 Good Enough (Rabbit Hole)`, and `⏰ Snooze`
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("hero_task_overflow_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "More ADHD Protocols (Draft It, Good Enough, Snooze)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("✍️ Draft It (Rough First Draft)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.RateReview,
                                        contentDescription = null,
                                        tint = ElectricIndigo
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onDraftItForTask(heroTask)
                                },
                                modifier = Modifier.testTag("chip_draft_it_${heroTask.id}")
                            )
                            DropdownMenuItem(
                                text = { Text("🛑 Good Enough (Rabbit Hole)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.StopCircle,
                                        contentDescription = null,
                                        tint = CoralQ1Urgent
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onRabbitHoleStop(heroTask)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("⏰ Snooze: Later Today") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = SlateParkedQ4
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onSnoozeTask(heroTask, "Later Today")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⏰ Snooze: Tomorrow 9 AM") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = SlateParkedQ4
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onSnoozeTask(heroTask, "Tomorrow 9 AM")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⏰ Snooze: Next Week") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = SlateParkedQ4
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onSnoozeTask(heroTask, "Next Week")
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("❓ Clarify Physical Step") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.HelpOutline,
                                        contentDescription = null,
                                        tint = AmberSteadyRisk
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onAskDetailForTask(heroTask)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("⚡ All ADHD Protocols") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = TelemetryCyan
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenRunPromptForTask(heroTask)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("✏️ Edit Task") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onEditTask(heroTask)
                                },
                                modifier = Modifier.testTag("edit_hero_task_button")
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TacticalProtocolChipsRow(
    task: ReiTask,
    onShrinkTask: () -> Unit,
    onOpenBreadcrumbModal: () -> Unit,
    onDraftItForTask: () -> Unit,
    onOpenVerify99Modal: () -> Unit,
    onRabbitHoleStop: () -> Unit = {},
    onSnoozeTask: (String) -> Unit = {},
    onOpenRunPrompt: () -> Unit = {},
    onAskMoreDetail: () -> Unit = {}
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssistChip(
            onClick = onOpenVerify99Modal,
            label = { Text("🏁 Verify & Close", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = EmeraldAiCore
            ),
            modifier = Modifier.testTag("chip_99_check_${task.id}")
        )

        AssistChip(
            onClick = onShrinkTask,
            label = { Text("🤏 Smaller Step", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Compress,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = if (task.isStalled) AmberSteadyRisk else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("chip_shrink_it_${task.id}")
        )

        AssistChip(
            onClick = onOpenBreadcrumbModal,
            label = { Text("🍞 Defer", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.BookmarkAdded,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = TelemetryCyan
            ),
            modifier = Modifier.testTag("chip_breadcrumb_${task.id}")
        )

        AssistChip(
            onClick = onDraftItForTask,
            label = { Text("✍️ Draft It", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.RateReview,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = ElectricIndigo
            ),
            modifier = Modifier.testTag("chip_draft_it_${task.id}")
        )

        AssistChip(
            onClick = onRabbitHoleStop,
            label = { Text("🛑 Good Enough", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = CoralQ1Urgent
            )
        )

        AssistChip(
            onClick = { onSnoozeTask("Tomorrow 9 AM") },
            label = { Text("⏰ Snooze", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = SlateParkedQ4
            )
        )

        AssistChip(
            onClick = onAskMoreDetail,
            label = { Text("❓ Clarify", style = MaterialTheme.typography.labelMedium) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                leadingIconContentColor = AmberSteadyRisk
            ),
            modifier = Modifier.testTag("chip_clarify_${task.id}")
        )
    }
}

@Composable
private fun MatrixTaskCard(
    task: ReiTask,
    accentColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleMicroStep: (ReiTask, String) -> Unit,
    onShrinkTask: (ReiTask) -> Unit,
    onOpenBreadcrumbModal: (ReiTask) -> Unit,
    onDraftItForTask: (ReiTask) -> Unit,
    onOpenVerify99Modal: (ReiTask) -> Unit,
    onRabbitHoleStop: (ReiTask) -> Unit,
    onSnoozeTask: (ReiTask, String) -> Unit,
    onOpenRunPromptForTask: (ReiTask) -> Unit,
    onAskDetailForTask: (ReiTask) -> Unit,
    onChangeQuadrant: (ReiTask, TaskQuadrant) -> Unit,
    onEditTask: (ReiTask) -> Unit,
    onEntityClick: (String) -> Unit,
    onReopenTask: (ReiTask) -> Unit
) {
    val steps = task.microSteps
    val nextStep = steps.firstOrNull { !it.isCompleted } ?: steps.firstOrNull()
    val estMins = nextStep?.estMinutes ?: 2
    val isDone = task.status == TaskStatus.DONE

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggleExpand() }
            .testTag("task_item_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            // Whenever a deferred task resurfaces, show exact "🍞 Stuck Because: ..." right at the top
            if (task.breadcrumbBlocker.isNotBlank() && !isDone) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TelemetryCyan.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "🍞 Stuck Because: ${task.breadcrumbBlocker}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TelemetryCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = task.quadrant.shortCode,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "⏱️ $estMins min",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EmeraldAiCore
                        )
                        if (task.dueDateNote.isNotBlank()) {
                            Text(
                                text = "• ${task.dueDateNote}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDone) {
                        Text(
                            text = "Reopen",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onReopenTask(task) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        IconButton(
                            onClick = { onOpenVerify99Modal(task) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("verify_task_button_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = "Finish Line (99% Check)",
                                tint = EmeraldAiCore,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = { onEditTask(task) },
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("edit_task_button_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Edit Task",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand or Collapse Task Card",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(20.dp)
                    )
                }
            }

            if (!isDone && nextStep != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleMicroStep(task, nextStep.id) }
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = nextStep.isCompleted,
                        onCheckedChange = { onToggleMicroStep(task, nextStep.id) },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = nextStep.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded && !isDone) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (task.peopleTags.isNotEmpty() || task.topicTags.isNotEmpty() || task.isStalled) {
                        EntityPillsRow(
                            peopleTags = task.peopleTags,
                            topicTags = task.topicTags,
                            hasRiskOrBlocker = task.isStalled,
                            onEntityClick = onEntityClick
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quadrant (Learns VIP Bias):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TaskQuadrant.entries.forEach { targetQuad ->
                                val active = task.quadrant == targetQuad
                                val qColor = quadrantAccentColor(targetQuad)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (active) qColor.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onChangeQuadrant(task, targetQuad) }
                                        .testTag("override_${task.id}_to_${targetQuad.shortCode.lowercase()}")
                                ) {
                                    Text(
                                        text = targetQuad.shortCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) qColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TacticalProtocolChipsRow(
                        task = task,
                        onShrinkTask = { onShrinkTask(task) },
                        onOpenBreadcrumbModal = { onOpenBreadcrumbModal(task) },
                        onDraftItForTask = { onDraftItForTask(task) },
                        onOpenVerify99Modal = { onOpenVerify99Modal(task) },
                        onRabbitHoleStop = { onRabbitHoleStop(task) },
                        onSnoozeTask = { until -> onSnoozeTask(task, until) },
                        onOpenRunPrompt = { onOpenRunPromptForTask(task) },
                        onAskMoreDetail = { onAskDetailForTask(task) }
                    )
                }
            }
        }
    }
}

fun quadrantAccentColor(quadrant: TaskQuadrant): Color = when (quadrant) {
    TaskQuadrant.Q1_DO_NOW -> CoralQ1Urgent
    TaskQuadrant.Q2_PROTECT_BUILD -> VioletQ2Build
    TaskQuadrant.Q3_BATCH_CONTAIN -> TelemetryCyan
    TaskQuadrant.Q4_PARKED_LOT -> SlateParkedQ4
}
