package com.example.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.local.CardCategory
import com.example.data.local.CommsStrategyLens
import com.example.data.local.EnergyBandwidthState
import com.example.data.local.EnergyLoad
import com.example.data.local.EntityContextWeight
import com.example.data.local.EntityType
import com.example.data.local.KnowledgeCard
import com.example.data.local.MeetingPrepSqlBundle
import com.example.data.local.MicroStep
import com.example.data.local.OutlineSection
import com.example.data.local.ReiJsonCodec
import com.example.data.local.ReiTask
import com.example.data.local.TaskQuadrant
import com.example.data.local.TaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured output of the 6-Step Inattentive ADHD Executive Assistant Protocol.
 * Displayed immediately after organizing a Brain Dump or running an ADHD Protocol on an existing task.
 */
data class AdhdExecutiveBriefing(
    val protocolHeadline: String,
    val topTaskTitle: String,
    val topTaskMicroStep: String,
    val timeEstimateLabel: String,
    val matrixBullets: List<String>,
    val waitingOnAutoFollowUps: List<String> = emptyList(),
    val roughDraftPreview: String? = null,
    val emergencyNotice: String? = null
)

/**
 * Step 1 / Step 2 / Step 6 Gate Check Result before organizing a Brain Dump.
 */
enum class BrainDumpGateType {
    NONE_PROCEED,
    STEP_1_VAGUE_OR_MASSIVE_CLARIFY,
    STEP_2_CALENDAR_NORTHWELL_REVIEW,
    STEP_6_FRUSTRATION_WALL_DETECTED,
    STEP_6_LOW_ENERGY_OVERRIDE
}

data class NorthwellMeetingCandidate(
    val id: String,
    val timeSlot: String,
    val title: String,
    val stakeholders: List<String>,
    val suggestedTaskConnection: String
)

data class BrainDumpPreCheckGate(
    val gateType: BrainDumpGateType,
    val flaggedItemText: String = "",
    val singleSpecificQuestion: String = "",
    val quickAnswerChips: List<String> = emptyList(),
    val missingDeadlineQuestion: String? = null,
    val calendarMeetings: List<NorthwellMeetingCandidate> = emptyList()
)

data class OrganizedBrainDumpResult(
    val card: KnowledgeCard,
    val extractedTasks: List<ReiTask>,
    val briefing: AdhdExecutiveBriefing,
    val contextHeaderUsed: String,
    val diagnostics: AiEngineDiagnostics
)

data class WhereWasIBannerState(
    val lastActiveTask: ReiTask?,
    val pausedBreadcrumbs: List<ReiTask>,
    val recentParkedLot: List<ReiTask>,
    val thirtySecondResumePrompt: String
)

data class MeetingGlanceSynthesis(
    val entityLabel: String,
    val roleSubtitle: String,
    val thirtySecondGoal: String,
    val yourMoveAction: String,
    val watchOutTrap: String,
    val tldrMeetingBrief: String,
    val checklistItems: List<String>,
    val sqlBundle: MeetingPrepSqlBundle,
    val contextHeaderUsed: String,
    val diagnostics: AiEngineDiagnostics
)

data class CommsStrategyOutput(
    val lens: CommsStrategyLens,
    val detectedReflexesOrAudit: List<String>,
    val primaryHeading: String,
    val primaryBody: String,
    val secondaryHeading: String,
    val secondaryBody: String,
    val ruleOfThumbOrStopDoing: String,
    val laneBreakdown: List<Pair<String, String>> = emptyList(),
    val contextHeaderUsed: String,
    val diagnostics: AiEngineDiagnostics
)

data class ClarifyingQuestionItem(
    val id: String,
    val dimensionLabel: String,
    val question: String,
    val quickChips: List<String>
)

/**
 * Implements the 6-Step Inattentive ADHD Executive Assistant System Prompt & Skill Router.
 * Routes all skills through `OnDeviceAiEngine` (AICore Gemini Nano + deterministic local fallback).
 */
class LocalSkillRouter(
    private val aiEngine: OnDeviceAiEngine
) {
    var forceSimMode: Boolean = false

    companion object {
        const val ADHD_EXECUTIVE_SYSTEM_PROMPT = """
You are an executive assistant for a user with Inattentive ADHD. Your goal is to bypass executive dysfunction by providing structure, clarity, and zero friction.
Follow these exact steps in order:
1. The Clarification Check: Scan for vague tasks (e.g., "the car", "work stuff") or massive projects (e.g., "do taxes"). If any exist, STOP. Ask ONE specific, short question to find the actual next physical step. Do not proceed to step 2 until clarified.
2. The Calendar Review (Conditional): If the user asks to check their calendar or upcoming meetings, scan Northwell meetings, present a clean chronological list, and ask ONE question: "Do any of these need to be tied to existing tasks or generate new ones?" STOP until reviewed.
3. Due Dates, Blockers, & The Matrix:
 - Extract deadlines and tag as 📅 Due: [Date].
 - If blocked by someone else, tag as ⏳ Waiting On: [Person], auto-create a deferred follow-up task, and set final step to "Close the loop (send the final email/chat)".
 - Organize actionable tasks into an Eisenhower Matrix (🔴 Urgent Q1, 🟢 Quick win / Q2, ⏳ Waiting / Q3, 🅿️ Parked Q4). Every task in a list MUST become its own individual task.
4. The Top Task & Micro-Step: Identify the single most important task to start right now and break it into a ridiculously small, frictionless first physical micro-step.
5. The Time Estimate: Provide a realistic time estimate (e.g., "⏱️ Time to complete: 2 minutes").
6. Emergency Commands: Energy Override, Breadcrumb Deferral ("What specifically isn't working or why are you stuck?"), Proactive Frustration Detection, Park It, Shrink It, Where Was I?, Remind Me / Snooze, Draft It, Rabbit Hole ("Good enough"), and 99% Check ("Did you physically hit send, save the file, or close the tab?").
FORMATTING RULES: Never use large blocks of text. Use bolding for key terms. Use emojis as visual anchors (🔴 Urgent, 🟢 Quick win, 📅 Due Date, ⏳ Waiting On, 🅿️ Parked, 🍞 Breadcrumb). Be concise. No polite fluff.
"""
    }

    /**
     * Builds a compact DynamicContextHeader (< 180 tokens) from matched EntityContextWeight rows,
     * active Q1_DO_NOW count, and current EnergyBandwidthState.
     */
    fun buildDynamicContextHeader(
        inputText: String,
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState
    ): String {
        val lower = inputText.lowercase()
        val matched = allEntities.filter { e ->
            lower.contains(e.entityName.lowercase())
        }.ifEmpty {
            allEntities.take(4)
        }.take(5)

        val entitySummary = matched.joinToString("; ") { e ->
            val q = e.learnedQuadrantBias?.shortCode ?: "Q2"
            val load = when (e.learnedEnergyBias) {
                EnergyLoad.DEEP_WORK -> "Deep"
                EnergyLoad.LOW_DRAIN -> "Low"
                else -> "Steady"
            }
            "${e.entityType.prefix}${e.entityName}(${e.roleOrContext.substringBefore("•").trim()},Bias=$q/$load)"
        }

        return "[REI_ADHD_CTX|<180tok] Bandwidth=${energyState.label}(${energyState.targetLoad.name}) | ActiveQ1=$activeQ1Count | VIPs: $entitySummary"
    }

    /**
     * Extract `@People` and `#Topics` from raw text + known VIP entity table.
     */
    fun extractEntitiesFromText(
        rawText: String,
        knownEntities: List<EntityContextWeight>
    ): Pair<List<String>, List<String>> {
        val people = mutableSetOf<String>()
        val topics = mutableSetOf<String>()
        val lower = rawText.lowercase()

        for (entity in knownEntities) {
            if (lower.contains(entity.entityName.lowercase())) {
                if (entity.entityType == EntityType.PERSON) {
                    people.add(entity.entityName)
                } else {
                    topics.add(entity.entityName)
                }
            }
        }

        Regex("@([A-Za-z][A-Za-z0-9_-]{1,22})").findAll(rawText).forEach { match ->
            val name = match.groupValues[1].replaceFirstChar { it.uppercase() }
            people.add(name)
        }

        Regex("#([A-Za-z][A-Za-z0-9_-]{1,26})").findAll(rawText).forEach { match ->
            val tag = match.groupValues[1].replace("_", " ").replace("-", " ")
            topics.add(tag)
        }

        // Detect "waiting on [Name]" or "with [Name]" or "ask [Name]" or "email [Name]"
        Regex("(?:waiting on|waiting for|blocked by|ask|email|ping|call|text|tell)\\s+@?([A-Z][a-z]{2,18})").findAll(rawText).forEach { match ->
            val candidate = match.groupValues[1]
            if (candidate.lowercase() !in setOf("the", "this", "that", "them", "today", "tomorrow", "monday", "tuesday", "wednesday", "thursday", "friday", "northwell", "epic", "teams")) {
                people.add(candidate)
            }
        }

        if (lower.contains("sbo") || lower.contains("epic") || lower.contains("statement")) {
            topics.add("Epic SBO")
        }
        if (lower.contains("resolution room") || lower.contains("swarm") || lower.contains("escalat")) {
            topics.add("Resolution Room")
        }
        if (lower.contains("teams") || lower.contains("broadcast") || lower.contains("channel")) {
            topics.add("Teams Broadcast")
        }
        if (lower.contains("ar ") || lower.contains("aging") || lower.contains("cash") || lower.contains("collection")) {
            topics.add("AR Goals")
        }
        if (lower.contains("northwell") || lower.contains("calendar") || lower.contains("meeting")) {
            topics.add("Northwell Calendar")
        }

        return people.toList() to topics.toList()
    }

    /**
     * Splits a multi-item Brain Dump (newlines, bullets, numbered lists, semicolons, or comma-separated tasks)
     * into distinct task strings so a task list is NEVER collapsed into 1 task's bullets.
     */
    fun splitBrainDumpIntoDistinctItems(rawText: String): List<String> {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) return emptyList()

        // First split on explicit line breaks, semicolons, or bullet characters
        val primaryLines = cleaned
            .split(Regex("\\r?\\n|\\s*;\\s*|\\s+[•▪▸]\\s+"))
            .map { line ->
                line.trim()
                    .replace(Regex("^([-*•▪▸]|\\d+[.)]|\\(\\d+\\)|\\[[ xX]?\\])\\s+"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }

        // If the user typed multiple lines/bullets, each line is its own distinct task item
        if (primaryLines.size > 1) {
            val expanded = mutableListOf<String>()
            for (line in primaryLines) {
                // Check if a single line also contains multiple sentences separated by ". "
                val subSentences = line.split(Regex("(?<=[.!?])\\s+(?=[A-Z@🔴🟢📅⏳🅿️🍞])"))
                    .map { it.trim().trimEnd('.') }
                    .filter { it.length > 2 }
                expanded.addAll(subSentences)
            }
            return expanded.distinct()
        }

        // Single-line input: split on sentence boundaries, " and also ", " then ", or comma-separated task lists
        val single = primaryLines.first()
        val bySentenceOrConjunction = single
            .split(Regex("(?<=[.!?])\\s+|\\s+(?:and also|also need to|plus|then)\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotBlank() }

        if (bySentenceOrConjunction.size > 1) {
            return bySentenceOrConjunction
        }

        // Check if it's a comma-separated list of tasks (e.g., "fix the car, do taxes, email Corrie, call Wayne")
        val commaParts = single.split(Regex("\\s*,\\s*(?:and\\s+)?"))
            .map { it.trim().trimEnd('.') }
            .filter { it.length >= 3 }

        if (commaParts.size >= 2) {
            return commaParts
        }

        return listOf(single.trimEnd('.'))
    }

    /**
     * STEP 1, STEP 2, & STEP 6 PRE-CHECK GATE:
     * Executes before Step 3 (Matrix & Second Brain organization):
     * - Step 6: Proactive Frustration / Wall Detection ("I just can't", "this is annoying", "I'm stuck", "not working")
     * - Step 6: Energy Override ("low energy", "fried")
     * - Step 2: Conditional Calendar Review ("calendar", "upcoming meetings", "northwell")
     * - Step 1: Clarification Check for vague tasks ("the car", "work stuff") or massive projects ("do taxes")
     */
    fun evaluateBrainDumpPreCheckGate(
        rawText: String,
        allEntities: List<EntityContextWeight>
    ): BrainDumpPreCheckGate {
        val clean = rawText.trim()
        val lower = clean.lowercase()

        // 1. Step 6: Proactive Frustration / Wall Detection
        val frustrationTriggers = listOf(
            "i just can't", "i cant", "can't", "this is annoying", "so annoying", "annoying", "hitting a wall",
            "i'm stuck", "im stuck", "stuck", "it's not working", "its not working", "overwhelmed",
            "too much", "hate this", "giving up", "paralyzed"
        )
        if (frustrationTriggers.any { lower.contains(it) }) {
            return BrainDumpPreCheckGate(
                gateType = BrainDumpGateType.STEP_6_FRUSTRATION_WALL_DETECTED,
                flaggedItemText = clean,
                singleSpecificQuestion = "Hitting a wall on this? Tap **Smaller Step** to shrink it, or **I'm Stuck** to leave a breadcrumb and switch tasks.",
                quickAnswerChips = listOf(
                    "🤏 Smaller Step (<60s physical action)",
                    "🍞 I'm Stuck (Leave Breadcrumb & Switch Tasks)",
                    "🟢 Switch to Quick Win Mode"
                )
            )
        }

        // 2. Step 6: Energy Override ("low energy", "fried", "quick win")
        val lowEnergyTriggers = listOf("low energy", "i'm fried", "im fried", "brain is fried", "exhausted", "no energy", "zero spoons")
        if (lowEnergyTriggers.any { lower.contains(it) } && splitBrainDumpIntoDistinctItems(clean).size <= 1) {
            return BrainDumpPreCheckGate(
                gateType = BrainDumpGateType.STEP_6_LOW_ENERGY_OVERRIDE,
                flaggedItemText = clean,
                singleSpecificQuestion = "🟢 **Quick Win Override**: Bypassing the Eisenhower Matrix. Ready to surface the single easiest, lowest-friction task (⏱️ 1-2 min) just to build momentum?",
                quickAnswerChips = listOf(
                    "🟢 Yes — Surface Easiest Quick Win Now",
                    "🅿️ Stash Current Thought & Rest"
                )
            )
        }

        // 3. Step 2: Conditional Calendar Review (Northwell / Samsung / Google Calendar)
        val calendarTriggers = listOf(
            "check my calendar", "check calendar", "upcoming meetings", "review meetings",
            "northwell calendar", "today's meetings", "todays meetings", "my schedule", "scan calendar"
        )
        if (calendarTriggers.any { lower.contains(it) }) {
            val meetings = buildNorthwellCalendarMeetings(allEntities)
            return BrainDumpPreCheckGate(
                gateType = BrainDumpGateType.STEP_2_CALENDAR_NORTHWELL_REVIEW,
                flaggedItemText = "Northwell / Connected Calendar Scan",
                singleSpecificQuestion = "Do any of these need to be tied to existing tasks or generate new ones?",
                quickAnswerChips = listOf(
                    "Spawn new prep tasks for all 3 meetings",
                    "Tie 10:30 AM Epic SBO Huddle to @Corrie Q1 task",
                    "Tie 1:00 PM AR Ring-Fence Sync to @Diana & @Debbie",
                    "Tie 3:30 PM Resolution Room Gate to @Lauren"
                ),
                calendarMeetings = meetings
            )
        }

        // 4. Step 1: The Clarification Check (Scan for vague tasks or massive projects)
        val items = splitBrainDumpIntoDistinctItems(clean)
        val vagueOrMassiveItem = items.firstOrNull { item ->
            isVagueOrMassiveTask(item)
        }

        if (vagueOrMassiveItem != null) {
            val (question, chips) = buildSinglePhysicalStepQuestion(vagueOrMassiveItem, allEntities)
            val needsDeadlineAsk = !hasExplicitTimeOrDate(vagueOrMassiveItem)
            return BrainDumpPreCheckGate(
                gateType = BrainDumpGateType.STEP_1_VAGUE_OR_MASSIVE_CLARIFY,
                flaggedItemText = vagueOrMassiveItem,
                singleSpecificQuestion = question,
                quickAnswerChips = chips,
                missingDeadlineQuestion = if (needsDeadlineAsk) "📅 Implied deadline missing — when is this due?" else null
            )
        }

        return BrainDumpPreCheckGate(gateType = BrainDumpGateType.NONE_PROCEED)
    }

    /**
     * Detects whether a task string is vague (e.g. "work stuff", "the report", "follow up", "the car")
     * or a massive project (e.g. "do taxes", "q4 budget", "epic migration") that has not yet been clarified.
     */
    fun isVagueOrMassiveTask(item: String): Boolean {
        val clean = item.trim()
        if (clean.contains("->") || clean.contains("Physical Step:", ignoreCase = true) || clean.contains("[Clarified:", ignoreCase = true)) {
            return false
        }

        val lower = clean.lowercase()
        val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }

        val exactOrSubstringVagueMassive = listOf(
            "the car", "car stuff", "fix the car", "work stuff", "the report", "report",
            "follow up", "follow-up", "do taxes", "taxes", "the presentation", "presentation",
            "house stuff", "clean the house", "clean house", "paperwork", "admin stuff",
            "budget", "q4 budget", "annual review", "onboarding", "reorganize",
            "everything", "catch up", "emails", "inbox", "epic project", "migration", "project"
        )
        if (exactOrSubstringVagueMassive.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {
            return true
        }

        val concreteVerbs = setOf(
            "email", "send", "call", "text", "ping", "draft", "reply", "approve", "sign",
            "export", "download", "upload", "print", "book", "schedule", "cancel", "pay",
            "buy", "submit", "confirm", "verify", "lock", "ring-fence", "freeze", "check",
            "open", "write", "paste", "share", "review", "update", "ask", "waiting"
        )
        val hasConcreteVerb = words.any { it in concreteVerbs }

        if (words.size <= 2 && !hasConcreteVerb) {
            return true
        }
        if (words.size <= 3 && lower in setOf("call them", "fix it", "do it", "handle that", "follow up", "check on it", "the report")) {
            return true
        }
        return false
    }

    /**
     * Builds ONE specific, short question to find the actual next physical step for a vague or massive task:
     * "This looks broad. What is the single next physical action to start it?"
     */
    fun buildSinglePhysicalStepQuestion(
        itemText: String,
        allEntities: List<EntityContextWeight>
    ): Pair<String, List<String>> {
        val clean = itemText.trim()
        val lower = clean.lowercase()
        val coreQuestion = "This looks broad. What is the single next physical action to start it?"

        val chips = when {
            lower.contains("car") -> listOf(
                "Call the mechanic to book an appointment",
                "Walk out to the car & check the dashboard/tire",
                "Open browser to renew registration / insurance",
                "Order replacement part online"
            )
            lower.contains("tax") -> listOf(
                "Create a '2026 Taxes' folder & drop in W-2 PDF",
                "Email accountant to ask for the intake checklist",
                "Log into tax portal & reset password",
                "Snap photo of 1 receipt on my desk"
            )
            lower.contains("report") || lower.contains("budget") || lower.contains("presentation") -> listOf(
                "Open the file and type ONLY the title & 3 bullet headers",
                "Export the raw Q4 numbers CSV from Epic SBO",
                "Ping @Wayne for the missing variance table",
                "Draft 2-sentence executive summary for @Corrie"
            )
            lower.contains("follow up") || lower.contains("work stuff") || lower.contains("email") || lower.contains("inbox") -> {
                val topVip = allEntities.firstOrNull { it.entityType == EntityType.PERSON }?.entityName ?: "Corrie"
                listOf(
                    "Send a 1-line check-in ping to @$topVip",
                    "Reply to the top urgent unread thread (2 sentences)",
                    "Send 1-line status update on #Epic SBO",
                    "⏳ Tag as Waiting On @$topVip"
                )
            }
            else -> listOf(
                "Send a 2-line email/Teams ping to confirm next step",
                "Open the doc/spreadsheet and write 3 rough bullets",
                "Make a 2-minute phone call to get the answer",
                "⏳ Tag as Waiting On someone"
            )
        }
        return coreQuestion to chips
    }

    /**
     * Reads upcoming meetings from Android `CalendarContract.Events` when `READ_CALENDAR` permission is granted,
     * falling back to (or enriching with) realistic Northwell sample meetings in Preview Sim Mode.
     */
    fun readDeviceOrNorthwellCalendarMeetings(
        context: Context?,
        allEntities: List<EntityContextWeight>
    ): List<NorthwellMeetingCandidate> {
        val deviceMeetings = mutableListOf<NorthwellMeetingCandidate>()
        if (context != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                val now = System.currentTimeMillis()
                val next48h = now + 48L * 60L * 60L * 1000L
                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.DESCRIPTION
                )
                val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} != 1"
                val selectionArgs = arrayOf(now.toString(), next48h.toString())
                val sortOrder = "${CalendarContract.Events.DTSTART} ASC"
                val fmt = SimpleDateFormat("h:mm a", Locale.US)

                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                    val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
                    val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                    while (cursor.moveToNext() && deviceMeetings.size < 5) {
                        val eventId = if (idIdx >= 0) cursor.getLong(idIdx).toString() else "cal_${deviceMeetings.size}"
                        val rawTitle = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else ""
                        if (rawTitle.isNotBlank()) {
                            val startMs = if (startIdx >= 0) cursor.getLong(startIdx) else now
                            val endMs = if (endIdx >= 0) cursor.getLong(endIdx) else (startMs + 1800_000L)
                            val slot = "${fmt.format(Date(startMs))} – ${fmt.format(Date(endMs))}"
                            val (people, topics) = extractEntitiesFromText(rawTitle, allEntities)
                            val tags = (people.map { "@$it" } + topics.map { "#$it" }).ifEmpty { listOf("@Corrie", "#Northwell") }
                            deviceMeetings.add(
                                NorthwellMeetingCandidate(
                                    id = "dev_$eventId",
                                    timeSlot = slot,
                                    title = rawTitle,
                                    stakeholders = tags,
                                    suggestedTaskConnection = "Prep 2-bullet decision check for '$rawTitle' (${tags.joinToString(" ")})"
                                )
                            )
                        }
                    }
                }
            }
        }
        return if (deviceMeetings.isNotEmpty()) {
            deviceMeetings
        } else {
            buildNorthwellCalendarMeetings(allEntities)
        }
    }

    fun buildNorthwellCalendarMeetings(allEntities: List<EntityContextWeight>): List<NorthwellMeetingCandidate> {
        val corrie = allEntities.find { it.entityName.equals("Corrie", true) }?.entityName ?: "Corrie"
        val debbie = allEntities.find { it.entityName.equals("Debbie", true) }?.entityName ?: "Debbie"
        val wayne = allEntities.find { it.entityName.equals("Wayne", true) }?.entityName ?: "Wayne"
        val diana = allEntities.find { it.entityName.equals("Diana", true) }?.entityName ?: "Diana"

        return listOf(
            NorthwellMeetingCandidate(
                id = "nw_1",
                timeSlot = "10:30 AM – 11:00 AM",
                title = "Northwell Epic SBO Wave-2 Cutover & Guarantor Hold Huddle",
                stakeholders = listOf("@$corrie", "@$wayne"),
                suggestedTaskConnection = "Lock 48h legacy guarantor statement freeze before Wave-2 cutover (#Epic SBO)"
            ),
            NorthwellMeetingCandidate(
                id = "nw_2",
                timeSlot = "1:00 PM – 1:30 PM",
                title = "Northwell Revenue Cycle: AR Collector Ring-Fence & SLA Review",
                stakeholders = listOf("@$debbie", "@$diana"),
                suggestedTaskConnection = "Ring-fence @$diana's 8 specialized AR collectors from 8:00 AM–1:00 PM (#AR Goals)"
            ),
            NorthwellMeetingCandidate(
                id = "nw_3",
                timeSlot = "3:30 PM – 4:00 PM",
                title = "Resolution Room Escalation Intake Gate Check-In",
                stakeholders = listOf("@Lauren", "@$debbie"),
                suggestedTaskConnection = "Enforce 3-field mandatory escalation intake gate in #Teams Broadcast"
            )
        )
    }

    private fun hasExplicitTimeOrDate(text: String): Boolean {
        val lower = text.lowercase()
        val dateTokens = listOf(
            "today", "tomorrow", "tonight", "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday", "eod", "cob", "am", "pm", "next week",
            "this week", "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec", "due:"
        )
        return dateTokens.any { lower.contains(it) } || Regex("\\b\\d{1,2}(:\\d{2})?\\s*(am|pm)?\\b").containsMatchIn(lower)
    }

    // =========================================================================
    // SKILL 1: Brain Dump & Multi-Task Eisenhower Matrix Auto-Organizer
    // =========================================================================
    suspend fun organizeBrainDump(
        rawText: String,
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState,
        capThreePerQuadrant: Boolean = false
    ): OrganizedBrainDumpResult {
        val cleanInput = rawText.trim()
        val header = buildDynamicContextHeader(cleanInput, allEntities, activeQ1Count, energyState)
        val (people, topics) = extractEntitiesFromText(cleanInput, allEntities)

        val prompt = """
            $ADHD_EXECUTIVE_SYSTEM_PROMPT
            SKILL_1_AUTO_ORGANIZER: Parse EVERY distinct task from this brain dump into its own task object in `tasks`.
            NEVER combine multiple tasks into bullets of 1 task.
            Extract deadlines as "📅 Due: [Date]".
            If blocked by someone, tag as "⏳ Waiting On: [Person]", and create a follow-up task with final step "Close the loop: send the final confirmation email/chat".
            Return JSON with keys:
            title, category (MEETING_RECORD|BRAIN_DUMP|SOP_DECISION|QUICK_NOTE),
            tldrSummary, hasRiskOrBlocker,
            sections ([{heading, bullets, annotation}]),
            tasks ([{title, quadrant, energyLoad, firstMicroStepUnder2Min, secondStep, thirdStep, estMinutes, dueDateNote, waitingOnPerson}]).
            INPUT: $cleanInput
        """.trimIndent()

        val fallbackBuilder: () -> String = {
            buildHeuristicOrganizedJson(cleanInput, people, topics, allEntities, energyState, capThreePerQuadrant)
        }

        val (rawOutput, diag) = executeWithMode(
            skillName = "Skill 1: ADHD Executive Brain Dump Organizer",
            contextHeader = header,
            prompt = prompt,
            fallbackGenerator = fallbackBuilder
        )

        return parseOrganizedResult(
            rawJson = rawOutput,
            fallbackBuilder = fallbackBuilder,
            rawSourceText = cleanInput,
            detectedPeople = people,
            detectedTopics = topics,
            allEntities = allEntities,
            energyState = energyState,
            header = header,
            diagnostics = diag
        )
    }

    private fun buildHeuristicOrganizedJson(
        rawText: String,
        people: List<String>,
        topics: List<String>,
        allEntities: List<EntityContextWeight>,
        energyState: EnergyBandwidthState,
        capThreePerQuadrant: Boolean
    ): String {
        val lower = rawText.lowercase()
        val distinctItems = splitBrainDumpIntoDistinctItems(rawText)
        val isLowEnergyOverride = energyState == EnergyBandwidthState.FRIED ||
            listOf("low energy", "fried", "exhausted", "stuck").any { lower.contains(it) }

        val hasRisk = listOf(
            "risk", "blocker", "blocked", "waiting on", "waiting for", "sla", "escalat",
            "urgent", "asap", "today", "delay", "drop", "outage", "issue", "spike"
        ).any { lower.contains(it) }

        val primaryTopic = topics.firstOrNull() ?: "Executive Operations"
        val primaryPerson = people.firstOrNull()

        val generatedTitle = if (distinctItems.size > 1) {
            "⚡ Action Deck Dump (${distinctItems.size} Tasks) • $primaryTopic"
        } else {
            val lead = distinctItems.firstOrNull()?.take(54)?.replaceFirstChar { it.uppercase() } ?: "Captured Task"
            if (primaryPerson != null && !lead.contains(primaryPerson, true)) {
                "$primaryTopic (@$primaryPerson): $lead"
            } else {
                lead
            }
        }

        val tasksArr = JSONArray()
        val waitingOnFollowUps = mutableListOf<JSONObject>()
        val quadrantTaskCounts = mutableMapOf<TaskQuadrant, Int>()

        distinctItems.forEachIndexed { index, itemText ->
            val itemLower = itemText.lowercase()
            val (itemPeople, itemTopics) = extractEntitiesFromText(itemText, allEntities)
            val matchedWeights = allEntities.filter { e ->
                itemPeople.any { it.equals(e.entityName, ignoreCase = true) } ||
                    itemTopics.any { it.equals(e.entityName, ignoreCase = true) }
            }

            // Detect "Waiting On: [Person]"
            val waitingMatch = Regex("(?:waiting on|waiting for|blocked by|pending|need sign-off from|awaiting)\\s+@?([A-Za-z][A-Za-z0-9_-]{1,20})", RegexOption.IGNORE_CASE)
                .find(itemText)
            val waitingPerson = waitingMatch?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }
                ?: if (itemLower.contains("waiting") || itemLower.contains("blocked")) itemPeople.firstOrNull() else null

            // Extract deadline tag -> 📅 Due: [Date]
            val extractedDue = extractDueDateTag(itemText)

            // Determine Quadrant + Anchor Emoji
            val rawQuadrant = when {
                itemLower.contains("park") || itemLower.contains("someday") || itemLower.contains("later") || itemLower.contains("idea") ->
                    TaskQuadrant.Q4_PARKED_LOT
                waitingPerson != null ->
                    TaskQuadrant.Q3_BATCH_CONTAIN
                itemLower.contains("urgent") || itemLower.contains("asap") || itemLower.contains("today") ||
                    itemLower.contains("now") || itemLower.contains("deadline") || itemLower.contains("emergency") ||
                    matchedWeights.any { it.learnedQuadrantBias == TaskQuadrant.Q1_DO_NOW } ->
                    TaskQuadrant.Q1_DO_NOW
                itemLower.contains("quick") || itemLower.contains("email") || itemLower.contains("call") ||
                    itemLower.contains("reply") || itemLower.contains("ping") || index == 0 ->
                    if (isLowEnergyOverride) TaskQuadrant.Q2_PROTECT_BUILD else (matchedWeights.firstOrNull()?.learnedQuadrantBias ?: TaskQuadrant.Q1_DO_NOW)
                else ->
                    matchedWeights.mapNotNull { it.learnedQuadrantBias }.minByOrNull { it.ordinal }
                        ?: TaskQuadrant.Q2_PROTECT_BUILD
            }

            val currentQuadCount = quadrantTaskCounts.getOrDefault(rawQuadrant, 0)
            if (!capThreePerQuadrant || currentQuadCount < 3) {
                quadrantTaskCounts[rawQuadrant] = currentQuadCount + 1

                val isQuickWin = itemText.length < 48 ||
                    listOf("call", "email", "text", "ping", "reply", "check", "send", "pay", "book").any { itemLower.contains(it) }

                val energyLoad = when {
                    isQuickWin || isLowEnergyOverride -> EnergyLoad.LOW_DRAIN
                    rawQuadrant == TaskQuadrant.Q1_DO_NOW -> EnergyLoad.DEEP_WORK
                    else -> matchedWeights.mapNotNull { it.learnedEnergyBias }.firstOrNull() ?: EnergyLoad.STEADY_WORK
                }

                val cleanItemTitle = itemText
                    .replace(Regex("^(we\\s+need\\s+to|need\\s+to|i\\s+need\\s+to|must|todo:?|task:?)\\s+", RegexOption.IGNORE_CASE), "")
                    .replaceFirstChar { it.uppercase() }

                val anchoredTitle = when {
                    waitingPerson != null && !cleanItemTitle.contains("⏳") ->
                        "⏳ Waiting On: @$waitingPerson — $cleanItemTitle"
                    rawQuadrant == TaskQuadrant.Q4_PARKED_LOT && !cleanItemTitle.startsWith("🅿️") ->
                        "🅿️ $cleanItemTitle"
                    rawQuadrant == TaskQuadrant.Q1_DO_NOW && !cleanItemTitle.startsWith("🔴") ->
                        "🔴 $cleanItemTitle"
                    isQuickWin && !cleanItemTitle.startsWith("🟢") ->
                        "🟢 $cleanItemTitle"
                    else -> cleanItemTitle
                }

                val (step1, step2, step3, estMins) = buildPhysicalMicroStepsForTask(
                    cleanItemTitle = cleanItemTitle,
                    person = waitingPerson ?: itemPeople.firstOrNull(),
                    topic = itemTopics.firstOrNull() ?: primaryTopic,
                    isWaitingOn = waitingPerson != null
                )

                val dueBadge = if (waitingPerson != null) {
                    "⏳ Waiting On: @$waitingPerson • $extractedDue"
                } else {
                    extractedDue
                }

                tasksArr.put(JSONObject().apply {
                    put("title", anchoredTitle)
                    put("quadrant", rawQuadrant.name)
                    put("energyLoad", energyLoad.name)
                    put("firstMicroStepUnder2Min", step1)
                    put("secondStep", step2)
                    put("thirdStep", step3)
                    put("estMinutes", estMins)
                    put("dueDateNote", dueBadge)
                    put("waitingOnPerson", waitingPerson ?: "")
                    put("peopleTags", JSONArray(itemPeople.ifEmpty { people }))
                    put("topicTags", JSONArray(itemTopics.ifEmpty { topics.ifEmpty { listOf("Executive Ops") } }))
                })

                // STEP 3 RULE: "If a task is blocked by someone else, tag it as ⏳ Waiting On: @Person.
                // Automatically create a deferred task to follow up with that person, and ensure the final step
                // is explicitly defined as 'Close the loop (send final message / save file)'."
                if (waitingPerson != null) {
                    waitingOnFollowUps.add(JSONObject().apply {
                        put("title", "🍞 Follow Up w/ @$waitingPerson: $cleanItemTitle")
                        put("quadrant", TaskQuadrant.Q3_BATCH_CONTAIN.name)
                        put("energyLoad", EnergyLoad.LOW_DRAIN.name)
                        put("status", TaskStatus.PAUSED_BREADCRUMB.name)
                        put("blocker", "⏳ Waiting On: @$waitingPerson")
                        put("firstMicroStepUnder2Min", "Open chat/email to @$waitingPerson & send 1-line check-in question")
                        put("secondStep", "Log @$waitingPerson's response or updated ETA in Rei")
                        put("thirdStep", "Close the loop (send final message / save file)")
                        put("estMinutes", 1)
                        put("dueDateNote", "⏳ Waiting On: @$waitingPerson • Deferred Follow-Up")
                        put("waitingOnPerson", waitingPerson)
                        put("peopleTags", JSONArray(listOf(waitingPerson) + itemPeople))
                        put("topicTags", JSONArray(itemTopics.ifEmpty { listOf("Follow-Up") }))
                    })
                }
            }
        }

        // Append auto-generated deferred follow-up tasks for any Waiting On blockers
        waitingOnFollowUps.forEach { tasksArr.put(it) }

        // Build concise, visual-anchor formatted Second Brain summary card (never a wall of text)
        val matrixBullets = mutableListOf<String>()
        for (i in 0 until tasksArr.length()) {
            val t = tasksArr.getJSONObject(i)
            matrixBullets.add("${t.optString("title")} (${t.optString("dueDateNote")})")
        }

        val topTaskObj = if (tasksArr.length() > 0) tasksArr.getJSONObject(0) else null
        val topTaskTitle = topTaskObj?.optString("title") ?: generatedTitle
        val topStep1 = topTaskObj?.optString("firstMicroStepUnder2Min")
            ?: "Open note & write the first 3 words (⏱️ Time to complete: 1 minute)"
        val topEstMins = topTaskObj?.optInt("estMinutes", 2) ?: 2

        val tldrSummary = "🎯 Top Task: $topTaskTitle | ⚡ First Micro-Step: $topStep1 (⏱️ Time to complete: $topEstMins minutes)."

        val sectionsArr = JSONArray().apply {
            put(JSONObject().apply {
                put("heading", "🎯 Step 4 & 5: Top Task & Frictionless Starter Step")
                put(
                    "bullets",
                    JSONArray(
                        listOf(
                            "**Top Task**: $topTaskTitle",
                            "**First Micro-Step**: $topStep1",
                            "**⏱️ Time to complete**: $topEstMins minute${if (topEstMins == 1) "" else "s"}"
                        )
                    )
                )
            })
            put(JSONObject().apply {
                put("heading", "📊 Step 3: Eisenhower Matrix & Deadlines (${tasksArr.length()} Individual Tasks Created)")
                put("bullets", JSONArray(matrixBullets))
                if (waitingOnFollowUps.isNotEmpty()) {
                    put("annotation", "⏳ Auto-Created ${waitingOnFollowUps.size} Deferred 'Waiting On' Follow-Up Task(s) with 'Close the loop' final step.")
                }
            })
        }

        return JSONObject().apply {
            put("title", generatedTitle)
            put("category", if (distinctItems.size > 1) CardCategory.BRAIN_DUMP.name else CardCategory.QUICK_NOTE.name)
            put("tldrSummary", tldrSummary)
            put("hasRiskOrBlocker", hasRisk || waitingOnFollowUps.isNotEmpty())
            put("sections", sectionsArr)
            put("tasks", tasksArr)
        }.toString()
    }

    /**
     * Extracts explicit or implied deadlines and formats them with `📅 Due: [Date]`.
     */
    fun extractDueDateTag(itemText: String): String {
        val lower = itemText.lowercase()
        val explicitMatch = Regex("(?:by|before|due:?|on|until)\\s+((?:today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|eod|cob|next week|this week)(?:\\s+at\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)?|\\d{1,2}/\\d{1,2}|\\d{1,2}:\\d{2}\\s*(?:am|pm)?)", RegexOption.IGNORE_CASE)
            .find(itemText)

        if (explicitMatch != null) {
            val found = explicitMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            return "📅 Due: $found"
        }

        return when {
            lower.contains("today") || lower.contains("asap") || lower.contains("urgent") || lower.contains("now") ->
                "📅 Due: Today"
            lower.contains("tomorrow") ->
                "📅 Due: Tomorrow"
            lower.contains("thursday") ->
                "📅 Due: Thursday"
            lower.contains("friday") || lower.contains("eow") ->
                "📅 Due: Friday"
            lower.contains("monday") ->
                "📅 Due: Monday"
            lower.contains("soon") || lower.contains("before meeting") ->
                "📅 Due: Today (Confirm exact time)"
            else ->
                "📅 Due: This Week (Tap to set date)"
        }
    }

    /**
     * Generates a ridiculously small, frictionless physical first micro-step + realistic time estimate
     * (1–2 minutes) and explicit "Close the loop" final step.
     */
    fun buildPhysicalMicroStepsForTask(
        cleanItemTitle: String,
        person: String?,
        topic: String,
        isWaitingOn: Boolean
    ): Tuple4<String, String, String, Int> {
        val lower = cleanItemTitle.lowercase()
        val targetPerson = person?.let { "@$it" } ?: "the recipient"

        return when {
            isWaitingOn -> Tuple4(
                "Open Teams/Outlook & type '$targetPerson' in the To: field — paste 1-sentence status check",
                "Record $targetPerson's blocker update or new target date",
                "Close the loop (send final message / save file)",
                1
            )
            lower.contains("call") || lower.contains("phone") -> Tuple4(
                "Pick up phone, open Contacts, and dial $targetPerson right now",
                "Ask the single core question & jot down the 1-line answer",
                "Close the loop (send final message / save file)",
                2
            )
            lower.contains("email") || lower.contains("send") || lower.contains("reply") || lower.contains("ping") || lower.contains("message") -> Tuple4(
                "Open compose window to $targetPerson & type a 4-word subject line for $topic",
                "Write 2 bullet points stating the decision or ask — zero fluff",
                "Close the loop (send final message / save file)",
                1
            )
            lower.contains("car") || lower.contains("mechanic") || lower.contains("oil") || lower.contains("tire") -> Tuple4(
                "Unlock phone & pull up the shop's phone number or booking page",
                "Pick the first open morning drop-off slot",
                "Close the loop (send final message / save file)",
                1
            )
            lower.contains("tax") || lower.contains("budget") || lower.contains("spreadsheet") || lower.contains("report") || lower.contains("audit") -> Tuple4(
                "Create/open the folder or file for '$cleanItemTitle' and type ONLY the top header line",
                "Drop in the first 3 numbers or documents without formatting",
                "Close the loop (send final message / save file)",
                2
            )
            else -> Tuple4(
                "Open the workspace/note for '$cleanItemTitle' and write 1 rough starter bullet for $targetPerson",
                "Complete the core 80% action item (ignore perfectionism)",
                "Close the loop (send final message / save file)",
                2
            )
        }
    }

    private fun parseOrganizedResult(
        rawJson: String,
        fallbackBuilder: () -> String,
        rawSourceText: String,
        detectedPeople: List<String>,
        detectedTopics: List<String>,
        allEntities: List<EntityContextWeight>,
        energyState: EnergyBandwidthState,
        header: String,
        diagnostics: AiEngineDiagnostics
    ): OrganizedBrainDumpResult {
        val distinctSourceItems = splitBrainDumpIntoDistinctItems(rawSourceText)
        val fallbackJsonString = fallbackBuilder()
        val cleanedJson = extractJsonObjectBlock(rawJson) ?: fallbackJsonString
        var obj = runCatching { JSONObject(cleanedJson) }.getOrElse { JSONObject(fallbackJsonString) }

        // CRITICAL FIX: If the user supplied multiple items in their Brain Dump, but the raw model
        // returned fewer tasks than distinct items (collapsing them into bullets of 1 card/task),
        // always use the multi-task decomposition from fallbackJsonString so EVERY item becomes its own ReiTask!
        val modelTasksArr = obj.optJSONArray("tasks")
        if (distinctSourceItems.size > 1 && (modelTasksArr == null || modelTasksArr.length() < distinctSourceItems.size)) {
            obj = JSONObject(fallbackJsonString)
        }

        val title = obj.optString("title", "Captured Executive Brief").ifBlank { "Captured Executive Brief" }
        val category = runCatching { CardCategory.valueOf(obj.optString("category")) }.getOrDefault(CardCategory.BRAIN_DUMP)
        val tldr = obj.optString("tldrSummary", rawSourceText.take(180))
        val hasRisk = obj.optBoolean("hasRiskOrBlocker", false)

        val sections = mutableListOf<OutlineSection>()
        val sectionsArr = obj.optJSONArray("sections") ?: JSONArray()
        for (i in 0 until sectionsArr.length()) {
            val sObj = sectionsArr.getJSONObject(i)
            val heading = sObj.optString("heading", "Key Outline")
            val bArr = sObj.optJSONArray("bullets") ?: JSONArray()
            val bullets = mutableListOf<String>()
            for (j in 0 until bArr.length()) {
                val b = bArr.optString(j)
                if (b.isNotBlank()) bullets.add(b)
            }
            val ann = sObj.optString("annotation", "").takeIf { it.isNotBlank() }
            sections.add(OutlineSection(heading, bullets, ann))
        }

        val card = KnowledgeCard(
            title = title,
            category = category,
            tldrSummary = tldr,
            outlineSectionsJson = ReiJsonCodec.encodeOutlineSections(sections),
            peopleTags = detectedPeople,
            topicTags = detectedTopics.ifEmpty { listOf("Executive Ops") },
            hasRiskOrBlocker = hasRisk,
            rawSourceText = rawSourceText
        )

        val tasks = mutableListOf<ReiTask>()
        val tasksArr = obj.optJSONArray("tasks") ?: JSONArray()
        val matrixBullets = mutableListOf<String>()
        val waitingFollowUps = mutableListOf<String>()

        for (i in 0 until tasksArr.length()) {
            val tObj = tasksArr.getJSONObject(i)
            val taskTitle = tObj.optString("title", "Follow up on $title")
            val quad = runCatching { TaskQuadrant.valueOf(tObj.optString("quadrant")) }.getOrDefault(
                allEntities.firstOrNull { e -> detectedPeople.contains(e.entityName) }?.learnedQuadrantBias
                    ?: TaskQuadrant.Q1_DO_NOW
            )
            val energy = runCatching { EnergyLoad.valueOf(tObj.optString("energyLoad")) }.getOrDefault(EnergyLoad.STEADY_WORK)
            val status = runCatching { TaskStatus.valueOf(tObj.optString("status", TaskStatus.ACTIVE.name)) }.getOrDefault(TaskStatus.ACTIVE)
            val estMins = tObj.optInt("estMinutes", 2).coerceIn(1, 5)
            val step1 = tObj.optString("firstMicroStepUnder2Min", "Open note & write 1-line starter bullet")
            val step2 = tObj.optString("secondStep", "Confirm data & owner alignment")
            val step3 = "Close the loop (send final message / save file)"
            val dueNote = tObj.optString("dueDateNote", "📅 Due: Today")
            val blocker = tObj.optString("blocker", "")

            val itemPeopleArr = tObj.optJSONArray("peopleTags")
            val taskPeople = if (itemPeopleArr != null && itemPeopleArr.length() > 0) {
                (0 until itemPeopleArr.length()).mapNotNull { idx -> itemPeopleArr.optString(idx).takeIf { it.isNotBlank() } }
            } else {
                detectedPeople
            }

            val itemTopicArr = tObj.optJSONArray("topicTags")
            val taskTopics = if (itemTopicArr != null && itemTopicArr.length() > 0) {
                (0 until itemTopicArr.length()).mapNotNull { idx -> itemTopicArr.optString(idx).takeIf { it.isNotBlank() } }
            } else {
                detectedTopics.ifEmpty { listOf("Executive Ops") }
            }

            tasks.add(
                ReiTask(
                    title = taskTitle,
                    quadrant = quad,
                    energyLoad = energy,
                    status = status,
                    microStepsJson = ReiJsonCodec.encodeMicroSteps(
                        listOf(
                            MicroStep("m1", step1, isCompleted = false, estMinutes = estMins),
                            MicroStep("m2", step2, isCompleted = false, estMinutes = 2),
                            MicroStep("m3", step3, isCompleted = false, estMinutes = 1)
                        )
                    ),
                    peopleTags = taskPeople,
                    topicTags = taskTopics,
                    dueDateNote = dueNote,
                    breadcrumbBlocker = blocker,
                    breadcrumbResumeStep = if (status == TaskStatus.PAUSED_BREADCRUMB) step1 else ""
                )
            )

            matrixBullets.add("**${quad.shortCode}** • $taskTitle ($dueNote)")
            if (taskTitle.startsWith("🍞 Follow Up")) {
                waitingFollowUps.add("$taskTitle → Final Step: **Close the loop**")
            }
        }

        val (hero, _) = selectHeroTaskForBandwidth(tasks, energyState)
        val topTask = hero ?: tasks.firstOrNull()
        val topStep = topTask?.microSteps?.firstOrNull()?.text
            ?: "Open workspace & write the first 3 words"
        val topMins = topTask?.microSteps?.firstOrNull()?.estMinutes ?: 2

        val briefing = AdhdExecutiveBriefing(
            protocolHeadline = "✅ Brain Dump Structured into ${tasks.size} Individual Matrix Task(s)",
            topTaskTitle = topTask?.title ?: title,
            topTaskMicroStep = topStep,
            timeEstimateLabel = "⏱️ Time to complete: $topMins minute${if (topMins == 1) "" else "s"}",
            matrixBullets = matrixBullets,
            waitingOnAutoFollowUps = waitingFollowUps
        )

        return OrganizedBrainDumpResult(
            card = card,
            extractedTasks = tasks,
            briefing = briefing,
            contextHeaderUsed = header,
            diagnostics = diagnostics
        )
    }

    // =========================================================================
    // SKILL 2: Rei Tactical Protocols (Shrink It, Park It, Where Was I?, Rabbit Hole, Snooze)
    // =========================================================================
    suspend fun shrinkTaskIntoMicroSteps(
        task: ReiTask,
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState
    ): Pair<List<MicroStep>, AiEngineDiagnostics> {
        val header = buildDynamicContextHeader(task.title, allEntities, activeQ1Count, energyState)
        val personTarget = task.peopleTags.firstOrNull()?.let { "@$it" } ?: "the owner"
        val cleanTitle = task.title
            .replace(Regex("^[🔴🟢⏳🅿️🍞⚡🔬🤏]+\\s*"), "")
            .trim()
        val currentActiveStepObj = task.microSteps.firstOrNull { !it.isCompleted }
        val currentFirstStep = (currentActiveStepObj?.text ?: cleanTitle)
            .replace(Regex("^🤏\\s*(<60s:|30s:)?\\s*"), "")
            .replace(Regex("^🔬[^:]*:\\s*"), "")
            .trim()
        val alreadyShrunkOnce = task.microSteps.any { it.id.startsWith("shrink_") }

        val prompt = """
            $ADHD_EXECUTIVE_SYSTEM_PROMPT
            SKILL_2_SHRINK_IT: Take current micro-step "$currentFirstStep" for task "$cleanTitle" and cut it down into an EVEN SMALLER, zero-friction physical action (< 60 seconds).
            Return JSON array of 3 strings, where item 3 is always "Close the loop (send final message / save file)".
        """.trimIndent()

        val fallback: () -> String = {
            if (!alreadyShrunkOnce) {
                val smallerCurrentStep = "🤏 <60s: Open the window/app for '${currentFirstStep.take(48)}' and type just the first 5 words to $personTarget"
                val followOnStep = "Finish: $currentFirstStep"
                val closeLoopStep = "Close the loop (send final message / save file)"
                JSONArray(listOf(smallerCurrentStep, followOnStep, closeLoopStep)).toString()
            } else {
                val atomicStep = "🤏 30s Atomic Action: Touch keyboard, open the file/message for '${cleanTitle.take(40)}', and type ONLY the subject/heading line"
                val followOnStep = "Write 1 unpolished line to $personTarget"
                val closeLoopStep = "Close the loop (send final message / save file)"
                JSONArray(listOf(atomicStep, followOnStep, closeLoopStep)).toString()
            }
        }

        val (raw, diag) = executeWithMode(
            skillName = "Skill 2: Smaller Step Protocol",
            contextHeader = header,
            prompt = prompt,
            fallbackGenerator = fallback
        )

        val steps = runCatching {
            val arr = JSONArray(extractJsonArrayBlock(raw) ?: fallback())
            listOf(
                MicroStep(if (alreadyShrunkOnce) "shrink_atomic_1" else "shrink_1", arr.optString(0), isCompleted = false, estMinutes = 1),
                MicroStep("shrink_2", arr.optString(1), isCompleted = false, estMinutes = 1),
                MicroStep("shrink_3", "Close the loop (send final message / save file)", isCompleted = false, estMinutes = 1)
            )
        }.getOrElse {
            val arr = JSONArray(fallback())
            listOf(
                MicroStep(if (alreadyShrunkOnce) "shrink_atomic_1" else "shrink_1", arr.optString(0), isCompleted = false, estMinutes = 1),
                MicroStep("shrink_2", arr.optString(1), isCompleted = false, estMinutes = 1),
                MicroStep("shrink_3", "Close the loop (send final message / save file)", isCompleted = false, estMinutes = 1)
            )
        }

        return steps to diag
    }

    fun buildParkedLotTaskFromTangent(
        rawTangent: String,
        allEntities: List<EntityContextWeight>
    ): ReiTask {
        val (people, topics) = extractEntitiesFromText(rawTangent, allEntities)
        val cleanTitle = rawTangent.trim().replaceFirstChar { it.uppercase() }
        val parkedTitle = if (cleanTitle.startsWith("🅿️")) cleanTitle else "🅿️ $cleanTitle"
        return ReiTask(
            title = parkedTitle,
            quadrant = TaskQuadrant.Q4_PARKED_LOT,
            energyLoad = EnergyLoad.LOW_DRAIN,
            status = TaskStatus.ACTIVE,
            microStepsJson = ReiJsonCodec.encodeMicroSteps(
                listOf(
                    MicroStep("p1", "Review parked thought & decide if it needs action (⏱️ Time to complete: 1 minute)", false, 1),
                    MicroStep("p2", "Tag owner (${people.firstOrNull()?.let { "@$it" } ?: "TBD"}) if action needed", false, 1),
                    MicroStep("p3", "Close the loop or promote to active matrix", false, 1)
                )
            ),
            peopleTags = people,
            topicTags = topics.ifEmpty { listOf("Parked Tangent") },
            dueDateNote = "🅿️ Parked • Zero Focus Interruption"
        )
    }

    fun buildWhereWasIContextReload(allTasks: List<ReiTask>): WhereWasIBannerState {
        val activeTasks = allTasks.filter { it.status == TaskStatus.ACTIVE }
        val breadcrumbs = allTasks.filter { it.status == TaskStatus.PAUSED_BREADCRUMB }
        val parked = allTasks.filter { it.quadrant == TaskQuadrant.Q4_PARKED_LOT && it.status != TaskStatus.DONE }

        val topActive = activeTasks
            .sortedWith(compareBy<ReiTask> { it.quadrant.ordinal }.thenByDescending { it.updatedAt })
            .firstOrNull()

        val resumeTarget = breadcrumbs.firstOrNull() ?: topActive
        val nextMicroStep = when {
            resumeTarget == null -> "All active queues clear. Capture a thought or review Q2 strategy."
            resumeTarget.status == TaskStatus.PAUSED_BREADCRUMB -> {
                val whyStuck = resumeTarget.breadcrumbBlocker.ifBlank { "External dependency" }
                val step = resumeTarget.breadcrumbResumeStep.ifBlank {
                    resumeTarget.microSteps.firstOrNull { !it.isCompleted }?.text ?: resumeTarget.title
                }
                "🍞 Stopped Because: $whyStuck → Next Physical Step: $step (⏱️ Time to complete: 2 minutes)"
            }
            else -> {
                val firstOpen = resumeTarget.microSteps.firstOrNull { !it.isCompleted }
                val stepText = firstOpen?.text ?: "Run 99% Check: Did you physically hit send, save the file, or close the tab?"
                val mins = firstOpen?.estMinutes ?: 2
                "🎯 Exact Micro-Step: $stepText (⏱️ Time to complete: $mins minutes)"
            }
        }

        return WhereWasIBannerState(
            lastActiveTask = topActive,
            pausedBreadcrumbs = breadcrumbs,
            recentParkedLot = parked.take(3),
            thirtySecondResumePrompt = nextMicroStep
        )
    }

    // =========================================================================
    // SKILL 3: "Focus My Energy" Bandwidth Matcher (With Fried Low-Energy Override)
    // =========================================================================
    fun selectHeroTaskForBandwidth(
        allTasks: List<ReiTask>,
        bandwidth: EnergyBandwidthState
    ): Pair<ReiTask?, String> {
        // Pull snoozed tasks ("⏰ Snoozed") off the active candidate list
        val candidates = allTasks.filter {
            it.status != TaskStatus.DONE && !it.dueDateNote.startsWith("⏰ Snoozed")
        }
        if (candidates.isEmpty()) {
            return null to "All active tasks completed! Unload a thought below to start."
        }

        // STEP 6 EMERGENCY COMMAND: Energy Override ("When the user taps 🟢 Quick Win,
        // bypass the Eisenhower Matrix and surface the single easiest, lowest-friction task (⏱️ 1-2 min) just to build momentum.")
        if (bandwidth == EnergyBandwidthState.FRIED) {
            val easiestTask = candidates.sortedWith(
                compareBy<ReiTask> { if (it.status == TaskStatus.ACTIVE) 0 else 1 }
                    .thenBy { if (it.energyLoad == EnergyLoad.LOW_DRAIN) 0 else 1 }
                    .thenBy { it.microSteps.firstOrNull { s -> !s.isCompleted }?.estMinutes ?: 2 }
                    .thenBy { it.title.length }
            ).firstOrNull()
            return easiestTask to "🟢 Quick Win Override • Bypassed Matrix → Easiest 1-2 min task to build momentum"
        }

        val targetLoad = bandwidth.targetLoad
        val matched = candidates.sortedWith(
            compareBy<ReiTask> { task ->
                if (task.status == TaskStatus.ACTIVE) 0 else 1
            }.thenBy { task ->
                task.quadrant.ordinal
            }.thenBy { task ->
                if (task.energyLoad == targetLoad) 0 else 1
            }.thenByDescending { task ->
                task.deferralCount
            }
        ).firstOrNull()

        val estMins = matched?.microSteps?.firstOrNull { !it.isCompleted }?.estMinutes ?: 2
        val reason = when (bandwidth) {
            EnergyBandwidthState.SHARP ->
                "🔴 Urgent • ⚡ Peak Bandwidth • ⏱️ Time: $estMins min"
            EnergyBandwidthState.STEADY ->
                "● Standard Bandwidth • ⏱️ Time: $estMins min"
            EnergyBandwidthState.FRIED ->
                "🟢 Quick Win • ⏱️ Time: $estMins min"
        }
        return matched to reason
    }

    // =========================================================================
    // SKILL 4: Contextual "Prep for Meetings" Aggregator & 30-Second Glance Card
    // =========================================================================
    suspend fun synthesizeMeetingPrepBrief(
        sqlBundle: MeetingPrepSqlBundle,
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState
    ): MeetingGlanceSynthesis {
        val tag = sqlBundle.entityTag
        val header = buildDynamicContextHeader(tag, allEntities, activeQ1Count, energyState)
        val roleSubtitle = sqlBundle.entityWeight?.roleOrContext
            ?: "Executive Stakeholder & Operational Context"

        val prompt = """
            SKILL_4_MEETING_PREP: Synthesize a 30-Second Glance Card for $tag ($roleSubtitle).
            SQL Records: MustDo=${sqlBundle.mustDoBefore.size}, Unblockers=${sqlBundle.yourMoveUnblockers.size},
            RecentCards=${sqlBundle.mustKnowRecentCards.size}, Risks=${sqlBundle.watchOutRiskCards.size + sqlBundle.watchOutStalledTasks.size}.
            Output JSON: {goal, yourMove, watchOutTrap, tldrBrief}.
        """.trimIndent()

        val fallback: () -> String = {
            val topTask = sqlBundle.mustDoBefore.firstOrNull()
            val topUnblocker = sqlBundle.yourMoveUnblockers.firstOrNull()
            val topCard = sqlBundle.mustKnowRecentCards.firstOrNull()
            val topRiskCard = sqlBundle.watchOutRiskCards.firstOrNull()
            val topStalled = sqlBundle.watchOutStalledTasks.firstOrNull()

            val goal = when {
                topTask != null -> "Lock decision & timeline on '${topTask.title}' while protecting ${sqlBundle.entityWeight?.learnedQuadrantBias?.shortCode ?: "Q1"} capacity."
                topCard != null -> "Confirm execution guardrails from '${topCard.title}' and clear cross-team handoffs."
                else -> "Align on immediate Q1/Q2 operational priorities and remove open blockers for $tag."
            }

            val yourMove = when {
                topUnblocker != null -> "🍞 Unblock '${topUnblocker.title}' — ${topUnblocker.breadcrumbResumeStep.ifBlank { topUnblocker.breadcrumbBlocker }}"
                topTask != null -> {
                    val step = topTask.microSteps.firstOrNull { !it.isCompleted }?.text ?: topTask.title
                    "Lead with: $step"
                }
                else -> "Open with a 2-sentence status pulse and ask for sign-off on next milestone."
            }

            val watchOut = when {
                topRiskCard != null -> {
                    val riskAnnotation = topRiskCard.outlineSections.mapNotNull { it.annotation }.firstOrNull()
                        ?: topRiskCard.tldrSummary
                    "Risk Flagged: $riskAnnotation"
                }
                topStalled != null ->
                    "Stall Trap (${topStalled.deferralCount}x deferred): Don't leave without a concrete owner on '${topStalled.title}'."
                else -> "Avoid scope creep into unvetted Q3/Q4 tangents—park non-urgent asks immediately."
            }

            val brief = buildString {
                if (topCard != null) {
                    append(topCard.tldrSummary)
                } else {
                    append("You have ${sqlBundle.mustDoBefore.size} open commitments and ${sqlBundle.yourMoveUnblockers.size} breadcrumb unblockers queued with $tag.")
                }
            }

            JSONObject().apply {
                put("goal", goal)
                put("yourMove", yourMove)
                put("watchOutTrap", watchOut)
                put("tldrBrief", brief)
            }.toString()
        }

        val (raw, diag) = executeWithMode(
            skillName = "Skill 4: Meeting Prep Synthesizer",
            contextHeader = header,
            prompt = prompt,
            fallbackGenerator = fallback
        )

        val parsed = runCatching {
            JSONObject(extractJsonObjectBlock(raw) ?: fallback())
        }.getOrElse { JSONObject(fallback()) }

        val checklist = buildList {
            sqlBundle.yourMoveUnblockers.forEach {
                add("🍞 Unblock: ${it.breadcrumbResumeStep.ifBlank { it.title }}")
            }
            sqlBundle.mustDoBefore.take(3).forEach {
                add("🔴 Commit: ${it.title}")
            }
            if (sqlBundle.watchOutRiskCards.isNotEmpty() || sqlBundle.watchOutStalledTasks.isNotEmpty()) {
                add("⚠️ Address flagged SLA / capacity risk before wrapping sync")
            }
            if (isEmpty()) {
                add("🟢 Confirm top Q1 priority & 🅿️ Park any new tangents")
            }
        }

        return MeetingGlanceSynthesis(
            entityLabel = tag,
            roleSubtitle = roleSubtitle,
            thirtySecondGoal = parsed.optString("goal"),
            yourMoveAction = parsed.optString("yourMove"),
            watchOutTrap = parsed.optString("watchOutTrap"),
            tldrMeetingBrief = parsed.optString("tldrBrief"),
            checklistItems = checklist,
            sqlBundle = sqlBundle,
            contextHeaderUsed = header,
            diagnostics = diag
        )
    }

    // =========================================================================
    // SKILL 5: Strategy & Grounded Comms Studio ("Draft It")
    // =========================================================================
    suspend fun runCommsStrategyStudio(
        lens: CommsStrategyLens,
        rawInput: String,
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState
    ): CommsStrategyOutput {
        val cleanInput = rawInput.trim().ifBlank {
            "We need to protect Diana's 8 specialized AR collectors from general inbound Epic SBO phone overflow so Q4 cash collections don't drop."
        }
        val header = buildDynamicContextHeader(cleanInput, allEntities, activeQ1Count, energyState)
        val (people, topics) = extractEntitiesFromText(cleanInput, allEntities)
        val primaryStakeholder = people.firstOrNull()?.let { "@$it" } ?: "Leadership"
        val primaryTopic = topics.firstOrNull() ?: "Operational Execution"

        val prompt = """
            SKILL_5_COMMS_STUDIO (${lens.name}): Transform input for $primaryStakeholder on $primaryTopic.
            Provide a rough first draft so the user only has to edit the logic and intent.
            INPUT: $cleanInput
        """.trimIndent()

        val fallback: () -> String = {
            buildCommsStrategyFallback(lens, cleanInput, primaryStakeholder, primaryTopic)
        }

        val (raw, diag) = executeWithMode(
            skillName = "Skill 5: ${lens.title}",
            contextHeader = header,
            prompt = prompt,
            fallbackGenerator = fallback
        )

        val obj = AiCoreSchemaRepair.parseOrMergeJsonObject(raw, fallback)
        val auditArr = obj.optJSONArray("audit") ?: JSONArray()
        val audits = (0 until auditArr.length()).map { auditArr.optString(it) }
        val lanesArr = obj.optJSONArray("lanes") ?: JSONArray()
        val lanes = (0 until lanesArr.length()).map {
            val l = lanesArr.getJSONObject(it)
            l.optString("lane") to l.optString("detail")
        }

        return CommsStrategyOutput(
            lens = lens,
            detectedReflexesOrAudit = audits,
            primaryHeading = obj.optString("primaryHeading"),
            primaryBody = obj.optString("primaryBody"),
            secondaryHeading = obj.optString("secondaryHeading"),
            secondaryBody = obj.optString("secondaryBody"),
            ruleOfThumbOrStopDoing = obj.optString("ruleOfThumb"),
            laneBreakdown = lanes,
            contextHeaderUsed = header,
            diagnostics = diag
        )
    }

    private fun buildCommsStrategyFallback(
        lens: CommsStrategyLens,
        cleanInput: String,
        stakeholder: String,
        topic: String
    ): String {
        val lower = cleanInput.lowercase()
        val coreSentence = cleanInput.lines().firstOrNull { it.isNotBlank() }?.trim() ?: cleanInput

        return when (lens) {
            CommsStrategyLens.GROUNDED_COMMS -> {
                val reflexes = mutableListOf<String>()
                if (lower.contains("sorry") || lower.contains("just") || lower.contains("maybe") || lower.contains("think")) {
                    reflexes.add("Cautious Hedging / Softeners detected ('just / maybe / think') — weakens executive authority.")
                }
                if (lower.contains("because") || lower.contains("tried") || lower.contains("busy")) {
                    reflexes.add("Pre-emptive Justification detected — leads with defensive backstory instead of the operational decision.")
                }
                if (cleanInput.length > 140) {
                    reflexes.add("Burying the Lead — key ask for $stakeholder appears after context buildup.")
                }
                if (reflexes.isEmpty()) {
                    reflexes.add("Draft It Protocol Active — Rough first draft ready so you only edit logic and intent.")
                    reflexes.add("Lead-First Calibration — Surfaced the concrete decision gate in sentence one.")
                }

                val option1 = "Hi ${stakeholder.removePrefix("@")}, to keep $topic on track this week, here is the proposed next step: $coreSentence. Let me know if you'd like any adjustments before 2:00 PM today so we can close the loop."
                val option2 = "BLUF ($topic): Executing the following step today — $coreSentence. Impact: Keeps SLA on target and prevents rework. Action needed from $stakeholder: Quick thumbs-up by 2:00 PM."
                val rule = "Rule of Thumb: State the operational guardrail and business impact in sentence one; never apologize for protecting capacity."

                JSONObject().apply {
                    put("audit", JSONArray(reflexes))
                    put("primaryHeading", "Option 1 • Rough First Draft (Collaborative)")
                    put("primaryBody", option1)
                    put("secondaryHeading", "Option 2 • Crisp 2-Sentence BLUF")
                    put("secondaryBody", option2)
                    put("ruleOfThumb", rule)
                }.toString()
            }

            CommsStrategyLens.PARETO_80_20 -> {
                val audits = listOf(
                    "Symptom: Repeated ad-hoc escalations, context switching, and queue overflow friction around $topic.",
                    "Root Cause: Missing mandatory intake gate at Tier-1 handoff and unbuffered capacity boundaries."
                )
                val lanes = JSONArray().apply {
                    put(JSONObject().apply {
                        put("lane", "🟢 100% In My Lane (Immediate Control)")
                        put("detail", "Enforce a 3-field intake gate, ring-fence high-skill blocks, and batch floor updates into a single 2:00 PM digest.")
                    })
                    put(JSONObject().apply {
                        put("lane", "🟡 Negotiated (Cross-Team Alignment)")
                        put("detail", "Lock SLA overflow timer thresholds and cutover sign-off criteria with $stakeholder.")
                    })
                    put(JSONObject().apply {
                        put("lane", "⚪ Fixed Weather (Accept & Route Around)")
                        put("detail", "Enterprise wave cutover dates and seasonal inbound call arrival spikes.")
                    })
                }
                val highLeverageMove = "The 20% High-Leverage Move: Lock a single non-negotiable intake & routing rule for $topic today so 80% of repetitive interruptions self-triage before reaching leadership."
                val stopDoing = "Stop Doing: Stop answering incomplete escalations in live chat threads—auto-return them with the 3-field checklist."

                JSONObject().apply {
                    put("audit", JSONArray(audits))
                    put("lanes", lanes)
                    put("primaryHeading", "The 20% High-Leverage Move")
                    put("primaryBody", highLeverageMove)
                    put("secondaryHeading", "Operational Trigger & Verification")
                    put("secondaryBody", "Apply directly to: \"$coreSentence\" — measure success by zero ad-hoc escalations over the next 48 hours.")
                    put("ruleOfThumb", stopDoing)
                }.toString()
            }

            CommsStrategyLens.EXEC_POSITIONING_ROI -> {
                val audits = listOf(
                    "Mode A • Executive ROI Translation Active",
                    "Converted tactical workflow mechanics into Revenue, Capacity, Governance, and Speed metrics for $stakeholder."
                )
                val roiBrief = buildString {
                    appendLine("• 💰 Revenue & Cash Protection: Preserves high-balance collection velocity by preventing specialist dilution on $topic.")
                    appendLine("• ⚙️ Capacity Unlocked: Reclaims ~6.5 frontline & supervisor hours/week via standardized intake gating.")
                    appendLine("• 🛡️ Governance & Risk Control: Eliminates unvetted statement/escalation defects before customer impact.")
                    append("• ⚡ Speed to Resolution: Cuts Tier-2 turnaround time by routing clean, pre-verified accounts on first touch.")
                }
                val execHeadline = "Executive Summary for $stakeholder: By formalizing the $topic operating guardrail ($coreSentence), we protect Q4 revenue yield and insulate specialized capacity without adding headcount."

                JSONObject().apply {
                    put("audit", JSONArray(audits))
                    put("primaryHeading", "Executive Headline (Board / VP Ready)")
                    put("primaryBody", execHeadline)
                    put("secondaryHeading", "4-Pillar ROI Breakdown (Revenue • Capacity • Governance • Speed)")
                    put("secondaryBody", roiBrief)
                    put("ruleOfThumb", "Positioning Lever: Always pair operational boundaries with the dollar or hours-saved counterfactual.")
                }.toString()
            }

            CommsStrategyLens.EXEC_POSITIONING_CLINICAL -> {
                val audits = listOf(
                    "Mode B • Clinical Paper-Trail Framer Active",
                    "Stripped emotional/interpersonal friction; documented workflow gap as an objective system state."
                )
                val clinicalNote = "Operational Status Log ($topic): Current workflow observation indicates that $coreSentence. To maintain target SLA and prevent downstream queue accumulation, items lacking standard intake criteria are being held in pending status awaiting upstream verification from $stakeholder."
                val followUpGate = "Next Checkpoint: Once the upstream routing parameter is confirmed in writing, Specialized Servicing will resume batch processing within the standard 4-hour SLA window."

                JSONObject().apply {
                    put("audit", JSONArray(audits))
                    put("primaryHeading", "Clinical Paper-Trail Record (Zero-Friction Neutrality)")
                    put("primaryBody", clinicalNote)
                    put("secondaryHeading", "Objective SLA Dependency & Handoff Gate")
                    put("secondaryBody", followUpGate)
                    put("ruleOfThumb", "Clinical Framing Rule: Describe the process state and SLA consequence in passive, verifiable terms—never critique individuals.")
                }.toString()
            }
        }
    }

    private suspend fun executeWithMode(
        skillName: String,
        contextHeader: String,
        prompt: String,
        fallbackGenerator: () -> String
    ): Pair<String, AiEngineDiagnostics> {
        if (forceSimMode) {
            val start = System.currentTimeMillis()
            val out = fallbackGenerator()
            val pretty = AiCoreSchemaRepair.prettyFormatJson(out)
            val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(6L)
            return out to AiEngineDiagnostics(
                status = EngineRuntimeStatus.SIM_FALLBACK,
                rawFeatureStatusCode = "Manual Toggle: Preview Sim Engine",
                lastSkillExecuted = skillName,
                lastPromptTokenEstimate = (contextHeader.length + prompt.length) / 4,
                lastContextHeaderPreview = contextHeader,
                lastLatencyMs = elapsed,
                schemaRepairApplied = false,
                repairNotes = listOf("Manual Sim Mode active — deterministic schema generated"),
                lastRawModelOutput = pretty,
                lastRepairedJsonOutput = pretty
            )
        }
        return aiEngine.runAtomicPrompt(
            skillName = skillName,
            contextHeader = contextHeader,
            instructionAndPayload = prompt,
            fallbackGenerator = fallbackGenerator
        )
    }

    suspend fun runHardwareSelfTestProbe(
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState
    ): AiEngineDiagnostics {
        val sampleInput = "Urgent sync with @Corrie and @Wayne on #Epic SBO: freeze legacy guarantor statements 48h before Wave-2 cutover to prevent duplicate patient billing."
        val header = buildDynamicContextHeader(sampleInput, allEntities, activeQ1Count, energyState)
        val prompt = """
            S25U_AICORE_SELF_TEST: Return JSON with title, category, tldrSummary, hasRiskOrBlocker for:
            "$sampleInput"
        """.trimIndent()
        val (people, topics) = extractEntitiesFromText(sampleInput, allEntities)
        val fallback: () -> String = {
            buildHeuristicOrganizedJson(sampleInput, people, topics, allEntities, energyState, false)
        }
        val (_, diag) = aiEngine.runAtomicPrompt(
            skillName = "S25U AICore Hardware Probe",
            contextHeader = header,
            instructionAndPayload = prompt,
            fallbackGenerator = fallback
        )
        return diag
    }

    fun runMalformedNanoRepairDemo(
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState
    ): AiEngineDiagnostics {
        val startMs = System.currentTimeMillis()
        val sampleInput = "Sync with @Corrie and @Debbie on #Epic SBO statement hold and #AR Goals protection."
        val header = buildDynamicContextHeader(sampleInput, allEntities, activeQ1Count, energyState)
        val (people, topics) = extractEntitiesFromText(sampleInput, allEntities)

        val simulatedNoisyNanoText = """
            ```json
            {
              "title": "Epic SBO Wave-2: Guarantor Hold & AR Collector Ring-Fence",
              "category": "MEETING_RECORD",
              "tldrSummary": "Corrie and Debbie approved the 48-hour legacy statement freeze prior to Wave-2 activation. Diana's 8 specialized AR collectors are ring-fenced from 8:00 AM to 1:00 PM.",
              "hasRiskOrBlocker": true,
            }
            ```
        """.trimIndent()

        val fallback: () -> String = {
            buildHeuristicOrganizedJson(sampleInput, people, topics, allEntities, energyState, false)
        }

        val (repaired, wasRepaired, notes) = AiCoreSchemaRepair.repairRawModelOutputWithNotes(
            rawText = simulatedNoisyNanoText,
            fallbackGenerator = fallback
        )
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(4L)

        return AiEngineDiagnostics(
            status = EngineRuntimeStatus.AICORE_SIMULATED_OVERRIDE,
            rawFeatureStatusCode = "Schema Repair Stress Test (Markdown Fence + Trailing Comma + Missing Keys)",
            lastSkillExecuted = "AiCoreSchemaRepair Stress Test",
            lastPromptTokenEstimate = (header.length + simulatedNoisyNanoText.length) / 4,
            lastContextHeaderPreview = header,
            lastLatencyMs = elapsed,
            schemaRepairApplied = wasRepaired,
            repairNotes = notes,
            lastRawModelOutput = simulatedNoisyNanoText,
            lastRepairedJsonOutput = AiCoreSchemaRepair.prettyFormatJson(repaired)
        )
    }

    /**
     * STEP 1 CLARIFICATION CHECK PROBE:
     * Scans for vague tasks (e.g., "the car", "work stuff") or massive projects (e.g., "do taxes")
     * and asks ONE specific, short question to find the actual next physical step (plus deadline/blocker check).
     */
    suspend fun generateClarifyingPrompts(
        rawTextOrTaskTitle: String,
        allEntities: List<EntityContextWeight>,
        activeQ1Count: Int,
        energyState: EnergyBandwidthState
    ): Pair<List<ClarifyingQuestionItem>, AiEngineDiagnostics> {
        val cleanInput = rawTextOrTaskTitle.trim().ifBlank { "Unspecified task" }
        val header = buildDynamicContextHeader(cleanInput, allEntities, activeQ1Count, energyState)
        val items = splitBrainDumpIntoDistinctItems(cleanInput)
        val targetVagueItem = items.firstOrNull { isVagueOrMassiveTask(it) } ?: items.firstOrNull() ?: cleanInput

        val (singleQuestion, physicalStepChips) = buildSinglePhysicalStepQuestion(targetVagueItem, allEntities)
        val vipPeopleChips = allEntities
            .filter { it.entityType == EntityType.PERSON }
            .take(5)
            .map { "⏳ Waiting On: @${it.entityName}" }
            .ifEmpty { listOf("⏳ Waiting On: @Corrie", "⏳ Waiting On: @Wayne", "⏳ Waiting On: @Debbie") }

        val prompt = """
            $ADHD_EXECUTIVE_SYSTEM_PROMPT
            STEP_1_CLARIFICATION_CHECK: Scan "$targetVagueItem". Ask ONE specific, short question to find the actual next physical step.
            Return JSON array: [{"dimension": "1. Next Physical Step (Required)", "question": "..."}].
        """.trimIndent()

        val fallback: () -> String = {
            JSONArray().apply {
                put(
                    JSONObject()
                        .put("dimension", "Step 1 • The Clarification Check (Actual Next Physical Step)")
                        .put("question", singleQuestion)
                )
            }.toString()
        }

        val (_, diag) = executeWithMode(
            skillName = "Step 1: ADHD Clarification Check",
            contextHeader = header,
            prompt = prompt,
            fallbackGenerator = fallback
        )

        val resultList = buildList {
            // Primary ONE specific, short question for the actual next physical step
            add(
                ClarifyingQuestionItem(
                    id = "clarify_physical_step",
                    dimensionLabel = "1. THE CLARIFICATION CHECK (ACTUAL NEXT PHYSICAL STEP)",
                    question = singleQuestion,
                    quickChips = physicalStepChips
                )
            )
            // Optional quick tags for Step 3 (📅 Due Date & ⏳ Waiting On) if implied/missing
            if (!hasExplicitTimeOrDate(targetVagueItem)) {
                add(
                    ClarifyingQuestionItem(
                        id = "clarify_due_or_blocker",
                        dimensionLabel = "2. OPTIONAL • 📅 DUE DATE OR ⏳ WAITING ON BLOCKER",
                        question = "Is there a deadline or someone blocking this? (Tap if applicable)",
                        quickChips = listOf(
                            "📅 Due: Today",
                            "📅 Due: Tomorrow",
                            "📅 Due: Friday",
                            "🟢 Quick Win (< 2 min)"
                        ) + vipPeopleChips.take(3)
                    )
                )
            }
        }

        return resultList to diag
    }

    private fun extractJsonObjectBlock(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }

    private fun extractJsonArrayBlock(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }
}

data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
