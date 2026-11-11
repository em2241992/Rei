package com.emaratta.rei.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Calendar

data class MeetingPrepSqlBundle(
    val entityTag: String,
    val entityWeight: EntityContextWeight?,
    val mustDoBefore: List<ReiTask>,
    val yourMoveUnblockers: List<ReiTask>,
    val mustKnowRecentCards: List<KnowledgeCard>,
    val watchOutRiskCards: List<KnowledgeCard>,
    val watchOutStalledTasks: List<ReiTask>
)

class ReiRepository(
    private val context: Context,
    private val database: ReiDatabase
) {
    private val dao: ReiDao = database.reiDao()

    val allCards: Flow<List<KnowledgeCard>> = dao.observeAllCards()
    val allTasks: Flow<List<ReiTask>> = dao.observeAllTasks()
    val allEntities: Flow<List<EntityContextWeight>> = dao.observeAllEntities()
    val recentTelemetry: Flow<List<BehavioralTelemetry>> = dao.observeRecentTelemetry()

    /**
     * Populates demo KnowledgeCards, ReiTasks, and VIP Entity weights ONLY when
     * Debug Sandbox Mode is explicitly enabled in Settings (or invoked by tests).
     */
    suspend fun populateDebugSandboxDataIfEmpty() = withContext(Dispatchers.IO) {
        if (dao.countCards() == 0 && dao.getAllTasksOnce().isEmpty()) {
            val now = System.currentTimeMillis()
            dao.upsertEntityWeights(ReiSeedData.buildSeedEntityWeights(now))
            dao.insertCards(ReiSeedData.buildSeedKnowledgeCards(now))
            dao.insertTasks(ReiSeedData.buildSeedTasks(now))
            dao.insertTelemetryList(ReiSeedData.buildSeedTelemetry(now))
            runCatching {
                dao.rebuildCardsFts()
                dao.rebuildTasksFts()
            }
        }
    }

    suspend fun purgeAllWorkspaceData(): String = withContext(Dispatchers.IO) {
        dao.purgeAllTables()
        "Cleared all cards, tasks, telemetry, and sandbox rules."
    }

    suspend fun searchCardsInstant(rawQuery: String): List<KnowledgeCard> = withContext(Dispatchers.IO) {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return@withContext dao.getAllCardsOnce()
        val sanitizedTokens = trimmed
            .replace(Regex("[^A-Za-z0-9@#_\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (sanitizedTokens.isEmpty()) {
            return@withContext dao.searchCardsFallback(trimmed)
        }
        val ftsQuery = sanitizedTokens.joinToString(" ") { token ->
            val clean = token.removePrefix("@").removePrefix("#")
            "${clean}*"
        }
        val ftsResults = runCatching { dao.searchCardsFts(ftsQuery) }.getOrDefault(emptyList())
        if (ftsResults.isNotEmpty()) ftsResults else dao.searchCardsFallback(trimmed.removePrefix("@").removePrefix("#"))
    }

    suspend fun searchTasksInstant(rawQuery: String): List<ReiTask> = withContext(Dispatchers.IO) {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return@withContext dao.getAllTasksOnce()
        val sanitizedTokens = trimmed
            .replace(Regex("[^A-Za-z0-9@#_\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        if (sanitizedTokens.isEmpty()) {
            return@withContext dao.searchTasksFallback(trimmed)
        }
        val ftsQuery = sanitizedTokens.joinToString(" ") { token ->
            val clean = token.removePrefix("@").removePrefix("#")
            "${clean}*"
        }
        val ftsResults = runCatching { dao.searchTasksFts(ftsQuery) }.getOrDefault(emptyList())
        if (ftsResults.isNotEmpty()) ftsResults else dao.searchTasksFallback(trimmed.removePrefix("@").removePrefix("#"))
    }

    suspend fun saveOrganizedCardAndTasks(
        card: KnowledgeCard,
        extractedTasks: List<ReiTask>
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cardId = dao.insertCard(card.copy(createdAt = now, updatedAt = now))
        val linkedTasks = extractedTasks.map { it.copy(linkedCardId = cardId, updatedAt = now) }
        if (linkedTasks.isNotEmpty()) {
            dao.insertTasks(linkedTasks)
        }
        // Update interaction counts for referenced @People and #Topics
        val allPeople = (card.peopleTags + linkedTasks.flatMap { it.peopleTags }).distinct()
        val allTopics = (card.topicTags + linkedTasks.flatMap { it.topicTags }).distinct()
        for (person in allPeople) {
            val clean = person.removePrefix("@").trim()
            if (clean.isBlank()) continue
            val existing = dao.getEntityWeight(clean)
            if (existing != null) {
                dao.upsertEntityWeight(
                    existing.copy(
                        interactionCount = existing.interactionCount + 1,
                        lastReferencedAt = now
                    )
                )
            } else {
                dao.upsertEntityWeight(
                    EntityContextWeight(
                        entityName = clean,
                        entityType = EntityType.PERSON,
                        roleOrContext = "Stakeholder captured in Second Brain",
                        learnedQuadrantBias = TaskQuadrant.Q2_PROTECT_BUILD,
                        learnedEnergyBias = EnergyLoad.STEADY_WORK,
                        interactionCount = 1,
                        lastReferencedAt = now
                    )
                )
            }
        }
        for (topic in allTopics) {
            val clean = topic.removePrefix("#").trim()
            if (clean.isBlank()) continue
            val existing = dao.getEntityWeight(clean)
            if (existing != null) {
                dao.upsertEntityWeight(
                    existing.copy(
                        interactionCount = existing.interactionCount + 1,
                        lastReferencedAt = now
                    )
                )
            } else {
                dao.upsertEntityWeight(
                    EntityContextWeight(
                        entityName = clean,
                        entityType = EntityType.PROJECT,
                        roleOrContext = "Operational topic / initiative",
                        learnedQuadrantBias = TaskQuadrant.Q2_PROTECT_BUILD,
                        learnedEnergyBias = EnergyLoad.STEADY_WORK,
                        interactionCount = 1,
                        lastReferencedAt = now
                    )
                )
            }
        }
        runCatching {
            dao.rebuildCardsFts()
            dao.rebuildTasksFts()
        }
        cardId
    }

    suspend fun insertOrUpdateCard(card: KnowledgeCard) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (card.id == 0L) {
            dao.insertCard(card.copy(createdAt = now, updatedAt = now))
        } else {
            dao.updateCard(card.copy(updatedAt = now))
        }
        runCatching { dao.rebuildCardsFts() }
    }

    suspend fun togglePinCard(card: KnowledgeCard) = withContext(Dispatchers.IO) {
        dao.updateCard(card.copy(isPinned = !card.isPinned, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCard(cardId: Long) = withContext(Dispatchers.IO) {
        dao.deleteCardById(cardId)
        runCatching { dao.rebuildCardsFts() }
    }

    suspend fun insertOrUpdateTask(task: ReiTask): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = if (task.id == 0L) {
            dao.insertTask(task.copy(updatedAt = now))
        } else {
            dao.updateTask(task.copy(updatedAt = now))
            task.id
        }
        runCatching { dao.rebuildTasksFts() }
        id
    }

    suspend fun deleteTask(taskId: Long) = withContext(Dispatchers.IO) {
        dao.deleteTaskById(taskId)
        runCatching { dao.rebuildTasksFts() }
    }

    /**
     * Implicit Learning: Changing a task's quadrant updates EntityContextWeight for its tags
     * and logs a QUADRANT_OVERRIDE telemetry record. Returns the learned summary message for Snackbar.
     */
    suspend fun overrideTaskQuadrantAndLearn(
        task: ReiTask,
        newQuadrant: TaskQuadrant
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.updateTask(task.copy(quadrant = newQuadrant, updatedAt = now))

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dow = cal.get(Calendar.DAY_OF_WEEK)

        val learnedEntities = mutableListOf<String>()

        for (rawPerson in task.peopleTags) {
            val personName = rawPerson.removePrefix("@").trim()
            if (personName.isBlank()) continue
            val existing = dao.getEntityWeight(personName)
            val updated = (existing ?: EntityContextWeight(
                entityName = personName,
                entityType = EntityType.PERSON,
                roleOrContext = "Executive Stakeholder"
            )).copy(
                learnedQuadrantBias = newQuadrant,
                interactionCount = (existing?.interactionCount ?: 0) + 1,
                lastReferencedAt = now
            )
            dao.upsertEntityWeight(updated)
            learnedEntities.add("@$personName")
        }

        for (rawTopic in task.topicTags) {
            val topicName = rawTopic.removePrefix("#").trim()
            if (topicName.isBlank()) continue
            val existing = dao.getEntityWeight(topicName)
            val updated = (existing ?: EntityContextWeight(
                entityName = topicName,
                entityType = EntityType.PROJECT,
                roleOrContext = "Strategic Initiative"
            )).copy(
                learnedQuadrantBias = newQuadrant,
                interactionCount = (existing?.interactionCount ?: 0) + 1,
                lastReferencedAt = now
            )
            dao.upsertEntityWeight(updated)
            learnedEntities.add("#$topicName")
        }

        val targetLabel = if (learnedEntities.isNotEmpty()) {
            learnedEntities.take(2).joinToString(" & ")
        } else {
            "\"${task.title.take(26)}\""
        }

        dao.insertTelemetry(
            BehavioralTelemetry(
                eventType = TelemetryEventType.QUADRANT_OVERRIDE,
                subjectKey = targetLabel,
                detailValue = "Prioritized $targetLabel as ${newQuadrant.title}",
                hourOfDay = hour,
                dayOfWeek = dow,
                timestamp = now
            )
        )

        "Learned: Prioritizing $targetLabel as ${newQuadrant.shortCode} (${newQuadrant.title.substringAfter("• ").trim()}) going forward"
    }

    suspend fun recordEnergyCheckIn(state: EnergyBandwidthState) = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        dao.insertTelemetry(
            BehavioralTelemetry(
                eventType = TelemetryEventType.ENERGY_CHECKIN,
                subjectKey = state.name,
                detailValue = "Checked in as ${state.emoji} ${state.label} at ${cal.get(Calendar.HOUR_OF_DAY)}:00",
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordTaskShrinkTelemetry(task: ReiTask) = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        dao.insertTelemetry(
            BehavioralTelemetry(
                eventType = TelemetryEventType.TASK_STALL_SHRINK,
                subjectKey = task.title.take(36),
                detailValue = "Decomposed stalled task (deferrals=${task.deferralCount}) into 3 <2m micro-steps",
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveBreadcrumbDeferral(
        task: ReiTask,
        stoppedAt: String,
        blocker: String,
        resumeStep: String
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = task.copy(
            status = TaskStatus.PAUSED_BREADCRUMB,
            breadcrumbStoppedAt = stoppedAt.trim(),
            breadcrumbBlocker = blocker.trim(),
            breadcrumbResumeStep = resumeStep.trim(),
            deferralCount = task.deferralCount + 1,
            updatedAt = now
        )
        dao.updateTask(updated)
        val cal = Calendar.getInstance()
        dao.insertTelemetry(
            BehavioralTelemetry(
                eventType = TelemetryEventType.BREADCRUMB_CREATED,
                subjectKey = task.title.take(36),
                detailValue = "Paused at: ${stoppedAt.take(40)} | Blocker: ${blocker.take(40)}",
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                timestamp = now
            )
        )
        runCatching { dao.rebuildTasksFts() }
    }

    suspend fun upsertEntityContextWeight(weight: EntityContextWeight) = withContext(Dispatchers.IO) {
        dao.upsertEntityWeight(weight.copy(lastReferencedAt = System.currentTimeMillis()))
    }

    suspend fun deleteEntityContextWeight(entityName: String) = withContext(Dispatchers.IO) {
        dao.deleteEntityWeight(entityName)
    }

    suspend fun queryMeetingPrepBundle(rawEntityTag: String): MeetingPrepSqlBundle = withContext(Dispatchers.IO) {
        val cleanTag = rawEntityTag.removePrefix("@").removePrefix("#").trim()
        val fourteenDaysAgo = System.currentTimeMillis() - (14L * 24L * 3600L * 1000L)
        val weight = dao.getEntityWeight(cleanTag)
        val mustDo = dao.getMustDoTasksForEntity(cleanTag)
        val unblockers = dao.getBreadcrumbUnblockersForEntity(cleanTag)
        val recentCards = dao.getRecentCardsForEntity(cleanTag, fourteenDaysAgo)
        val riskCards = dao.getRiskCardsForEntity(cleanTag)
        val stalledTasks = dao.getStalledTasksForEntity(cleanTag)
        MeetingPrepSqlBundle(
            entityTag = cleanTag,
            entityWeight = weight,
            mustDoBefore = mustDo,
            yourMoveUnblockers = unblockers,
            mustKnowRecentCards = recentCards,
            watchOutRiskCards = riskCards,
            watchOutStalledTasks = stalledTasks
        )
    }

    suspend fun computeSuggestedEnergyByTimeOfDay(): Pair<EnergyBandwidthState, String> = withContext(Dispatchers.IO) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val telemetry = dao.getAllTelemetryOnce().filter { it.eventType == TelemetryEventType.ENERGY_CHECKIN }
        val bucketMatches = telemetry.filter { kotlin.math.abs(it.hourOfDay - hour) <= 2 }
        val learnedState = bucketMatches
            .groupingBy { it.subjectKey }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?.let { runCatching { EnergyBandwidthState.valueOf(it) }.getOrNull() }

        when {
            learnedState != null -> learnedState to "Learned Rhythm (${hour}:00 window): ${learnedState.emoji} ${learnedState.label}"
            hour in 6..11 -> EnergyBandwidthState.SHARP to "Morning Peak (${hour}:00): Suggesting ⚡ Sharp"
            hour in 12..15 -> EnergyBandwidthState.STEADY to "Midday Execution (${hour}:00): Suggesting 🟡 Steady"
            else -> EnergyBandwidthState.FRIED to "Late Shift / Recovery (${hour}:00): Suggesting 🔋 Fried"
        }
    }

    // --- 1-Tap Local JSON & SQLite Backup / Restore Bridge ---
    suspend fun exportFullBackupJsonString(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "Rei: Second Brain & Executive Hub")

        val cardsArr = JSONArray()
        for (c in dao.getAllCardsOnce()) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("title", c.title)
            obj.put("category", c.category.name)
            obj.put("tldrSummary", c.tldrSummary)
            obj.put("outlineSectionsJson", c.outlineSectionsJson)
            obj.put("peopleTags", ReiJsonCodec.encodeStringList(c.peopleTags))
            obj.put("topicTags", ReiJsonCodec.encodeStringList(c.topicTags))
            obj.put("hasRiskOrBlocker", c.hasRiskOrBlocker)
            obj.put("rawSourceText", c.rawSourceText)
            obj.put("createdAt", c.createdAt)
            obj.put("updatedAt", c.updatedAt)
            obj.put("isPinned", c.isPinned)
            cardsArr.put(obj)
        }
        root.put("knowledgeCards", cardsArr)

        val tasksArr = JSONArray()
        for (t in dao.getAllTasksOnce()) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            if (t.linkedCardId != null) obj.put("linkedCardId", t.linkedCardId)
            obj.put("quadrant", t.quadrant.name)
            obj.put("energyLoad", t.energyLoad.name)
            obj.put("status", t.status.name)
            obj.put("microStepsJson", t.microStepsJson)
            obj.put("breadcrumbStoppedAt", t.breadcrumbStoppedAt)
            obj.put("breadcrumbBlocker", t.breadcrumbBlocker)
            obj.put("breadcrumbResumeStep", t.breadcrumbResumeStep)
            obj.put("deferralCount", t.deferralCount)
            obj.put("peopleTags", ReiJsonCodec.encodeStringList(t.peopleTags))
            obj.put("topicTags", ReiJsonCodec.encodeStringList(t.topicTags))
            obj.put("dueDateNote", t.dueDateNote)
            obj.put("updatedAt", t.updatedAt)
            tasksArr.put(obj)
        }
        root.put("reiTasks", tasksArr)

        val entitiesArr = JSONArray()
        for (e in dao.getAllEntitiesOnce()) {
            val obj = JSONObject()
            obj.put("entityName", e.entityName)
            obj.put("entityType", e.entityType.name)
            obj.put("roleOrContext", e.roleOrContext)
            if (e.learnedQuadrantBias != null) obj.put("learnedQuadrantBias", e.learnedQuadrantBias.name)
            if (e.learnedEnergyBias != null) obj.put("learnedEnergyBias", e.learnedEnergyBias.name)
            obj.put("interactionCount", e.interactionCount)
            obj.put("lastReferencedAt", e.lastReferencedAt)
            entitiesArr.put(obj)
        }
        root.put("entityContextWeights", entitiesArr)

        val telemetryArr = JSONArray()
        for (b in dao.getAllTelemetryOnce()) {
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("eventType", b.eventType.name)
            obj.put("subjectKey", b.subjectKey)
            obj.put("detailValue", b.detailValue)
            obj.put("hourOfDay", b.hourOfDay)
            obj.put("dayOfWeek", b.dayOfWeek)
            obj.put("timestamp", b.timestamp)
            telemetryArr.put(obj)
        }
        root.put("behavioralTelemetry", telemetryArr)

        root.toString(2)
    }

    suspend fun importFullBackupJsonString(jsonString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(jsonString)
            val cardsArr = root.optJSONArray("knowledgeCards") ?: JSONArray()
            val cards = mutableListOf<KnowledgeCard>()
            for (i in 0 until cardsArr.length()) {
                val o = cardsArr.getJSONObject(i)
                cards.add(
                    KnowledgeCard(
                        id = o.optLong("id", 0L),
                        title = o.optString("title", "Untitled Card"),
                        category = runCatching { CardCategory.valueOf(o.optString("category")) }.getOrDefault(CardCategory.BRAIN_DUMP),
                        tldrSummary = o.optString("tldrSummary", ""),
                        outlineSectionsJson = o.optString("outlineSectionsJson", "[]"),
                        peopleTags = ReiJsonCodec.decodeStringList(o.optString("peopleTags", "[]")),
                        topicTags = ReiJsonCodec.decodeStringList(o.optString("topicTags", "[]")),
                        hasRiskOrBlocker = o.optBoolean("hasRiskOrBlocker", false),
                        rawSourceText = o.optString("rawSourceText", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                        isPinned = o.optBoolean("isPinned", false)
                    )
                )
            }

            val tasksArr = root.optJSONArray("reiTasks") ?: JSONArray()
            val tasks = mutableListOf<ReiTask>()
            for (i in 0 until tasksArr.length()) {
                val o = tasksArr.getJSONObject(i)
                tasks.add(
                    ReiTask(
                        id = o.optLong("id", 0L),
                        title = o.optString("title", "Untitled Task"),
                        linkedCardId = if (o.has("linkedCardId")) o.optLong("linkedCardId") else null,
                        quadrant = runCatching { TaskQuadrant.valueOf(o.optString("quadrant")) }.getOrDefault(TaskQuadrant.Q1_DO_NOW),
                        energyLoad = runCatching { EnergyLoad.valueOf(o.optString("energyLoad")) }.getOrDefault(EnergyLoad.STEADY_WORK),
                        status = runCatching { TaskStatus.valueOf(o.optString("status")) }.getOrDefault(TaskStatus.ACTIVE),
                        microStepsJson = o.optString("microStepsJson", "[]"),
                        breadcrumbStoppedAt = o.optString("breadcrumbStoppedAt", ""),
                        breadcrumbBlocker = o.optString("breadcrumbBlocker", ""),
                        breadcrumbResumeStep = o.optString("breadcrumbResumeStep", ""),
                        deferralCount = o.optInt("deferralCount", 0),
                        peopleTags = ReiJsonCodec.decodeStringList(o.optString("peopleTags", "[]")),
                        topicTags = ReiJsonCodec.decodeStringList(o.optString("topicTags", "[]")),
                        dueDateNote = o.optString("dueDateNote", "Today"),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }

            val entitiesArr = root.optJSONArray("entityContextWeights") ?: JSONArray()
            val entities = mutableListOf<EntityContextWeight>()
            for (i in 0 until entitiesArr.length()) {
                val o = entitiesArr.getJSONObject(i)
                entities.add(
                    EntityContextWeight(
                        entityName = o.optString("entityName", "Unknown"),
                        entityType = runCatching { EntityType.valueOf(o.optString("entityType")) }.getOrDefault(EntityType.PERSON),
                        roleOrContext = o.optString("roleOrContext", ""),
                        learnedQuadrantBias = o.optString("learnedQuadrantBias").takeIf { it.isNotBlank() }?.let {
                            runCatching { TaskQuadrant.valueOf(it) }.getOrNull()
                        },
                        learnedEnergyBias = o.optString("learnedEnergyBias").takeIf { it.isNotBlank() }?.let {
                            runCatching { EnergyLoad.valueOf(it) }.getOrNull()
                        },
                        interactionCount = o.optInt("interactionCount", 1),
                        lastReferencedAt = o.optLong("lastReferencedAt", System.currentTimeMillis())
                    )
                )
            }

            val telemetryArr = root.optJSONArray("behavioralTelemetry") ?: JSONArray()
            val telemetry = mutableListOf<BehavioralTelemetry>()
            for (i in 0 until telemetryArr.length()) {
                val o = telemetryArr.getJSONObject(i)
                telemetry.add(
                    BehavioralTelemetry(
                        id = o.optLong("id", 0L),
                        eventType = runCatching { TelemetryEventType.valueOf(o.optString("eventType")) }.getOrDefault(TelemetryEventType.ENERGY_CHECKIN),
                        subjectKey = o.optString("subjectKey", ""),
                        detailValue = o.optString("detailValue", ""),
                        hourOfDay = o.optInt("hourOfDay", 9),
                        dayOfWeek = o.optInt("dayOfWeek", 2),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }

            dao.restoreFullSnapshot(cards, tasks, entities, telemetry)
            "Restored ${cards.size} Knowledge Cards, ${tasks.size} ReiTasks, and ${entities.size} VIP Context Rules."
        }
    }

    suspend fun writeJsonBackupToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = exportFullBackupJsonString()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Unable to open output stream for URI")
            "Exported JSON Second Brain backup (${json.length / 1024 + 1} KB)."
        }
    }

    suspend fun readJsonBackupFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Unable to read backup file")
        }
        raw.fold(
            onSuccess = { importFullBackupJsonString(it) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun exportSqliteDatabaseToUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Checkpoint WAL so the main .db file contains all committed pages
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            val dbFile = context.getDatabasePath(ReiDatabase.DB_NAME)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(out)
                }
                out.flush()
            } ?: error("Unable to open destination URI for SQLite export")
            "Exported raw SQLite database (${dbFile.length() / 1024 + 1} KB)."
        }
    }

    suspend fun restoreSqliteDatabaseFromUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = java.io.File(context.cacheDir, "restore_temp.db")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read source SQLite file")

            // Validate SQLite header ("SQLite format 3")
            val headerBytes = ByteArray(16)
            FileInputStream(tempFile).use { it.read(headerBytes) }
            val headerString = String(headerBytes, Charsets.US_ASCII)
            if (!headerString.startsWith("SQLite format 3")) {
                tempFile.delete()
                error("Selected file is not a valid SQLite database (.db) or JSON backup.")
            }

            val sqliteDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                tempFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            sqliteDb.close()

            val dbFile = context.getDatabasePath(ReiDatabase.DB_NAME)
            FileInputStream(tempFile).use { input ->
                FileOutputStream(dbFile, false).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.delete()
            dao.rebuildCardsFts()
            dao.rebuildTasksFts()
            "Restored SQLite database snapshot and rebuilt FTS indices."
        }
    }

    suspend fun resetToExecutiveSeedData(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.restoreFullSnapshot(
            cards = ReiSeedData.buildSeedKnowledgeCards(now),
            tasks = ReiSeedData.buildSeedTasks(now),
            entities = ReiSeedData.buildSeedEntityWeights(now),
            telemetry = ReiSeedData.buildSeedTelemetry(now)
        )
        "Loaded Debug Sandbox dataset (cards, tasks, & VIP weights)."
    }

    suspend fun startFreshKeepVipRules(): String = withContext(Dispatchers.IO) {
        val keptCount = dao.getAllEntitiesOnce().size
        dao.clearCardsAndTasksKeepVipRules()
        "Start Fresh complete: Cleared demo cards & tasks while keeping $keptCount VIP Context Rules intact."
    }
}
