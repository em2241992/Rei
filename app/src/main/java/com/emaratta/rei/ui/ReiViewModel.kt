package com.emaratta.rei.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emaratta.rei.ai.AdhdExecutiveBriefing
import com.emaratta.rei.ai.AiCoreGeminiNanoEngine
import com.emaratta.rei.ai.AiEngineDiagnostics
import com.emaratta.rei.ai.BrainDumpGateType
import com.emaratta.rei.ai.BrainDumpPreCheckGate
import com.emaratta.rei.ai.ClarifyingQuestionItem
import com.emaratta.rei.ai.CommsStrategyOutput
import com.emaratta.rei.ai.EngineRuntimeStatus
import com.emaratta.rei.ai.LocalSkillRouter
import com.emaratta.rei.ai.LocalSmartSimEngine
import com.emaratta.rei.ai.MeetingGlanceSynthesis
import com.emaratta.rei.ai.NorthwellMeetingCandidate
import com.emaratta.rei.ai.WhereWasIBannerState
import com.emaratta.rei.data.local.AiBackendConfig
import com.emaratta.rei.data.local.AiBackendMode
import com.emaratta.rei.data.local.BehavioralTelemetry
import com.emaratta.rei.data.local.CardCategory
import com.emaratta.rei.data.local.CommsStrategyLens
import com.emaratta.rei.data.local.EnergyBandwidthState
import com.emaratta.rei.data.local.EnergyLoad
import com.emaratta.rei.data.local.EntityContextWeight
import com.emaratta.rei.data.local.EntityType
import com.emaratta.rei.data.local.KnowledgeCard
import com.emaratta.rei.data.local.MicroStep
import com.emaratta.rei.data.local.OutlineSection
import com.emaratta.rei.data.local.ReiDatabase
import com.emaratta.rei.data.local.ReiJsonCodec
import com.emaratta.rei.data.local.ReiRepository
import com.emaratta.rei.data.local.ReiSettingsStore
import com.emaratta.rei.data.local.ReiTask
import com.emaratta.rei.data.local.TaskQuadrant
import com.emaratta.rei.data.local.TaskStatus
import com.emaratta.rei.ui.screens.DeliverableHorizon
import com.emaratta.rei.ui.screens.ReiCommandViewMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReiNavTab(val route: String, val label: String) {
    REI_COMMAND("rei_command", "Rei"),
    SECOND_BRAIN("second_brain", "Brain"),
    MEETING_PREP("meeting_prep", "Prep"),
    COMMS_STRATEGY("comms_strategy", "Studio")
}

data class VoiceSamplePreset(
    val id: String,
    val label: String,
    val badge: String,
    val transcript: String
)

data class ReiUiState(
    val currentTab: ReiNavTab = ReiNavTab.REI_COMMAND,
    val commandViewMode: ReiCommandViewMode = ReiCommandViewMode.ACTION_DECK,
    val isDarkTheme: Boolean = true,
    val debugSandboxMode: Boolean = false,
    val aiBackendConfig: AiBackendConfig = AiBackendConfig(),
    val engineStatus: EngineRuntimeStatus = EngineRuntimeStatus.SIM_FALLBACK,
    val hardwareAiCoreDetected: Boolean = false,
    val manualNanoTestOverride: Boolean = false,
    val lastDiagnostics: AiEngineDiagnostics = AiEngineDiagnostics(
        status = EngineRuntimeStatus.SIM_FALLBACK,
        rawFeatureStatusCode = "Initializing AI Backend..."
    ),
    val currentBandwidth: EnergyBandwidthState = EnergyBandwidthState.SHARP,
    val learnedRhythmHint: String = "Morning Peak: Suggesting ⚡ Peak",
    val showWhereWasIBanner: Boolean = false,
    val quickDumpText: String = "",
    val isProcessingAi: Boolean = false,
    val lastOrganizedBannerTitle: String? = null,
    // Tab 2: Second Brain filters & FTS search
    val ftsSearchQuery: String = "",
    val selectedPersonFilter: String? = null,
    val selectedTopicFilter: String? = null,
    val selectedCategoryFilter: CardCategory? = null,
    val searchedCards: List<KnowledgeCard>? = null,
    // Tab 3 & Runway: Meeting Prep + Upcoming Device Calendar Runway
    val selectedMeetingEntity: String = "",
    val meetingSynthesis: MeetingGlanceSynthesis? = null,
    val checkedPrepItems: Set<String> = emptySet(),
    val upcomingCalendarMeetings: List<NorthwellMeetingCandidate> = emptyList(),
    // Tab 4: Comms & Strategy ("Draft It" Studio)
    val selectedCommsLens: CommsStrategyLens = CommsStrategyLens.GROUNDED_COMMS,
    val commsInputText: String = "",
    val commsOutput: CommsStrategyOutput? = null,
    // Active Modals & Bottom Sheets
    val activeEntityHubTag: String? = null,
    val activeBreadcrumbTask: ReiTask? = null,
    val activeVerify99Task: ReiTask? = null,
    val editingCard: KnowledgeCard? = null,
    val editingTask: ReiTask? = null,
    val showSettingsDialog: Boolean = false,
    val showVipContextSheet: Boolean = false,
    val showBackupRestoreSheet: Boolean = false,
    val showVoicePresetSheet: Boolean = false,
    val showTypeComposerSheet: Boolean = false,
    val showSelfTestInspectorSheet: Boolean = false,
    val showClarifySheet: Boolean = false,
    val clarifyTargetTask: ReiTask? = null,
    val clarifySourceText: String = "",
    val clarifyingQuestions: List<ClarifyingQuestionItem> = emptyList(),
    val activePreCheckGate: BrainDumpPreCheckGate? = null,
    val activeAdhdBriefing: AdhdExecutiveBriefing? = null,
    val showRunPromptOnTaskSheet: Boolean = false,
    val runPromptTargetTask: ReiTask? = null,
    val sharedSourceLabel: String? = null,
    val aiCoreDownloadProgress: String? = null
)

class ReiViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsStore = ReiSettingsStore(application)
    private val database = ReiDatabase.getInstance(application)
    val repository = ReiRepository(application, database)
    private val simEngine = LocalSmartSimEngine()
    private val aiCoreEngine = AiCoreGeminiNanoEngine(
        localSimEngine = simEngine,
        appContext = application,
        initialConfig = settingsStore.loadAiBackendConfig()
    )
    val skillRouter = LocalSkillRouter(aiCoreEngine).apply {
        debugSandboxFallbackEnabled = settingsStore.isDebugSandboxEnabled()
    }

    private val _uiState = MutableStateFlow(
        ReiUiState(
            isDarkTheme = settingsStore.isDarkThemeEnabled(),
            debugSandboxMode = settingsStore.isDebugSandboxEnabled(),
            aiBackendConfig = settingsStore.loadAiBackendConfig()
        )
    )
    val uiState: StateFlow<ReiUiState> = _uiState.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    val allCards: StateFlow<List<KnowledgeCard>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<ReiTask>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEntities: StateFlow<List<EntityContextWeight>> = repository.allEntities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTelemetry: StateFlow<List<BehavioralTelemetry>> = repository.recentTelemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voicePresets: List<VoiceSamplePreset> = listOf(
        VoiceSamplePreset(
            id = "preset_sbo_escalation",
            label = "Sample: Cutover & Guarantor Hold",
            badge = "Multi-Task Dump",
            transcript = "Quick voice note after meeting with Corrie and Debbie on Epic SBO wave 2. We have an SLA risk on unmapped family guarantor splits causing duplicate statements. Need to freeze statement batching for 48 hours during cutover and have Wayne set up a warm-transfer queue rule to Diana's Specialized Servicing pod before Thursday at 2pm."
        ),
        VoiceSamplePreset(
            id = "preset_res_room_broadcast",
            label = "Sample: Escalation Intake Gate",
            badge = "SOP & Ops",
            transcript = "Sync with Lauren and Diana on Resolution Room noise. Frontline reps are dropping incomplete escalations into the floor Teams Broadcast channel without HAR IDs. We need to enforce the 3-field mandatory intake gate today and batch shift updates into a single 2:00 PM digest."
        ),
        VoiceSamplePreset(
            id = "preset_ar_capacity",
            label = "Sample: Q4 Collector Ring-Fence",
            badge = "ROI & Capacity",
            transcript = "Brain dump for Friday's VP review: blending 8 high-balance AR collectors into general inbound queues costs $38K a day in cash collections. Need Wayne to lock the IVR skill-group ring-fence from 8am to 1pm and build the ROI slide."
        )
    )

    init {
        viewModelScope.launch {
            aiCoreEngine.downloadProgressFlow.collect { progressText ->
                _uiState.update { it.copy(aiCoreDownloadProgress = progressText) }
            }
        }
        viewModelScope.launch {
            val sandboxOn = settingsStore.isDebugSandboxEnabled()
            val cfg = settingsStore.loadAiBackendConfig()
            skillRouter.debugSandboxFallbackEnabled = sandboxOn

            // Production Clean Boot: NEVER auto-seed demo data unless Debug Sandbox Mode is ON
            if (sandboxOn) {
                repository.populateDebugSandboxDataIfEmpty()
            }

            val (suggestedEnergy, hint) = repository.computeSuggestedEnergyByTimeOfDay()
            val runtimeStatus = aiCoreEngine.checkAvailability()
            val isHw = runtimeStatus == EngineRuntimeStatus.AICORE_ACTIVE ||
                runtimeStatus == EngineRuntimeStatus.LITERT_BUNDLE_READY
            val entitiesSnapshot = database.reiDao().getAllEntitiesOnce()
            val calendarEvents = skillRouter.readDeviceOrNorthwellCalendarMeetings(
                context = getApplication(),
                allEntities = entitiesSnapshot,
                includeDebugSandboxFallback = sandboxOn
            )
            val initialEntity = entitiesSnapshot.firstOrNull()?.entityName.orEmpty()

            _uiState.update {
                it.copy(
                    debugSandboxMode = sandboxOn,
                    aiBackendConfig = cfg,
                    currentBandwidth = suggestedEnergy,
                    learnedRhythmHint = hint,
                    hardwareAiCoreDetected = isHw,
                    engineStatus = runtimeStatus,
                    selectedMeetingEntity = initialEntity,
                    upcomingCalendarMeetings = calendarEvents,
                    lastDiagnostics = AiEngineDiagnostics(
                        status = runtimeStatus,
                        rawFeatureStatusCode = "${cfg.mode.shortBadge}: ${runtimeStatus.detailLabel}",
                        lastContextHeaderPreview = "[REI_CTX|<180tok] Ready (${cfg.mode.shortBadge})"
                    )
                )
            }
            if (initialEntity.isNotBlank()) {
                refreshMeetingPrep(initialEntity)
            }
        }

        // Keep Meeting Prep entity selection and FTS search reactive when underlying tables update
        viewModelScope.launch {
            combine(allCards, allTasks, allEntities) { cards, tasks, entities ->
                Triple(cards.size, tasks.size, entities)
            }.collect { (_, _, entities) ->
                val query = _uiState.value.ftsSearchQuery
                if (query.isNotBlank()) {
                    val results = repository.searchCardsInstant(query)
                    _uiState.update { it.copy(searchedCards = results) }
                } else {
                    _uiState.update { it.copy(searchedCards = null) }
                }
                if (_uiState.value.selectedMeetingEntity.isBlank() && entities.isNotEmpty()) {
                    val firstEntity = entities.first().entityName
                    _uiState.update { it.copy(selectedMeetingEntity = firstEntity) }
                    if (_uiState.value.currentTab == ReiNavTab.MEETING_PREP) {
                        refreshMeetingPrep(firstEntity)
                    }
                }
            }
        }
    }

    // --- Production Settings, 3-Backend AI Config, & Debug Sandbox Mode ---
    fun openSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun saveAiBackendConfig(config: AiBackendConfig, runProbe: Boolean = false) {
        settingsStore.saveAiBackendConfig(config)
        aiCoreEngine.updateBackendConfig(config)
        skillRouter.forceSimMode = config.mode == AiBackendMode.OFFLINE_HEURISTIC
        viewModelScope.launch {
            _uiState.update { it.copy(aiBackendConfig = config, isProcessingAi = true) }
            val status = aiCoreEngine.checkAvailability()
            _uiState.update {
                it.copy(
                    engineStatus = status,
                    isProcessingAi = false,
                    lastDiagnostics = it.lastDiagnostics.copy(
                        status = status,
                        rawFeatureStatusCode = "${config.mode.shortBadge} -> ${status.detailLabel}"
                    )
                )
            }
            if (runProbe) {
                runLiveAiCoreSelfTest()
            } else {
                emitSnackbar("Saved AI Backend: ${config.mode.shortBadge} (${status.badgeText})")
            }
        }
    }

    fun setDebugSandboxMode(enabled: Boolean) {
        settingsStore.setDebugSandboxEnabled(enabled)
        skillRouter.debugSandboxFallbackEnabled = enabled
        viewModelScope.launch {
            if (enabled) {
                repository.populateDebugSandboxDataIfEmpty()
            }
            val entitiesNow = database.reiDao().getAllEntitiesOnce()
            val meetings = skillRouter.readDeviceOrNorthwellCalendarMeetings(
                context = getApplication(),
                allEntities = entitiesNow,
                includeDebugSandboxFallback = enabled
            )
            val nextEntity = _uiState.value.selectedMeetingEntity.ifBlank {
                entitiesNow.firstOrNull()?.entityName.orEmpty()
            }
            _uiState.update {
                it.copy(
                    debugSandboxMode = enabled,
                    upcomingCalendarMeetings = meetings,
                    selectedMeetingEntity = nextEntity
                )
            }
            if (enabled && nextEntity.isNotBlank()) {
                refreshMeetingPrep(nextEntity)
            }
            emitSnackbar(
                if (enabled) {
                    "Debug Sandbox Mode ON — Sample cards, tasks, & fallback meetings enabled"
                } else {
                    "Debug Sandbox Mode OFF — Clean production mode active"
                }
            )
        }
    }

    fun purgeAllWorkspaceData() {
        viewModelScope.launch {
            settingsStore.setDebugSandboxEnabled(false)
            skillRouter.debugSandboxFallbackEnabled = false
            val msg = repository.purgeAllWorkspaceData()
            val realMeetings = skillRouter.readDeviceOrNorthwellCalendarMeetings(
                context = getApplication(),
                allEntities = emptyList(),
                includeDebugSandboxFallback = false
            )
            _uiState.update {
                it.copy(
                    debugSandboxMode = false,
                    selectedMeetingEntity = "",
                    meetingSynthesis = null,
                    commsInputText = "",
                    commsOutput = null,
                    upcomingCalendarMeetings = realMeetings,
                    showSettingsDialog = false,
                    showBackupRestoreSheet = false
                )
            }
            emitSnackbar(msg)
        }
    }

    // --- Native S25 Ultra Launcher AppShortcuts & Calendar Runway ---
    fun onShortcutQuickDump() {
        _uiState.update {
            it.copy(
                currentTab = ReiNavTab.REI_COMMAND,
                showTypeComposerSheet = true,
                sharedSourceLabel = "Quick Stash Shortcut"
            )
        }
    }

    fun onShortcutWhereWasI() {
        _uiState.update {
            it.copy(
                currentTab = ReiNavTab.REI_COMMAND,
                showWhereWasIBanner = true
            )
        }
    }

    fun refreshDeviceCalendarMeetings() {
        viewModelScope.launch {
            val meetings = skillRouter.readDeviceOrNorthwellCalendarMeetings(
                context = getApplication(),
                allEntities = allEntities.value,
                includeDebugSandboxFallback = _uiState.value.debugSandboxMode
            )
            _uiState.update { it.copy(upcomingCalendarMeetings = meetings) }
            emitSnackbar(
                if (meetings.isNotEmpty()) {
                    "Synced ${meetings.size} upcoming calendar meeting(s)"
                } else {
                    "No upcoming calendar events found in the next 7 days"
                }
            )
        }
    }

    fun spawnTaskFromCalendarMeeting(meeting: NorthwellMeetingCandidate) {
        viewModelScope.launch {
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val result = skillRouter.organizeBrainDump(
                rawText = "${meeting.suggestedTaskConnection} 📅 Due: ${meeting.timeSlot}",
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            repository.saveOrganizedCardAndTasks(result.card, result.extractedTasks)
            emitSnackbar("Added prep task for '${meeting.title.take(28)}'")
        }
    }

    fun selectTab(tab: ReiNavTab) {
        _uiState.update { it.copy(currentTab = tab) }
        if (tab == ReiNavTab.MEETING_PREP) {
            refreshDeviceCalendarMeetings()
            val target = _uiState.value.selectedMeetingEntity.ifBlank {
                allEntities.value.firstOrNull()?.entityName.orEmpty()
            }
            if (target.isNotBlank() && _uiState.value.meetingSynthesis == null) {
                refreshMeetingPrep(target)
            }
        }
        if (tab == ReiNavTab.COMMS_STRATEGY && _uiState.value.commsOutput == null && _uiState.value.commsInputText.isNotBlank()) {
            runCommsStudio()
        }
    }

    fun toggleTheme() {
        val next = !_uiState.value.isDarkTheme
        settingsStore.setDarkThemeEnabled(next)
        _uiState.update { it.copy(isDarkTheme = next) }
    }

    fun selectCommandViewMode(mode: ReiCommandViewMode) {
        _uiState.update { it.copy(commandViewMode = mode) }
        if (mode == ReiCommandViewMode.DELIVERABLE_RUNWAY) {
            val meetings = skillRouter.readDeviceOrNorthwellCalendarMeetings(
                context = getApplication(),
                allEntities = allEntities.value,
                includeDebugSandboxFallback = _uiState.value.debugSandboxMode
            )
            _uiState.update { it.copy(upcomingCalendarMeetings = meetings) }
        }
    }

    /**
     * Cycles through the 3 AI Backends + Offline Heuristic or opens Settings.
     */
    fun toggleEngineMode() {
        val currentCfg = _uiState.value.aiBackendConfig
        val modes = AiBackendMode.entries
        val nextMode = modes[(modes.indexOf(currentCfg.mode) + 1) % modes.size]
        saveAiBackendConfig(currentCfg.copy(mode = nextMode), runProbe = false)
    }

    fun selectBandwidth(state: EnergyBandwidthState) {
        _uiState.update {
            it.copy(
                currentBandwidth = state,
                learnedRhythmHint = "Active Bandwidth: ${state.emoji} ${state.label} (${state.targetLoad.label})"
            )
        }
        viewModelScope.launch {
            repository.recordEnergyCheckIn(state)
        }
        emitSnackbar("Bandwidth set to ${state.emoji} ${state.label} — Top Task recalibrated")
    }

    fun toggleWhereWasIBanner() {
        _uiState.update { it.copy(showWhereWasIBanner = !it.showWhereWasIBanner) }
    }

    /**
     * Step 2 — Conditional Calendar Review (Device Calendar via CalendarContract):
     * Reads upcoming meetings, displays a clean chronological list, and STOPs with one question:
     * "Do any of these need to be tied to existing tasks or generate new ones?"
     */
    fun openCalendarCheckDialog() {
        val meetings = skillRouter.readDeviceOrNorthwellCalendarMeetings(
            context = getApplication(),
            allEntities = allEntities.value,
            includeDebugSandboxFallback = _uiState.value.debugSandboxMode
        )
        _uiState.update { it.copy(upcomingCalendarMeetings = meetings) }

        val chips = if (meetings.isNotEmpty()) {
            meetings.map { m -> "Spawn task: ${m.suggestedTaskConnection}" } +
                listOf("Tie ${meetings.first().timeSlot} meeting to active Top Task")
        } else {
            listOf(
                "No upcoming device calendar events found — type a meeting prep action below"
            )
        }
        val gate = BrainDumpPreCheckGate(
            gateType = BrainDumpGateType.STEP_2_CALENDAR_NORTHWELL_REVIEW,
            flaggedItemText = if (meetings.isNotEmpty()) {
                "Device Calendar Scan (${meetings.size} upcoming meeting(s))"
            } else {
                "Device Calendar Scan (0 upcoming events in next 7 days)"
            },
            singleSpecificQuestion = "Do any of these need to be tied to existing tasks or generate new ones?",
            quickAnswerChips = chips,
            calendarMeetings = meetings
        )
        _uiState.update {
            it.copy(
                showClarifySheet = true,
                clarifyTargetTask = null,
                clarifySourceText = "",
                clarifyingQuestions = listOf(
                    ClarifyingQuestionItem(
                        id = "cal_review_q",
                        dimensionLabel = "STEP 2 • CALENDAR CHECK",
                        question = "Do any of these need to be tied to existing tasks or generate new ones?",
                        quickChips = chips
                    )
                ),
                activePreCheckGate = gate
            )
        }
    }

    fun updateQuickDumpText(text: String) {
        _uiState.update { it.copy(quickDumpText = text) }
    }

    fun openVoicePresetSheet(show: Boolean) {
        _uiState.update { it.copy(showVoicePresetSheet = show) }
    }

    fun openTypeComposerSheet(show: Boolean) {
        _uiState.update {
            it.copy(
                showTypeComposerSheet = show,
                sharedSourceLabel = if (!show) null else it.sharedSourceLabel
            )
        }
    }

    /**
     * Handles incoming Android Share-Sheet text (ACTION_SEND text/plain from Teams, Outlook, Browser, etc.)
     */
    fun onExternalTextShared(sharedText: String, subject: String? = null) {
        val combined = if (!subject.isNullOrBlank() && !sharedText.contains(subject, ignoreCase = true)) {
            "$subject — $sharedText".trim()
        } else {
            sharedText.trim()
        }
        if (combined.isBlank()) return
        _uiState.update {
            it.copy(
                currentTab = ReiNavTab.REI_COMMAND,
                quickDumpText = combined,
                showTypeComposerSheet = true,
                sharedSourceLabel = "Shared via Android Share Sheet"
            )
        }
        emitSnackbar("Captured shared text — Stash or Organize")
    }

    fun applyVoiceTranscript(transcript: String, autoOrganize: Boolean = false) {
        _uiState.update {
            it.copy(
                quickDumpText = transcript,
                showVoicePresetSheet = false
            )
        }
        if (autoOrganize) {
            organizeCurrentQuickDump()
        } else {
            emitSnackbar("Transcript loaded into Quick Dump Bar — tap Organize or Stash")
        }
    }

    // --- SKILL 1: Organize Brain Dump (With Step 1, Step 2, & Step 6 Pre-Check Gate) ---
    fun organizeCurrentQuickDump(bypassPreCheckGate: Boolean = false) {
        val raw = _uiState.value.quickDumpText.trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(showTypeComposerSheet = true) }
            return
        }

        if (!bypassPreCheckGate) {
            val gate = skillRouter.evaluateBrainDumpPreCheckGate(raw, allEntities.value)
            if (gate.gateType != BrainDumpGateType.NONE_PROCEED) {
                val vipWaitChips = allEntities.value
                    .filter { it.entityType == EntityType.PERSON }
                    .take(2)
                    .map { "⏳ Waiting On: @${it.entityName}" }
                val gateQuestions = buildList {
                    add(
                        ClarifyingQuestionItem(
                            id = "gate_primary_q",
                            dimensionLabel = when (gate.gateType) {
                                BrainDumpGateType.STEP_2_CALENDAR_NORTHWELL_REVIEW ->
                                    "STEP 2 • CALENDAR REVIEW (ONE QUESTION)"
                                BrainDumpGateType.STEP_6_FRUSTRATION_WALL_DETECTED ->
                                    "STEP 6 • PROACTIVE FRUSTRATION CHECK"
                                BrainDumpGateType.STEP_6_LOW_ENERGY_OVERRIDE ->
                                    "STEP 6 • ENERGY OVERRIDE"
                                else ->
                                    "STEP 1 • THE CLARIFICATION CHECK (ONE QUESTION)"
                            },
                            question = gate.singleSpecificQuestion,
                            quickChips = gate.quickAnswerChips
                        )
                    )
                    if (gate.missingDeadlineQuestion != null) {
                        add(
                            ClarifyingQuestionItem(
                                id = "gate_deadline_q",
                                dimensionLabel = "STEP 3 • DEADLINE & BLOCKER CHECK",
                                question = gate.missingDeadlineQuestion,
                                quickChips = listOf(
                                    "📅 Due: Today",
                                    "📅 Due: Tomorrow",
                                    "📅 Due: Friday"
                                ) + vipWaitChips
                            )
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        showTypeComposerSheet = false,
                        showClarifySheet = true,
                        clarifyTargetTask = null,
                        clarifySourceText = raw,
                        clarifyingQuestions = gateQuestions,
                        activePreCheckGate = gate
                    )
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessingAi = true,
                    showTypeComposerSheet = false,
                    showClarifySheet = false,
                    activePreCheckGate = null,
                    sharedSourceLabel = null
                )
            }
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val capThree = raw.contains("cap it", ignoreCase = true)
            val result = skillRouter.organizeBrainDump(
                rawText = raw,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth,
                capThreePerQuadrant = capThree
            )
            repository.saveOrganizedCardAndTasks(result.card, result.extractedTasks)
            val effectiveStatus = if (_uiState.value.manualNanoTestOverride) {
                EngineRuntimeStatus.AICORE_SIMULATED_OVERRIDE
            } else {
                result.diagnostics.status
            }
            _uiState.update {
                it.copy(
                    currentTab = ReiNavTab.REI_COMMAND,
                    commandViewMode = ReiCommandViewMode.ACTION_DECK,
                    quickDumpText = "",
                    isProcessingAi = false,
                    engineStatus = effectiveStatus,
                    lastOrganizedBannerTitle = "${result.extractedTasks.size} Matrix Task(s) + Second Brain Brief Created",
                    activeAdhdBriefing = result.briefing,
                    lastDiagnostics = result.diagnostics.copy(status = effectiveStatus)
                )
            }
            emitSnackbar("Added ${result.extractedTasks.size} individual task(s) to Eisenhower Matrix")
            if (_uiState.value.selectedMeetingEntity.isNotBlank()) {
                refreshMeetingPrep(_uiState.value.selectedMeetingEntity)
            }
        }
    }

    // --- SKILL 2: Stash (1-tap tangent stash into Q4_PARKED_LOT without disrupting focus) ---
    fun parkCurrentQuickDump() {
        val raw = _uiState.value.quickDumpText.trim()
        if (raw.isBlank()) {
            _uiState.update { it.copy(showTypeComposerSheet = true) }
            emitSnackbar("Type or dictate a thought to Stash into 🅿️ Parked")
            return
        }
        viewModelScope.launch {
            val (heroBefore, _) = skillRouter.selectHeroTaskForBandwidth(allTasks.value, _uiState.value.currentBandwidth)
            val currentMicroStep = heroBefore?.microSteps?.firstOrNull { !it.isCompleted }?.text
                ?: heroBefore?.title
                ?: "your current Top Task"
            val parkedTask = skillRouter.buildParkedLotTaskFromTangent(raw, allEntities.value)
            repository.insertOrUpdateTask(parkedTask)
            _uiState.update {
                it.copy(
                    quickDumpText = "",
                    showTypeComposerSheet = false,
                    sharedSourceLabel = null
                )
            }
            emitSnackbar("Stashed. Back to: $currentMicroStep")
        }
    }

    fun dismissLastOrganizedBanner() {
        _uiState.update { it.copy(lastOrganizedBannerTitle = null) }
    }

    fun dismissAdhdBriefing() {
        _uiState.update { it.copy(activeAdhdBriefing = null) }
    }

    // --- SKILL 2: Smaller Step ("Shrink It" — cuts current micro-step into <60s physical action) ---
    fun shrinkTask(task: ReiTask) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingAi = true, showRunPromptOnTaskSheet = false) }
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val alreadyShrunkCount = task.microSteps.count { it.id.startsWith("shrink_") }
            val (newSteps, diag) = skillRouter.shrinkTaskIntoMicroSteps(
                task = task,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            val completedPrefix = task.microSteps.filter { it.isCompleted }
            val mergedSteps = (completedPrefix + newSteps).take(5)
            val updated = task.copy(
                microStepsJson = ReiJsonCodec.encodeMicroSteps(mergedSteps),
                status = TaskStatus.ACTIVE
            )
            repository.insertOrUpdateTask(updated)
            repository.recordTaskShrinkTelemetry(task)
            val step1 = newSteps.firstOrNull()?.text ?: "Open window & type 1 line"
            _uiState.update {
                it.copy(
                    isProcessingAi = false,
                    engineStatus = diag.status,
                    lastDiagnostics = diag
                )
            }
            emitSnackbar("🤏 Smaller Step (<60s): $step1")

            if (alreadyShrunkCount >= 1 || updated.deferralCount >= 2) {
                promptProactiveStallDetection(updated)
            }
        }
    }

    fun promptProactiveStallDetection(task: ReiTask) {
        val gate = BrainDumpPreCheckGate(
            gateType = BrainDumpGateType.STEP_6_FRUSTRATION_WALL_DETECTED,
            flaggedItemText = task.title,
            singleSpecificQuestion = "Hitting a wall on this? Tap **Smaller Step** to shrink it, or **Defer** to leave a breadcrumb and switch tasks.",
            quickAnswerChips = listOf(
                "🤏 Smaller Step (<60s physical action)",
                "🍞 Defer (Leave Breadcrumb & Switch Tasks)",
                "🟢 Switch to Quick Win Mode"
            )
        )
        _uiState.update {
            it.copy(
                showClarifySheet = true,
                clarifyTargetTask = task,
                clarifySourceText = task.title,
                clarifyingQuestions = listOf(
                    ClarifyingQuestionItem(
                        id = "stall_wall_q",
                        dimensionLabel = "PROACTIVE STALL / FRUSTRATION DETECTION",
                        question = gate.singleSpecificQuestion,
                        quickChips = gate.quickAnswerChips
                    )
                ),
                activePreCheckGate = gate
            )
        }
    }

    fun toggleTaskMicroStep(task: ReiTask, stepId: String) {
        viewModelScope.launch {
            val updatedSteps = task.microSteps.map { step ->
                if (step.id == stepId) step.copy(isCompleted = !step.isCompleted) else step
            }
            val allDone = updatedSteps.isNotEmpty() && updatedSteps.all { it.isCompleted }
            val updatedTask = task.copy(
                microStepsJson = ReiJsonCodec.encodeMicroSteps(updatedSteps)
            )
            repository.insertOrUpdateTask(updatedTask)
            if (allDone && task.status != TaskStatus.DONE) {
                _uiState.update { it.copy(activeVerify99Task = updatedTask) }
            }
        }
    }

    // --- SKILL 2: Breadcrumb Deferral Modal ("🍞 Defer") ---
    fun openBreadcrumbModal(task: ReiTask?) {
        _uiState.update { it.copy(activeBreadcrumbTask = task) }
    }

    fun confirmBreadcrumbDeferral(
        task: ReiTask,
        stoppedAt: String,
        blocker: String,
        resumeStep: String
    ) {
        viewModelScope.launch {
            val cleanBlocker = blocker.trim().ifBlank { "Waiting on missing detail / stakeholder input" }
            repository.saveBreadcrumbDeferral(
                task = task,
                stoppedAt = stoppedAt.ifBlank {
                    task.microSteps.firstOrNull { !it.isCompleted }?.text ?: "Initial step"
                },
                blocker = cleanBlocker,
                resumeStep = resumeStep.ifBlank { "Resolve '$cleanBlocker' & take 1-minute next step" }
            )
            _uiState.update { it.copy(activeBreadcrumbTask = null) }
            val remainingActive = allTasks.value.filter { it.id != task.id && it.status == TaskStatus.ACTIVE }
            val (nextTop, _) = skillRouter.selectHeroTaskForBandwidth(remainingActive, _uiState.value.currentBandwidth)
            val nextTitle = nextTop?.title ?: "Next open task"
            emitSnackbar("🍞 Deferred ('Stuck Because: $cleanBlocker') — Next: ${nextTitle.take(32)}")
            if (_uiState.value.selectedMeetingEntity.isNotBlank()) {
                refreshMeetingPrep(_uiState.value.selectedMeetingEntity)
            }
        }
    }

    fun resumeBreadcrumbTask(task: ReiTask) {
        viewModelScope.launch {
            repository.insertOrUpdateTask(
                task.copy(status = TaskStatus.ACTIVE)
            )
            _uiState.update { it.copy(showWhereWasIBanner = false, currentTab = ReiNavTab.REI_COMMAND) }
            emitSnackbar("Resumed '${task.title.take(32)}' — Stuck Because: ${task.breadcrumbBlocker.ifBlank { "Resolved" }}")
        }
    }

    // --- SKILL 2: Verify & Close (99% Finish-Line Check Modal) ---
    fun openVerify99Modal(task: ReiTask?) {
        _uiState.update { it.copy(activeVerify99Task = task) }
    }

    fun completeTaskAfter99Check(task: ReiTask) {
        viewModelScope.launch {
            val completedSteps = task.microSteps.map { it.copy(isCompleted = true) }
            repository.insertOrUpdateTask(
                task.copy(
                    status = TaskStatus.DONE,
                    microStepsJson = ReiJsonCodec.encodeMicroSteps(completedSteps)
                )
            )
            _uiState.update { it.copy(activeVerify99Task = null) }
            emitSnackbar("Verified & Closed '${task.title.take(30)}'")
            if (_uiState.value.selectedMeetingEntity.isNotBlank()) {
                refreshMeetingPrep(_uiState.value.selectedMeetingEntity)
            }
        }
    }

    fun reopenTask(task: ReiTask) {
        viewModelScope.launch {
            repository.insertOrUpdateTask(task.copy(status = TaskStatus.ACTIVE))
            emitSnackbar("Reopened '${task.title.take(30)}'")
        }
    }

    fun changeTaskQuadrant(task: ReiTask, newQuadrant: TaskQuadrant) {
        if (task.quadrant == newQuadrant) return
        viewModelScope.launch {
            val learnedMessage = repository.overrideTaskQuadrantAndLearn(task, newQuadrant)
            emitSnackbar(learnedMessage)
        }
    }

    fun shiftTaskDeliverableHorizon(task: ReiTask, targetHorizon: DeliverableHorizon) {
        if (targetHorizon == DeliverableHorizon.SHIPPED_99 && task.status != TaskStatus.DONE) {
            _uiState.update { it.copy(activeVerify99Task = task) }
            return
        }
        viewModelScope.launch {
            val newStatus = if (task.status == TaskStatus.DONE && targetHorizon != DeliverableHorizon.SHIPPED_99) {
                TaskStatus.ACTIVE
            } else {
                task.status
            }
            val updated = task.copy(
                dueDateNote = targetHorizon.defaultDueNote,
                quadrant = targetHorizon.mappedQuadrant,
                status = newStatus
            )
            repository.insertOrUpdateTask(updated)
            if (task.quadrant != targetHorizon.mappedQuadrant) {
                repository.overrideTaskQuadrantAndLearn(updated, targetHorizon.mappedQuadrant)
            }
            emitSnackbar("Moved '${task.title.take(26)}' to ${targetHorizon.shortLabel} (${targetHorizon.mappedQuadrant.shortCode})")
        }
    }

    fun launchDraftItFromTask(task: ReiTask) {
        val draftPrompt = buildString {
            append(task.title)
            if (task.breadcrumbBlocker.isNotBlank()) {
                append(". Current blocker: ${task.breadcrumbBlocker}.")
            }
            if (task.peopleTags.isNotEmpty()) {
                append(" Stakeholders: ${task.peopleTags.joinToString(", ") { "@$it" }}.")
            }
        }
        _uiState.update {
            it.copy(
                currentTab = ReiNavTab.COMMS_STRATEGY,
                commsInputText = draftPrompt
            )
        }
        runCommsStudio(customInput = draftPrompt)
    }

    fun launchDraftItFromCard(card: KnowledgeCard) {
        _uiState.update {
            it.copy(
                currentTab = ReiNavTab.COMMS_STRATEGY,
                commsInputText = card.tldrSummary
            )
        }
        runCommsStudio(customInput = card.tldrSummary)
    }

    // --- Tab 2: Second Brain Search & Filtering ---
    fun updateFtsSearchQuery(query: String) {
        _uiState.update { it.copy(ftsSearchQuery = query) }
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.update { it.copy(searchedCards = null) }
            } else {
                val results = repository.searchCardsInstant(query)
                _uiState.update { it.copy(searchedCards = results) }
            }
        }
    }

    fun selectPersonFilter(person: String?) {
        _uiState.update {
            it.copy(selectedPersonFilter = if (it.selectedPersonFilter == person) null else person)
        }
    }

    fun selectTopicFilter(topic: String?) {
        _uiState.update {
            it.copy(selectedTopicFilter = if (it.selectedTopicFilter == topic) null else topic)
        }
    }

    fun selectCategoryFilter(category: CardCategory?) {
        _uiState.update {
            it.copy(selectedCategoryFilter = if (it.selectedCategoryFilter == category) null else category)
        }
    }

    fun toggleCardPin(card: KnowledgeCard) {
        viewModelScope.launch {
            repository.togglePinCard(card)
            emitSnackbar(if (card.isPinned) "Unpinned card" else "Pinned card to top of Second Brain")
        }
    }

    fun openEditCardSheet(card: KnowledgeCard?) {
        _uiState.update { it.copy(editingCard = card) }
    }

    fun saveEditedCard(
        card: KnowledgeCard,
        newTitle: String,
        newTldr: String,
        newCategory: CardCategory,
        newHasRisk: Boolean,
        newSections: List<OutlineSection>,
        newPeopleTags: List<String>,
        newTopicTags: List<String>
    ) {
        viewModelScope.launch {
            val updated = card.copy(
                title = newTitle.trim().ifBlank { card.title },
                tldrSummary = newTldr.trim().ifBlank { card.tldrSummary },
                category = newCategory,
                hasRiskOrBlocker = newHasRisk,
                outlineSectionsJson = ReiJsonCodec.encodeOutlineSections(newSections),
                peopleTags = newPeopleTags.map { it.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct(),
                topicTags = newTopicTags.map { it.removePrefix("#").trim() }.filter { it.isNotBlank() }.distinct()
            )
            repository.insertOrUpdateCard(updated)
            _uiState.update { it.copy(editingCard = null) }
            emitSnackbar("Updated Knowledge Card '${updated.title.take(28)}'")
            if (_uiState.value.selectedMeetingEntity.isNotBlank()) {
                refreshMeetingPrep(_uiState.value.selectedMeetingEntity)
            }
        }
    }

    fun deleteKnowledgeCard(cardId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
            _uiState.update { it.copy(editingCard = null) }
            emitSnackbar("Deleted Knowledge Card")
        }
    }

    fun openEditTaskSheet(task: ReiTask?) {
        _uiState.update { it.copy(editingTask = task) }
    }

    fun saveEditedTask(
        task: ReiTask,
        newTitle: String,
        newQuadrant: TaskQuadrant,
        newEnergyLoad: EnergyLoad,
        newDueDateNote: String,
        newSteps: List<MicroStep>,
        newPeopleTags: List<String>,
        newTopicTags: List<String>
    ) {
        viewModelScope.launch {
            val quadChanged = task.quadrant != newQuadrant
            val updated = task.copy(
                title = newTitle.trim().ifBlank { task.title },
                quadrant = newQuadrant,
                energyLoad = newEnergyLoad,
                dueDateNote = newDueDateNote.trim().ifBlank { "Today" },
                microStepsJson = ReiJsonCodec.encodeMicroSteps(newSteps),
                peopleTags = newPeopleTags.map { it.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct(),
                topicTags = newTopicTags.map { it.removePrefix("#").trim() }.filter { it.isNotBlank() }.distinct()
            )
            repository.insertOrUpdateTask(updated)
            if (quadChanged) {
                val msg = repository.overrideTaskQuadrantAndLearn(updated, newQuadrant)
                emitSnackbar(msg)
            } else {
                emitSnackbar("Saved changes to task '${updated.title.take(28)}'")
            }
            _uiState.update { it.copy(editingTask = null) }
            if (_uiState.value.selectedMeetingEntity.isNotBlank()) {
                refreshMeetingPrep(_uiState.value.selectedMeetingEntity)
            }
        }
    }

    fun deleteReiTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            _uiState.update { it.copy(editingTask = null) }
            emitSnackbar("Removed task")
        }
    }

    // --- Unified Entity Hub Sheet (@Person / #Topic Bi-Directional Link Sheet) ---
    fun openEntityHub(entityTag: String?) {
        val clean = entityTag?.removePrefix("@")?.removePrefix("#")?.trim()
        _uiState.update { it.copy(activeEntityHubTag = clean) }
    }

    fun jumpToMeetingPrepForEntity(entityTag: String) {
        val clean = entityTag.removePrefix("@").removePrefix("#").trim()
        _uiState.update {
            it.copy(
                activeEntityHubTag = null,
                currentTab = ReiNavTab.MEETING_PREP,
                selectedMeetingEntity = clean
            )
        }
        refreshMeetingPrep(clean)
    }

    // --- Tab 3: Contextual "Prep for Meetings" Aggregator & 30-Second Glance Card ---
    fun refreshMeetingPrep(entityName: String) {
        val clean = entityName.removePrefix("@").removePrefix("#").trim()
            .ifBlank { allEntities.value.firstOrNull()?.entityName.orEmpty() }
        if (clean.isBlank()) {
            _uiState.update { it.copy(selectedMeetingEntity = "", meetingSynthesis = null) }
            return
        }
        _uiState.update { it.copy(selectedMeetingEntity = clean, isProcessingAi = true) }
        viewModelScope.launch {
            val sqlBundle = repository.queryMeetingPrepBundle(clean)
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val synthesis = skillRouter.synthesizeMeetingPrepBrief(
                sqlBundle = sqlBundle,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            _uiState.update {
                it.copy(
                    meetingSynthesis = synthesis,
                    checkedPrepItems = emptySet(),
                    isProcessingAi = false,
                    engineStatus = synthesis.diagnostics.status,
                    lastDiagnostics = synthesis.diagnostics
                )
            }
        }
    }

    fun togglePrepChecklistItem(item: String) {
        _uiState.update { state ->
            val next = state.checkedPrepItems.toMutableSet()
            if (!next.add(item)) next.remove(item)
            state.copy(checkedPrepItems = next)
        }
    }

    // --- Tab 4: Strategy & Grounded Comms Studio ("Draft It") ---
    fun selectCommsLens(lens: CommsStrategyLens) {
        _uiState.update { it.copy(selectedCommsLens = lens) }
        if (_uiState.value.commsInputText.isNotBlank()) {
            runCommsStudio(customLens = lens)
        }
    }

    fun updateCommsInputText(text: String) {
        _uiState.update { it.copy(commsInputText = text) }
    }

    fun runCommsStudio(
        customLens: CommsStrategyLens? = null,
        customInput: String? = null
    ) {
        val lens = customLens ?: _uiState.value.selectedCommsLens
        val input = (customInput ?: _uiState.value.commsInputText).trim()
        if (input.isBlank()) {
            emitSnackbar("Enter or paste a rough thought first to generate a draft")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingAi = true) }
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val output = skillRouter.runCommsStrategyStudio(
                lens = lens,
                rawInput = input,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            _uiState.update {
                it.copy(
                    commsOutput = output,
                    isProcessingAi = false,
                    engineStatus = output.diagnostics.status,
                    lastDiagnostics = output.diagnostics
                )
            }
        }
    }

    // --- VIP Context & Learned Rules Manager ---
    fun openVipContextSheet(show: Boolean) {
        _uiState.update { it.copy(showVipContextSheet = show) }
    }

    fun saveEntityContextRule(
        entityName: String,
        entityType: EntityType,
        roleOrContext: String,
        quadrantBias: TaskQuadrant?,
        energyBias: EnergyLoad?
    ) {
        val cleanName = entityName.removePrefix("@").removePrefix("#").trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val existing = allEntities.value.find { it.entityName.equals(cleanName, ignoreCase = true) }
            repository.upsertEntityContextWeight(
                EntityContextWeight(
                    entityName = existing?.entityName ?: cleanName,
                    entityType = entityType,
                    roleOrContext = roleOrContext.trim().ifBlank { "Executive Stakeholder" },
                    learnedQuadrantBias = quadrantBias,
                    learnedEnergyBias = energyBias,
                    interactionCount = (existing?.interactionCount ?: 0) + 1
                )
            )
            emitSnackbar("Updated VIP Rule for ${entityType.prefix}$cleanName")
            val prepTarget = _uiState.value.selectedMeetingEntity.ifBlank { cleanName }
            refreshMeetingPrep(prepTarget)
        }
    }

    fun deleteEntityContextRule(entityName: String) {
        viewModelScope.launch {
            repository.deleteEntityContextWeight(entityName)
            emitSnackbar("Removed VIP Rule '$entityName'")
        }
    }

    // --- Local Backup & Restore Bridge ---
    fun openBackupRestoreSheet(show: Boolean) {
        _uiState.update { it.copy(showBackupRestoreSheet = show) }
    }

    fun exportJsonBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val res = repository.writeJsonBackupToUri(uri)
            res.fold(
                onSuccess = { emitSnackbar(it) },
                onFailure = { emitSnackbar("JSON Export failed: ${it.message}") }
            )
        }
    }

    fun importJsonBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val res = repository.readJsonBackupFromUri(uri)
            res.fold(
                onSuccess = {
                    emitSnackbar(it)
                    val target = _uiState.value.selectedMeetingEntity.ifBlank {
                        database.reiDao().getAllEntitiesOnce().firstOrNull()?.entityName.orEmpty()
                    }
                    if (target.isNotBlank()) refreshMeetingPrep(target)
                },
                onFailure = { emitSnackbar("JSON Restore failed: ${it.message}") }
            )
        }
    }

    fun exportSqliteToUri(uri: Uri) {
        viewModelScope.launch {
            val res = repository.exportSqliteDatabaseToUri(uri)
            res.fold(
                onSuccess = { emitSnackbar(it) },
                onFailure = { emitSnackbar("SQLite Export failed: ${it.message}") }
            )
        }
    }

    fun restoreSqliteFromUri(uri: Uri) {
        viewModelScope.launch {
            val res = repository.restoreSqliteDatabaseFromUri(uri)
            res.fold(
                onSuccess = {
                    emitSnackbar(it)
                    val target = _uiState.value.selectedMeetingEntity.ifBlank {
                        database.reiDao().getAllEntitiesOnce().firstOrNull()?.entityName.orEmpty()
                    }
                    if (target.isNotBlank()) refreshMeetingPrep(target)
                },
                onFailure = { emitSnackbar("SQLite Restore failed: ${it.message}") }
            )
        }
    }

    fun resetSeedData() {
        setDebugSandboxMode(true)
        viewModelScope.launch {
            val msg = repository.resetToExecutiveSeedData()
            emitSnackbar(msg)
            refreshMeetingPrep("Corrie")
        }
    }

    fun startFreshKeepVipRules() {
        viewModelScope.launch {
            settingsStore.setDebugSandboxEnabled(false)
            skillRouter.debugSandboxFallbackEnabled = false
            val msg = repository.startFreshKeepVipRules()
            _uiState.update { it.copy(debugSandboxMode = false) }
            emitSnackbar(msg)
            if (_uiState.value.selectedMeetingEntity.isNotBlank()) {
                refreshMeetingPrep(_uiState.value.selectedMeetingEntity)
            }
        }
    }

    fun previewDetectedEntities(rawText: String): Pair<List<String>, List<String>> {
        if (rawText.isBlank()) return emptyList<String>() to emptyList()
        return skillRouter.extractEntitiesFromText(rawText, allEntities.value)
    }

    fun openSelfTestInspectorSheet(show: Boolean) {
        _uiState.update { it.copy(showSelfTestInspectorSheet = show) }
        if (show && _uiState.value.lastDiagnostics.lastRawModelOutput.isBlank()) {
            runLiveAiCoreSelfTest()
        }
    }

    fun runLiveAiCoreSelfTest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingAi = true) }
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val diag = skillRouter.runHardwareSelfTestProbe(
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            _uiState.update {
                it.copy(
                    isProcessingAi = false,
                    engineStatus = diag.status,
                    lastDiagnostics = diag
                )
            }
            emitSnackbar("AI Backend Probe (${diag.status.badgeText}) completed in ${diag.lastLatencyMs} ms")
        }
    }

    fun runMalformedNanoRepairStressTest() {
        val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
        val diag = skillRouter.runMalformedNanoRepairDemo(
            allEntities = allEntities.value,
            activeQ1Count = activeQ1,
            energyState = _uiState.value.currentBandwidth
        )
        _uiState.update { it.copy(lastDiagnostics = diag) }
        emitSnackbar("Schema Repair Verified: ${diag.repairNotes.size} repair rule(s) applied")
    }

    // --- Ask Rei for More Detail (Step 1 Single-Question Clarification Probe) ---
    fun openClarifyForQuickDump() {
        val raw = _uiState.value.quickDumpText
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingAi = true) }
            val gate = skillRouter.evaluateBrainDumpPreCheckGate(raw, allEntities.value)
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val (questions, diag) = skillRouter.generateClarifyingPrompts(
                rawTextOrTaskTitle = raw,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            _uiState.update {
                it.copy(
                    isProcessingAi = false,
                    showClarifySheet = true,
                    clarifyTargetTask = null,
                    clarifySourceText = raw,
                    clarifyingQuestions = questions,
                    activePreCheckGate = gate.takeIf { g -> g.gateType != BrainDumpGateType.NONE_PROCEED },
                    lastDiagnostics = diag.copy(status = it.engineStatus)
                )
            }
        }
    }

    fun openClarifyForExistingTask(task: ReiTask) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingAi = true, showRunPromptOnTaskSheet = false) }
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val (questions, diag) = skillRouter.generateClarifyingPrompts(
                rawTextOrTaskTitle = task.title,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            _uiState.update {
                it.copy(
                    isProcessingAi = false,
                    showClarifySheet = true,
                    clarifyTargetTask = task,
                    clarifySourceText = task.title,
                    clarifyingQuestions = questions,
                    activePreCheckGate = BrainDumpPreCheckGate(
                        gateType = BrainDumpGateType.STEP_1_VAGUE_OR_MASSIVE_CLARIFY,
                        flaggedItemText = task.title,
                        singleSpecificQuestion = questions.firstOrNull()?.question ?: "What is the actual next physical step?"
                    ),
                    lastDiagnostics = diag.copy(status = it.engineStatus)
                )
            }
        }
    }

    fun dismissClarifySheet() {
        _uiState.update {
            it.copy(
                showClarifySheet = false,
                clarifyTargetTask = null,
                activePreCheckGate = null
            )
        }
    }

    fun applyClarificationAndEnrich(clarifiedDetails: String) {
        val targetTask = _uiState.value.clarifyTargetTask
        val baseText = _uiState.value.clarifySourceText
        val gate = _uiState.value.activePreCheckGate
        _uiState.update {
            it.copy(
                showClarifySheet = false,
                clarifyTargetTask = null,
                activePreCheckGate = null
            )
        }

        if (gate?.gateType == BrainDumpGateType.STEP_6_FRUSTRATION_WALL_DETECTED ||
            gate?.gateType == BrainDumpGateType.STEP_6_LOW_ENERGY_OVERRIDE
        ) {
            val topOpen = targetTask ?: allTasks.value.firstOrNull { it.status == TaskStatus.ACTIVE }
            when {
                (clarifiedDetails.contains("Smaller Step", ignoreCase = true) ||
                    clarifiedDetails.contains("Shrink", ignoreCase = true)) && topOpen != null -> {
                    _uiState.update { it.copy(quickDumpText = "") }
                    shrinkTask(topOpen)
                    return
                }
                (clarifiedDetails.contains("Stuck", ignoreCase = true) ||
                    clarifiedDetails.contains("Defer", ignoreCase = true) ||
                    clarifiedDetails.contains("Breadcrumb", ignoreCase = true)) && topOpen != null -> {
                    _uiState.update { it.copy(quickDumpText = "", activeBreadcrumbTask = topOpen) }
                    return
                }
                clarifiedDetails.contains("Quick Win", ignoreCase = true) ||
                    clarifiedDetails.contains("Fried", ignoreCase = true) -> {
                    _uiState.update { it.copy(quickDumpText = "") }
                    selectBandwidth(EnergyBandwidthState.FRIED)
                    return
                }
            }
        }

        if (gate?.gateType == BrainDumpGateType.STEP_2_CALENDAR_NORTHWELL_REVIEW) {
            viewModelScope.launch {
                val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
                val cleanSpawn = clarifiedDetails
                    .replace("Spawn task:", "", ignoreCase = true)
                    .trim()
                if (cleanSpawn.isBlank() || cleanSpawn.startsWith("No upcoming device calendar")) {
                    return@launch
                }
                if (clarifiedDetails.contains("Tie ", ignoreCase = true) && allTasks.value.isNotEmpty()) {
                    val target = allTasks.value.firstOrNull { it.status != TaskStatus.DONE }
                    if (target != null) {
                        val updated = target.copy(
                            dueDateNote = "📅 Linked: ${cleanSpawn.take(40)} • ${target.dueDateNote}"
                        )
                        repository.insertOrUpdateTask(updated)
                        emitSnackbar("📅 Tied calendar meeting to '${target.title.take(28)}'")
                        return@launch
                    }
                }
                val result = skillRouter.organizeBrainDump(
                    rawText = cleanSpawn,
                    allEntities = allEntities.value,
                    activeQ1Count = activeQ1,
                    energyState = _uiState.value.currentBandwidth
                )
                repository.saveOrganizedCardAndTasks(result.card, result.extractedTasks)
                emitSnackbar("📅 Added ${result.extractedTasks.size} calendar-linked task(s) to Matrix")
            }
            return
        }

        if (targetTask != null) {
            viewModelScope.launch {
                _uiState.update { it.copy(isProcessingAi = true) }
                val combined = "${targetTask.title} -> Physical Step: $clarifiedDetails"
                val (newPeople, newTopics) = skillRouter.extractEntitiesFromText(combined, allEntities.value)
                val mergedPeople = (targetTask.peopleTags + newPeople).distinct()
                val mergedTopics = (targetTask.topicTags + newTopics).distinct()
                val enrichedDue = skillRouter.extractDueDateTag(clarifiedDetails)
                val enrichedQuad = when {
                    clarifiedDetails.contains("Q1", ignoreCase = true) ||
                        clarifiedDetails.contains("Urgent", ignoreCase = true) ||
                        clarifiedDetails.contains("Today", ignoreCase = true) -> TaskQuadrant.Q1_DO_NOW
                    clarifiedDetails.contains("Waiting On", ignoreCase = true) -> TaskQuadrant.Q3_BATCH_CONTAIN
                    else -> targetTask.quadrant
                }
                val newSteps = listOf(
                    MicroStep("clarified_1", "$clarifiedDetails (⏱️ Time to complete: 2 minutes)", isCompleted = false, estMinutes = 2),
                    MicroStep("clarified_2", "Complete core action for '${targetTask.title.take(36)}'", isCompleted = false, estMinutes = 2),
                    MicroStep("clarified_3", "Close the loop (send final message / save file)", isCompleted = false, estMinutes = 1)
                )
                val finalTask = targetTask.copy(
                    title = "${targetTask.title} → $clarifiedDetails".take(140),
                    peopleTags = mergedPeople,
                    topicTags = mergedTopics,
                    dueDateNote = enrichedDue,
                    quadrant = enrichedQuad,
                    microStepsJson = ReiJsonCodec.encodeMicroSteps(newSteps)
                )
                repository.insertOrUpdateTask(finalTask)
                _uiState.update {
                    it.copy(
                        isProcessingAi = false,
                        activeAdhdBriefing = AdhdExecutiveBriefing(
                            protocolHeadline = "✅ Step 1 Clarification Applied to Task",
                            topTaskTitle = finalTask.title,
                            topTaskMicroStep = newSteps.first().text,
                            timeEstimateLabel = "⏱️ Time to complete: 2 minutes",
                            matrixBullets = listOf(
                                "**Updated Task**: ${finalTask.title}",
                                "**Deadline**: $enrichedDue",
                                "**Final Step**: Close the loop"
                            )
                        )
                    )
                }
                emitSnackbar("Clarified physical step for '${targetTask.title.take(24)}'")
            }
        } else {
            val flagged = gate?.flaggedItemText?.takeIf { it.isNotBlank() }
            val updatedDump = if (flagged != null && baseText.contains(flagged, ignoreCase = true)) {
                baseText.replaceFirst(flagged, "$flagged -> Physical Step: $clarifiedDetails", ignoreCase = true)
            } else if (baseText.isBlank()) {
                clarifiedDetails
            } else {
                "$baseText -> Physical Step: $clarifiedDetails"
            }
            _uiState.update { it.copy(quickDumpText = updatedDump) }
            organizeCurrentQuickDump(bypassPreCheckGate = true)
        }
    }

    // --- Run Existing AI Prompts & ADHD Emergency Commands on Existing Tasks ---
    fun openRunPromptOnTaskSheet(task: ReiTask? = null) {
        _uiState.update {
            it.copy(
                showRunPromptOnTaskSheet = true,
                runPromptTargetTask = task ?: allTasks.value.firstOrNull()
            )
        }
    }

    fun dismissRunPromptOnTaskSheet() {
        _uiState.update { it.copy(showRunPromptOnTaskSheet = false, runPromptTargetTask = null) }
    }

    fun splitExistingTaskIntoMultipleTasks(task: ReiTask) {
        _uiState.update { it.copy(showRunPromptOnTaskSheet = false) }
        viewModelScope.launch {
            val linkedCard = allCards.value.find { it.id == task.linkedCardId }
            val rawCandidates = buildList {
                addAll(skillRouter.splitBrainDumpIntoDistinctItems(task.title))
                if (linkedCard != null) {
                    linkedCard.outlineSections.forEach { sec ->
                        sec.bullets.forEach { b ->
                            if (!b.startsWith("**Top Task**") && !b.startsWith("**First Micro-Step**") && !b.startsWith("**⏱️")) {
                                add(b)
                            }
                        }
                    }
                }
            }.map { it.trim() }.filter { it.length > 3 }.distinct()

            val itemsToCreate = if (rawCandidates.size > 1) {
                rawCandidates
            } else {
                task.microSteps.map { it.text }.filter { it.isNotBlank() }
            }

            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val combinedText = itemsToCreate.joinToString("\n")
            val result = skillRouter.organizeBrainDump(
                rawText = combinedText,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            repository.deleteTask(task.id)
            result.extractedTasks.forEach { newTask ->
                repository.insertOrUpdateTask(newTask.copy(linkedCardId = task.linkedCardId))
            }
            _uiState.update {
                it.copy(
                    currentTab = ReiNavTab.REI_COMMAND,
                    activeAdhdBriefing = result.briefing.copy(
                        protocolHeadline = "🔀 Split into ${result.extractedTasks.size} Separate Matrix Tasks"
                    )
                )
            }
            emitSnackbar("Split into ${result.extractedTasks.size} individual Matrix tasks")
        }
    }

    fun extractCardBulletsIntoMatrixTasks(card: KnowledgeCard) {
        viewModelScope.launch {
            val rawBullets = card.outlineSections
                .flatMap { it.bullets }
                .map { it.trim() }
                .filter {
                    it.length > 3 &&
                        !it.startsWith("**Top Task**") &&
                        !it.startsWith("**First Micro-Step**") &&
                        !it.startsWith("**⏱️")
                }
                .ifEmpty {
                    skillRouter.splitBrainDumpIntoDistinctItems(card.rawSourceText.ifBlank { card.tldrSummary })
                }
                .distinct()

            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val result = skillRouter.organizeBrainDump(
                rawText = rawBullets.joinToString("\n"),
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            result.extractedTasks.forEach { newTask ->
                repository.insertOrUpdateTask(newTask.copy(linkedCardId = card.id))
            }
            _uiState.update {
                it.copy(
                    currentTab = ReiNavTab.REI_COMMAND,
                    commandViewMode = ReiCommandViewMode.ACTION_DECK,
                    activeAdhdBriefing = result.briefing.copy(
                        protocolHeadline = "🔀 Extracted ${result.extractedTasks.size} Matrix Tasks from '${card.title.take(26)}'"
                    )
                )
            }
            emitSnackbar("Extracted ${result.extractedTasks.size} individual tasks into Rei Command Matrix")
        }
    }

    fun tagWaitingOnPersonForTask(task: ReiTask, personName: String) {
        val cleanPerson = personName.removePrefix("@").trim().ifBlank { "Stakeholder" }
        _uiState.update { it.copy(showRunPromptOnTaskSheet = false) }
        viewModelScope.launch {
            val updatedTitle = if (task.title.contains("⏳ Waiting On")) {
                task.title
            } else {
                "⏳ Waiting On: @$cleanPerson — ${task.title}"
            }
            val updatedTask = task.copy(
                title = updatedTitle,
                quadrant = TaskQuadrant.Q3_BATCH_CONTAIN,
                dueDateNote = "⏳ Waiting On: @$cleanPerson • ${task.dueDateNote}",
                peopleTags = (task.peopleTags + cleanPerson).distinct()
            )
            repository.insertOrUpdateTask(updatedTask)

            val followUpSteps = listOf(
                MicroStep("wf_1", "Open chat/email to @$cleanPerson & send 1-line check-in on '${task.title.take(36)}' (⏱️ Time to complete: 1 minute)", false, 1),
                MicroStep("wf_2", "Log @$cleanPerson's answer or updated delivery date", false, 1),
                MicroStep("wf_3", "Close the loop (send final message / save file)", false, 1)
            )
            val followUpTask = ReiTask(
                title = "🍞 Follow Up w/ @$cleanPerson: ${task.title.take(60)}",
                quadrant = TaskQuadrant.Q3_BATCH_CONTAIN,
                energyLoad = EnergyLoad.LOW_DRAIN,
                status = TaskStatus.PAUSED_BREADCRUMB,
                microStepsJson = ReiJsonCodec.encodeMicroSteps(followUpSteps),
                peopleTags = listOf(cleanPerson),
                topicTags = task.topicTags.ifEmpty { listOf("Follow-Up") },
                dueDateNote = "⏳ Waiting On: @$cleanPerson • Deferred Follow-Up",
                breadcrumbBlocker = "⏳ Waiting On: @$cleanPerson",
                breadcrumbResumeStep = followUpSteps.first().text
            )
            repository.insertOrUpdateTask(followUpTask)

            _uiState.update {
                it.copy(
                    activeAdhdBriefing = AdhdExecutiveBriefing(
                        protocolHeadline = "⏳ Tagged 'Waiting On: @$cleanPerson' + Auto Follow-Up Created",
                        topTaskTitle = followUpTask.title,
                        topTaskMicroStep = followUpSteps.first().text,
                        timeEstimateLabel = "⏱️ Time to complete: 1 minute",
                        matrixBullets = listOf(
                            "**Blocked Task**: $updatedTitle",
                            "**Auto-Created Deferred Task**: ${followUpTask.title}",
                            "**Final Step Locked**: Close the loop (send final message / save file)"
                        ),
                        waitingOnAutoFollowUps = listOf("${followUpTask.title} → Final Step: **Close the loop**")
                    )
                )
            }
            emitSnackbar("⏳ Tagged Waiting On @$cleanPerson + created 'Close the loop' follow-up")
        }
    }

    fun snoozeTask(task: ReiTask, untilWhen: String) {
        _uiState.update { it.copy(showRunPromptOnTaskSheet = false) }
        viewModelScope.launch {
            val cleanTitle = task.title.replace(Regex("^[🅿️⏰]+\\s*"), "")
            val snoozed = task.copy(
                title = cleanTitle,
                quadrant = TaskQuadrant.Q4_PARKED_LOT,
                status = TaskStatus.PAUSED_BREADCRUMB,
                dueDateNote = "⏰ Snoozed: $untilWhen",
                breadcrumbBlocker = "Snoozed until $untilWhen",
                breadcrumbResumeStep = task.microSteps.firstOrNull { !it.isCompleted }?.text ?: "Resume task"
            )
            repository.insertOrUpdateTask(snoozed)
            emitSnackbar("⏰ Snoozed '${cleanTitle.take(26)}' until $untilWhen — pulled off active list")
        }
    }

    fun triggerRabbitHoleStop(task: ReiTask) {
        _uiState.update { it.copy(showRunPromptOnTaskSheet = false) }
        viewModelScope.launch {
            val steps = task.microSteps
            val currentIdx = steps.indexOfFirst { !it.isCompleted }
            val updatedSteps = if (currentIdx >= 0) {
                steps.mapIndexed { idx, step ->
                    if (idx == currentIdx) {
                        step.copy(
                            text = "${step.text} (🛑 Good enough — stop perfecting)",
                            isCompleted = true
                        )
                    } else {
                        step
                    }
                }
            } else {
                steps.map { it.copy(isCompleted = true) }
            }
            val updatedTask = task.copy(
                microStepsJson = ReiJsonCodec.encodeMicroSteps(updatedSteps),
                dueDateNote = "🛑 Good Enough • ${task.dueDateNote}"
            )
            repository.insertOrUpdateTask(updatedTask)
            val nextOpen = updatedSteps.firstOrNull { !it.isCompleted }
            if (nextOpen != null) {
                emitSnackbar("🛑 Good enough — stop perfecting! Checked step & advanced to: ${nextOpen.text.take(36)}")
            } else {
                _uiState.update { it.copy(activeVerify99Task = updatedTask) }
                emitSnackbar("🛑 Good enough — stop perfecting! Ready to Verify & Close.")
            }
        }
    }

    fun reOrganizeExistingTaskIntoSecondBrain(task: ReiTask) {
        _uiState.update { it.copy(showRunPromptOnTaskSheet = false) }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingAi = true) }
            val activeQ1 = allTasks.value.count { it.quadrant == TaskQuadrant.Q1_DO_NOW && it.status != TaskStatus.DONE }
            val fullTaskText = buildString {
                append(task.title)
                if (task.peopleTags.isNotEmpty()) append(" ${task.peopleTags.joinToString(" ") { "@$it" }}")
                if (task.topicTags.isNotEmpty()) append(" ${task.topicTags.joinToString(" ") { "#$it" }}")
                if (task.breadcrumbBlocker.isNotBlank()) append(". Blocker: ${task.breadcrumbBlocker}")
            }
            val result = skillRouter.organizeBrainDump(
                rawText = fullTaskText,
                allEntities = allEntities.value,
                activeQ1Count = activeQ1,
                energyState = _uiState.value.currentBandwidth
            )
            val savedCardId = repository.saveOrganizedCardAndTasks(result.card, emptyList())
            repository.insertOrUpdateTask(task.copy(linkedCardId = savedCardId))
            _uiState.update {
                it.copy(
                    isProcessingAi = false,
                    lastOrganizedBannerTitle = result.card.title,
                    activeAdhdBriefing = result.briefing.copy(
                        protocolHeadline = "✨ Created Second Brain Brief & Executive Breakdown"
                    ),
                    lastDiagnostics = result.diagnostics.copy(status = it.engineStatus)
                )
            }
            emitSnackbar("Created Second Brain Brief '${result.card.title.take(28)}' from task")
        }
    }

    fun prepMeetingFromExistingTask(task: ReiTask) {
        val targetEntity = task.peopleTags.firstOrNull()
            ?: task.topicTags.firstOrNull()
            ?: allEntities.value.firstOrNull()?.entityName
            ?: "General"
        _uiState.update {
            it.copy(
                showRunPromptOnTaskSheet = false,
                currentTab = ReiNavTab.MEETING_PREP,
                selectedMeetingEntity = targetEntity
            )
        }
        refreshMeetingPrep(targetEntity)
        emitSnackbar("Synthesized Meeting Prep Brief for $targetEntity")
    }

    fun runCommsLensOnExistingTask(task: ReiTask, lens: CommsStrategyLens) {
        val draftPrompt = buildString {
            append(task.title)
            if (task.breadcrumbBlocker.isNotBlank()) {
                append(". Current blocker: ${task.breadcrumbBlocker}.")
            }
            if (task.peopleTags.isNotEmpty()) {
                append(" Stakeholders: ${task.peopleTags.joinToString(", ") { "@$it" }}.")
            }
        }
        _uiState.update {
            it.copy(
                showRunPromptOnTaskSheet = false,
                currentTab = ReiNavTab.COMMS_STRATEGY,
                selectedCommsLens = lens,
                commsInputText = draftPrompt
            )
        }
        runCommsStudio(customInput = draftPrompt)
        emitSnackbar("📝 Draft It: Generated rough first draft for '${task.title.take(24)}'")
    }

    fun computeWhereWasIState(tasks: List<ReiTask>): WhereWasIBannerState {
        return skillRouter.buildWhereWasIContextReload(tasks)
    }

    fun computeHeroTask(tasks: List<ReiTask>, bandwidth: EnergyBandwidthState): Pair<ReiTask?, String> {
        return skillRouter.selectHeroTaskForBandwidth(tasks, bandwidth)
    }

    fun emitSnackbar(message: String) {
        _snackbarMessages.tryEmit(message)
    }

    override fun onCleared() {
        super.onCleared()
        aiCoreEngine.close()
    }
}
