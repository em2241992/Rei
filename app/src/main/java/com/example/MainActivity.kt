package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ReiNavTab
import com.example.ui.ReiViewModel
import com.example.ui.components.AdhdExecutiveBriefingDialog
import com.example.ui.components.AiCoreSelfTestInspectorDialog
import com.example.ui.components.BreadcrumbDeferralDialog
import com.example.ui.components.ClarifyWithReiDialog
import com.example.ui.components.EditKnowledgeCardDialog
import com.example.ui.components.EditReiTaskDialog
import com.example.ui.components.EntityHubDialog
import com.example.ui.components.LocalBackupRestoreDialog
import com.example.ui.components.PersistentQuickDumpBar
import com.example.ui.components.ReiExecutiveTopBar
import com.example.ui.components.RunPromptOnTaskDialog
import com.example.ui.components.TypeAndPasteBrainDumpDialog
import com.example.ui.components.Verify99FinishLineDialog
import com.example.ui.components.VipContextRulesDialog
import com.example.ui.components.VoicePresetSelectorDialog
import com.example.ui.components.WhereWasICollapsibleBanner
import com.example.ui.screens.CommsStrategyTab
import com.example.ui.screens.MeetingPrepTab
import com.example.ui.screens.ReiCommandTab
import com.example.ui.screens.ReiCommandViewMode
import com.example.ui.screens.SecondBrainTab
import com.example.ui.theme.ReiExecutiveTheme

class MainActivity : ComponentActivity() {

    private val reiViewModel: ReiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIncomingShareIntent(intent)
        }
        setContent {
            ReiExecutiveHubApp(reiViewModel = reiViewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent?.action == Intent.ACTION_SEND && incomingIntent.type?.startsWith("text/") == true) {
            val sharedText = incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
            val subject = incomingIntent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!sharedText.isNullOrBlank()) {
                reiViewModel.onExternalTextShared(sharedText, subject)
                incomingIntent.removeExtra(Intent.EXTRA_TEXT)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReiExecutiveHubApp(reiViewModel: ReiViewModel) {
    val uiState by reiViewModel.uiState.collectAsStateWithLifecycle()
    val allCards by reiViewModel.allCards.collectAsStateWithLifecycle()
    val allTasks by reiViewModel.allTasks.collectAsStateWithLifecycle()
    val allEntities by reiViewModel.allEntities.collectAsStateWithLifecycle()
    val recentTelemetry by reiViewModel.recentTelemetry.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(reiViewModel) {
        reiViewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // BackHandler for secondary tabs, Deliverable Runway sub-view, or open banners
    BackHandler(
        enabled = uiState.currentTab != ReiNavTab.REI_COMMAND ||
            uiState.commandViewMode != ReiCommandViewMode.ACTION_DECK ||
            uiState.showWhereWasIBanner
    ) {
        when {
            uiState.showWhereWasIBanner -> reiViewModel.toggleWhereWasIBanner()
            uiState.currentTab != ReiNavTab.REI_COMMAND -> reiViewModel.selectTab(ReiNavTab.REI_COMMAND)
            uiState.commandViewMode != ReiCommandViewMode.ACTION_DECK ->
                reiViewModel.selectCommandViewMode(ReiCommandViewMode.ACTION_DECK)
        }
    }

    val whereWasIState = remember(allTasks) {
        reiViewModel.computeWhereWasIState(allTasks)
    }

    val (heroTask, heroReason) = remember(allTasks, uiState.currentBandwidth) {
        reiViewModel.computeHeroTask(allTasks, uiState.currentBandwidth)
    }

    ReiExecutiveTheme(darkTheme = uiState.isDarkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ReiExecutiveTopBar(
                        engineStatus = uiState.engineStatus,
                        diagnostics = uiState.lastDiagnostics,
                        aiCoreDownloadProgress = uiState.aiCoreDownloadProgress,
                        isDarkTheme = uiState.isDarkTheme,
                        showWhereWasI = uiState.showWhereWasIBanner,
                        onToggleEngineMode = reiViewModel::toggleEngineMode,
                        onToggleWhereWasI = reiViewModel::toggleWhereWasIBanner,
                        onOpenCalendarCheck = reiViewModel::openCalendarCheckDialog,
                        onOpenVipRules = { reiViewModel.openVipContextSheet(true) },
                        onOpenBackupBridge = { reiViewModel.openBackupRestoreSheet(true) },
                        onOpenSelfTestInspector = { reiViewModel.openSelfTestInspectorSheet(true) },
                        onToggleTheme = reiViewModel::toggleTheme
                    )
                    WhereWasICollapsibleBanner(
                        visible = uiState.showWhereWasIBanner,
                        state = whereWasIState,
                        onResumeTask = reiViewModel::resumeBreadcrumbTask,
                        onDismiss = reiViewModel::toggleWhereWasIBanner
                    )
                }
            },
            bottomBar = {
                val isKeyboardOpen = WindowInsets.isImeVisible
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    PersistentQuickDumpBar(
                        text = uiState.quickDumpText,
                        isProcessing = uiState.isProcessingAi,
                        onTextChange = reiViewModel::updateQuickDumpText,
                        onOrganizeClick = { reiViewModel.organizeCurrentQuickDump() },
                        onParkItClick = reiViewModel::parkCurrentQuickDump,
                        onAskMoreDetailClick = reiViewModel::openClarifyForQuickDump,
                        onOpenVoicePresets = { reiViewModel.openVoicePresetSheet(true) },
                        onOpenTypeComposer = { reiViewModel.openTypeComposerSheet(true) },
                        onVoiceResult = { spoken -> reiViewModel.applyVoiceTranscript(spoken, autoOrganize = true) }
                    )

                    AnimatedVisibility(visible = !isKeyboardOpen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            ReiNavTab.entries.forEach { tab ->
                                val selected = uiState.currentTab == tab
                                val (filledIcon, outlinedIcon) = when (tab) {
                                    ReiNavTab.REI_COMMAND -> Icons.Filled.Bolt to Icons.Outlined.Bolt
                                    ReiNavTab.SECOND_BRAIN -> Icons.Filled.Hub to Icons.Outlined.Hub
                                    ReiNavTab.MEETING_PREP -> Icons.Filled.Groups to Icons.Outlined.Groups
                                    ReiNavTab.COMMS_STRATEGY -> Icons.Filled.RateReview to Icons.Outlined.RateReview
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { reiViewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) filledIcon else outlinedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                    ) {
                        when (uiState.currentTab) {
                            ReiNavTab.REI_COMMAND -> {
                                ReiCommandTab(
                                    tasks = allTasks,
                                    cards = allCards,
                                    entities = allEntities,
                                    viewMode = uiState.commandViewMode,
                                    heroTask = heroTask,
                                    heroReason = heroReason,
                                    currentBandwidth = uiState.currentBandwidth,
                                    learnedRhythmHint = uiState.learnedRhythmHint,
                                    lastOrganizedBannerTitle = uiState.lastOrganizedBannerTitle,
                                    onSelectViewMode = reiViewModel::selectCommandViewMode,
                                    onDismissOrganizedBanner = reiViewModel::dismissLastOrganizedBanner,
                                    onSelectBandwidth = reiViewModel::selectBandwidth,
                                    onToggleMicroStep = reiViewModel::toggleTaskMicroStep,
                                    onShrinkTask = reiViewModel::shrinkTask,
                                    onOpenBreadcrumbModal = { reiViewModel.openBreadcrumbModal(it) },
                                    onDraftItForTask = reiViewModel::launchDraftItFromTask,
                                    onOpenVerify99Modal = { reiViewModel.openVerify99Modal(it) },
                                    onRabbitHoleStop = reiViewModel::triggerRabbitHoleStop,
                                    onSnoozeTask = reiViewModel::snoozeTask,
                                    onChangeQuadrant = reiViewModel::changeTaskQuadrant,
                                    onShiftTaskHorizon = reiViewModel::shiftTaskDeliverableHorizon,
                                    onEditTask = { reiViewModel.openEditTaskSheet(it) },
                                    onEntityClick = { reiViewModel.openEntityHub(it) },
                                    onReopenTask = reiViewModel::reopenTask,
                                    onOpenVipRules = { reiViewModel.openVipContextSheet(true) },
                                    onOpenRunPromptForTask = { reiViewModel.openRunPromptOnTaskSheet(it) },
                                    onAskDetailForTask = reiViewModel::openClarifyForExistingTask
                                )
                            }

                            ReiNavTab.SECOND_BRAIN -> {
                                SecondBrainTab(
                                    allCards = allCards,
                                    searchedCards = uiState.searchedCards,
                                    allTasks = allTasks,
                                    allEntities = allEntities,
                                    ftsSearchQuery = uiState.ftsSearchQuery,
                                    selectedPersonFilter = uiState.selectedPersonFilter,
                                    selectedTopicFilter = uiState.selectedTopicFilter,
                                    selectedCategoryFilter = uiState.selectedCategoryFilter,
                                    onSearchQueryChange = reiViewModel::updateFtsSearchQuery,
                                    onSelectPersonFilter = reiViewModel::selectPersonFilter,
                                    onSelectTopicFilter = reiViewModel::selectTopicFilter,
                                    onSelectCategoryFilter = reiViewModel::selectCategoryFilter,
                                    onTogglePinCard = reiViewModel::toggleCardPin,
                                    onEditCard = { reiViewModel.openEditCardSheet(it) },
                                    onDraftItFromCard = reiViewModel::launchDraftItFromCard,
                                    onEntityClick = { reiViewModel.openEntityHub(it) },
                                    onEditTask = { reiViewModel.openEditTaskSheet(it) },
                                    onExtractBulletsAsTasks = reiViewModel::extractCardBulletsIntoMatrixTasks
                                )
                            }

                            ReiNavTab.MEETING_PREP -> {
                                MeetingPrepTab(
                                    allEntities = allEntities,
                                    selectedEntity = uiState.selectedMeetingEntity,
                                    synthesis = uiState.meetingSynthesis,
                                    checkedPrepItems = uiState.checkedPrepItems,
                                    onSelectEntity = reiViewModel::refreshMeetingPrep,
                                    onTogglePrepChecklistItem = reiViewModel::togglePrepChecklistItem,
                                    onOpenCard = { reiViewModel.openEditCardSheet(it) },
                                    onOpenTask = { reiViewModel.openEditTaskSheet(it) },
                                    onOpenEntityHub = { reiViewModel.openEntityHub(it) }
                                )
                            }

                            ReiNavTab.COMMS_STRATEGY -> {
                                CommsStrategyTab(
                                    selectedLens = uiState.selectedCommsLens,
                                    inputText = uiState.commsInputText,
                                    output = uiState.commsOutput,
                                    entities = allEntities,
                                    recentTelemetry = recentTelemetry,
                                    onSelectLens = reiViewModel::selectCommsLens,
                                    onInputTextChange = reiViewModel::updateCommsInputText,
                                    onRunStudio = { reiViewModel.runCommsStudio() },
                                    onOpenVipContextSheet = { reiViewModel.openVipContextSheet(true) },
                                    onOpenBackupSheet = { reiViewModel.openBackupRestoreSheet(true) },
                                    onCopyFeedback = reiViewModel::emitSnackbar
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- INTERACTIVE MODALS & BOTTOM SHEETS ---
        uiState.activeEntityHubTag?.let { tag ->
            EntityHubDialog(
                entityTag = tag,
                allEntities = allEntities,
                allCards = allCards,
                allTasks = allTasks,
                onDismiss = { reiViewModel.openEntityHub(null) },
                onJumpToMeetingPrep = reiViewModel::jumpToMeetingPrepForEntity,
                onOpenCard = { reiViewModel.openEditCardSheet(it) },
                onOpenTask = { reiViewModel.openEditTaskSheet(it) },
                onSaveEntityWeight = reiViewModel::saveEntityContextRule
            )
        }

        uiState.activeBreadcrumbTask?.let { task ->
            BreadcrumbDeferralDialog(
                task = task,
                onDismiss = { reiViewModel.openBreadcrumbModal(null) },
                onConfirmBreadcrumb = { stoppedAt, blocker, resumeStep ->
                    reiViewModel.confirmBreadcrumbDeferral(task, stoppedAt, blocker, resumeStep)
                }
            )
        }

        uiState.activeVerify99Task?.let { task ->
            Verify99FinishLineDialog(
                task = task,
                onDismiss = { reiViewModel.openVerify99Modal(null) },
                onConfirmDone = { reiViewModel.completeTaskAfter99Check(task) }
            )
        }

        uiState.editingCard?.let { card ->
            EditKnowledgeCardDialog(
                card = card,
                onDismiss = { reiViewModel.openEditCardSheet(null) },
                onSave = { title, tldr, cat, hasRisk, sections, people, topics ->
                    reiViewModel.saveEditedCard(card, title, tldr, cat, hasRisk, sections, people, topics)
                },
                onDelete = { reiViewModel.deleteKnowledgeCard(card.id) }
            )
        }

        uiState.editingTask?.let { task ->
            EditReiTaskDialog(
                task = task,
                onDismiss = { reiViewModel.openEditTaskSheet(null) },
                onSave = { title, quad, energy, dueNote, steps, people, topics ->
                    reiViewModel.saveEditedTask(task, title, quad, energy, dueNote, steps, people, topics)
                },
                onDelete = { reiViewModel.deleteReiTask(task.id) }
            )
        }

        if (uiState.showVipContextSheet) {
            VipContextRulesDialog(
                entities = allEntities,
                onDismiss = { reiViewModel.openVipContextSheet(false) },
                onSaveEntityRule = reiViewModel::saveEntityContextRule,
                onDeleteEntityRule = reiViewModel::deleteEntityContextRule
            )
        }

        if (uiState.showBackupRestoreSheet) {
            LocalBackupRestoreDialog(
                cardCount = allCards.size,
                taskCount = allTasks.size,
                entityCount = allEntities.size,
                onDismiss = { reiViewModel.openBackupRestoreSheet(false) },
                onExportJsonUri = reiViewModel::exportJsonBackupToUri,
                onImportJsonUri = reiViewModel::importJsonBackupFromUri,
                onExportSqliteUri = reiViewModel::exportSqliteToUri,
                onRestoreSqliteUri = reiViewModel::restoreSqliteFromUri,
                onStartFreshKeepVipRules = reiViewModel::startFreshKeepVipRules,
                onResetSeedData = reiViewModel::resetSeedData
            )
        }

        if (uiState.showVoicePresetSheet) {
            VoicePresetSelectorDialog(
                presets = reiViewModel.voicePresets,
                onDismiss = { reiViewModel.openVoicePresetSheet(false) },
                onSelectPreset = { transcript, autoOrganize ->
                    reiViewModel.applyVoiceTranscript(transcript, autoOrganize)
                }
            )
        }

        if (uiState.showTypeComposerSheet) {
            val (detectedPeople, detectedTopics) = remember(uiState.quickDumpText, allEntities) {
                reiViewModel.previewDetectedEntities(uiState.quickDumpText)
            }
            TypeAndPasteBrainDumpDialog(
                text = uiState.quickDumpText,
                sharedSourceLabel = uiState.sharedSourceLabel,
                detectedPeople = detectedPeople,
                detectedTopics = detectedTopics,
                allEntities = allEntities,
                onTextChange = reiViewModel::updateQuickDumpText,
                onDismiss = { reiViewModel.openTypeComposerSheet(false) },
                onOrganizeNow = { reiViewModel.organizeCurrentQuickDump() },
                onParkItNow = reiViewModel::parkCurrentQuickDump,
                onSwitchToVoicePresets = {
                    reiViewModel.openTypeComposerSheet(false)
                    reiViewModel.openVoicePresetSheet(true)
                }
            )
        }

        if (uiState.showSelfTestInspectorSheet) {
            AiCoreSelfTestInspectorDialog(
                diagnostics = uiState.lastDiagnostics,
                isProcessing = uiState.isProcessingAi,
                onDismiss = { reiViewModel.openSelfTestInspectorSheet(false) },
                onRunLiveProbe = reiViewModel::runLiveAiCoreSelfTest,
                onRunMalformedStressTest = reiViewModel::runMalformedNanoRepairStressTest
            )
        }

        if (uiState.showClarifySheet) {
            ClarifyWithReiDialog(
                sourceTitleOrText = uiState.clarifySourceText,
                targetTask = uiState.clarifyTargetTask,
                questions = uiState.clarifyingQuestions,
                preCheckGate = uiState.activePreCheckGate,
                onDismiss = reiViewModel::dismissClarifySheet,
                onApplyClarification = reiViewModel::applyClarificationAndEnrich,
                onSkipGateAndOrganizeAsIs = if (uiState.clarifyTargetTask == null) {
                    { reiViewModel.organizeCurrentQuickDump(bypassPreCheckGate = true) }
                } else {
                    null
                }
            )
        }

        uiState.activeAdhdBriefing?.let { briefing ->
            AdhdExecutiveBriefingDialog(
                briefing = briefing,
                onDismiss = reiViewModel::dismissAdhdBriefing,
                onShrinkTopTask = heroTask?.let { top ->
                    { reiViewModel.shrinkTask(top) }
                }
            )
        }

        if (uiState.showRunPromptOnTaskSheet) {
            RunPromptOnTaskDialog(
                initialTask = uiState.runPromptTargetTask,
                allTasks = allTasks,
                onDismiss = reiViewModel::dismissRunPromptOnTaskSheet,
                onAskMoreDetailForTask = { task ->
                    reiViewModel.openClarifyForExistingTask(task)
                },
                onSplitTaskIntoMultipleTasks = { task ->
                    reiViewModel.splitExistingTaskIntoMultipleTasks(task)
                },
                onShrinkTask = { task ->
                    reiViewModel.shrinkTask(task)
                },
                onStuckBreadcrumbForTask = { task ->
                    reiViewModel.dismissRunPromptOnTaskSheet()
                    reiViewModel.openBreadcrumbModal(task)
                },
                onTagWaitingOnPersonForTask = { task, person ->
                    reiViewModel.tagWaitingOnPersonForTask(task, person)
                },
                onSnoozeTask = { task, untilWhen ->
                    reiViewModel.snoozeTask(task, untilWhen)
                },
                onRabbitHoleStopForTask = { task ->
                    reiViewModel.triggerRabbitHoleStop(task)
                },
                onVerify99ForTask = { task ->
                    reiViewModel.dismissRunPromptOnTaskSheet()
                    reiViewModel.openVerify99Modal(task)
                },
                onReOrganizeTaskIntoSecondBrain = { task ->
                    reiViewModel.reOrganizeExistingTaskIntoSecondBrain(task)
                },
                onPrepMeetingForTask = { task ->
                    reiViewModel.prepMeetingFromExistingTask(task)
                },
                onRunCommsLensForTask = { task, lens ->
                    reiViewModel.runCommsLensOnExistingTask(task, lens)
                }
            )
        }
    }
}
