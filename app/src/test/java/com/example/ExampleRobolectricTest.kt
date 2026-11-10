package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiCoreGeminiNanoEngine
import com.example.ai.AiCoreSchemaRepair
import com.example.ai.LocalSkillRouter
import com.example.ai.LocalSmartSimEngine
import com.example.data.local.CommsStrategyLens
import com.example.data.local.EnergyBandwidthState
import com.example.data.local.ReiDatabase
import com.example.data.local.ReiRepository
import com.example.data.local.TaskQuadrant
import com.example.data.local.TaskStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: ReiDatabase
    private lateinit var repository: ReiRepository
    private lateinit var skillRouter: LocalSkillRouter

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ReiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReiRepository(context, database)
        val simEngine = LocalSmartSimEngine()
        val aiCoreEngine = AiCoreGeminiNanoEngine(simEngine)
        skillRouter = LocalSkillRouter(aiCoreEngine)
        repository.ensureSeedDataPopulated()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name resource matches Rei`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Rei", appName)
    }

    @Test
    fun `seed data populates KnowledgeCards ReiTasks EntityWeights and FTS4 search works`() = runBlocking {
        val allCards = repository.searchCardsInstant("")
        assertEquals(4, allCards.size)

        // Test FTS4 instant search for @Corrie and #Epic SBO
        val sboResults = repository.searchCardsInstant("Epic SBO")
        assertTrue(sboResults.isNotEmpty())
        assertTrue(sboResults.any { it.title.contains("Epic SBO") })

        // Test FTS4 instant search for Resolution Room tasks
        val taskResults = repository.searchTasksInstant("Resolution Room")
        assertTrue(taskResults.isNotEmpty())
    }

    @Test
    fun `Skill 1 Brain Dump Auto-Organizer structures raw text into KnowledgeCard and ReiTasks`() = runBlocking {
        val entities = database.reiDao().getAllEntitiesOnce()
        val rawDump = "Sync with @Corrie and @Wayne on #Epic SBO: urgent SLA risk on unmapped guarantor splits. We need to freeze legacy statements for 48 hours before Thursday cutover."

        val result = skillRouter.organizeBrainDump(
            rawText = rawDump,
            allEntities = entities,
            activeQ1Count = 2,
            energyState = EnergyBandwidthState.SHARP
        )

        assertTrue(result.card.title.isNotBlank())
        assertTrue(result.card.tldrSummary.isNotBlank())
        assertTrue(result.card.hasRiskOrBlocker)
        assertTrue(result.card.peopleTags.contains("Corrie"))
        assertTrue(result.card.peopleTags.contains("Wayne"))
        assertTrue(result.card.topicTags.contains("Epic SBO"))
        assertTrue(result.extractedTasks.isNotEmpty())

        // Verify Step 1 micro-step is < 2 minutes
        val firstTask = result.extractedTasks.first()
        assertEquals(TaskQuadrant.Q1_DO_NOW, firstTask.quadrant)
        assertTrue(firstTask.microSteps.isNotEmpty())
        assertTrue(firstTask.microSteps.first().estMinutes <= 2)

        // Save into Room and verify FTS indexing
        val savedId = repository.saveOrganizedCardAndTasks(result.card, result.extractedTasks)
        assertTrue(savedId > 0L)
        val searched = repository.searchCardsInstant("guarantor")
        assertTrue(searched.any { it.id == savedId })
    }

    @Test
    fun `Implicit Priority Learning updates EntityContextWeight bias when task quadrant changes`() = runBlocking {
        val tasks = database.reiDao().getAllTasksOnce()
        val dianaTask = tasks.first { it.peopleTags.contains("Diana") }

        val msg = repository.overrideTaskQuadrantAndLearn(dianaTask, TaskQuadrant.Q1_DO_NOW)
        assertTrue(msg.contains("Learned: Prioritizing"))

        val updatedDianaWeight = database.reiDao().getEntityWeight("Diana")
        assertNotNull(updatedDianaWeight)
        assertEquals(TaskQuadrant.Q1_DO_NOW, updatedDianaWeight?.learnedQuadrantBias)
    }

    @Test
    fun `Skill 2 Tactical Protocols execute Shrink It Breadcrumb Park It and Where Was I`() = runBlocking {
        val entities = database.reiDao().getAllEntitiesOnce()
        val tasks = database.reiDao().getAllTasksOnce()
        val targetTask = tasks.first()

        // 1. Shrink It
        val (shrunkSteps, _) = skillRouter.shrinkTaskIntoMicroSteps(
            task = targetTask,
            allEntities = entities,
            activeQ1Count = 2,
            energyState = EnergyBandwidthState.STEADY
        )
        assertEquals(3, shrunkSteps.size)
        assertTrue(shrunkSteps.first().estMinutes <= 2)

        // 2. Breadcrumb Deferral
        repository.saveBreadcrumbDeferral(
            task = targetTask,
            stoppedAt = "Completed SBO table export",
            blocker = "Waiting on @Debbie sign-off",
            resumeStep = "Ping @Debbie with 2-line summary"
        )
        val reloadedTask = database.reiDao().getTaskById(targetTask.id)
        assertEquals(TaskStatus.PAUSED_BREADCRUMB, reloadedTask?.status)
        assertEquals(targetTask.deferralCount + 1, reloadedTask?.deferralCount)

        // 3. Park It
        val parked = skillRouter.buildParkedLotTaskFromTangent(
            rawTangent = "Check with @Lauren on Q1 coaching rubric template",
            allEntities = entities
        )
        assertEquals(TaskQuadrant.Q4_PARKED_LOT, parked.quadrant)
        assertTrue(parked.peopleTags.contains("Lauren"))

        // 4. Where Was I?
        val allNow = database.reiDao().getAllTasksOnce()
        val whereWasI = skillRouter.buildWhereWasIContextReload(allNow)
        assertTrue(whereWasI.pausedBreadcrumbs.isNotEmpty())
        assertTrue(whereWasI.thirtySecondResumePrompt.isNotBlank())
    }

    @Test
    fun `Skill 3 and Skill 4 Bandwidth Matcher and Meeting Prep 4-Part SQL Bundle work`() = runBlocking {
        val entities = database.reiDao().getAllEntitiesOnce()
        val tasks = database.reiDao().getAllTasksOnce()

        val (heroSharp, _) = skillRouter.selectHeroTaskForBandwidth(tasks, EnergyBandwidthState.SHARP)
        assertNotNull(heroSharp)

        val sqlBundle = repository.queryMeetingPrepBundle("Corrie")
        assertTrue(sqlBundle.mustDoBefore.isNotEmpty())
        assertTrue(sqlBundle.mustKnowRecentCards.isNotEmpty())

        val synthesis = skillRouter.synthesizeMeetingPrepBrief(
            sqlBundle = sqlBundle,
            allEntities = entities,
            activeQ1Count = 2,
            energyState = EnergyBandwidthState.SHARP
        )
        assertTrue(synthesis.thirtySecondGoal.isNotBlank())
        assertTrue(synthesis.yourMoveAction.isNotBlank())
        assertTrue(synthesis.watchOutTrap.isNotBlank())
        assertTrue(synthesis.checklistItems.isNotEmpty())
    }

    @Test
    fun `Skill 5 Comms and Strategy Studio executes all 4 executive lenses`() = runBlocking {
        val entities = database.reiDao().getAllEntitiesOnce()
        for (lens in CommsStrategyLens.entries) {
            val out = skillRouter.runCommsStrategyStudio(
                lens = lens,
                rawInput = "Sorry to bother you @Corrie, I just wanted to see if maybe we could ring-fence Diana's 8 AR collectors.",
                allEntities = entities,
                activeQ1Count = 2,
                energyState = EnergyBandwidthState.SHARP
            )
            assertEquals(lens, out.lens)
            assertTrue(out.primaryBody.isNotBlank())
            assertTrue(out.secondaryBody.isNotBlank())
            assertTrue(out.ruleOfThumbOrStopDoing.isNotBlank())
        }
    }

    @Test
    fun `AiCoreSchemaRepair strips Markdown fences fixes trailing commas and backfills missing keys`() {
        val noisyNanoOutput = """
            ```json
            {
              "title": "S25U Nano Repaired Card",
              "hasRiskOrBlocker": true,
            }
            ```
        """.trimIndent()

        val fallbackJson = JSONObject().apply {
            put("title", "Fallback Title")
            put("tldrSummary", "Fallback 2-sentence summary.")
            put("hasRiskOrBlocker", false)
        }.toString()

        val (repairedStr, wasRepaired, notes) = AiCoreSchemaRepair.repairRawModelOutputWithNotes(
            rawText = noisyNanoOutput,
            fallbackGenerator = { fallbackJson }
        )

        assertTrue(wasRepaired)
        assertTrue(notes.isNotEmpty())
        val parsed = JSONObject(repairedStr)
        assertEquals("S25U Nano Repaired Card", parsed.getString("title"))
        assertEquals("Fallback 2-sentence summary.", parsed.getString("tldrSummary"))
        assertTrue(parsed.getBoolean("hasRiskOrBlocker"))
    }

    @Test
    fun `Start Fresh Mode clears demo cards and tasks while keeping all VIP rules and JSON backup restores`() = runBlocking {
        // Export full backup first
        val backupJson = repository.exportFullBackupJsonString()
        assertTrue(backupJson.contains("Epic SBO"))

        // Run Start Fresh Mode (clears cards & tasks, keeps VIP EntityContextWeight rules)
        val freshMsg = repository.startFreshKeepVipRules()
        assertTrue(freshMsg.contains("Start Fresh complete"))
        assertEquals(0, database.reiDao().getAllCardsOnce().size)
        assertEquals(0, database.reiDao().getAllTasksOnce().size)
        assertFalse(database.reiDao().getAllEntitiesOnce().isEmpty())

        // Restore from JSON backup and verify all cards & tasks return
        val restoreResult = repository.importFullBackupJsonString(backupJson)
        assertTrue(restoreResult.isSuccess)
        assertEquals(4, database.reiDao().getAllCardsOnce().size)
        assertEquals(6, database.reiDao().getAllTasksOnce().size)
    }

    @Test
    fun `Option C DeliverableRunwayBuilder auto-aggregates initiatives RAG rings and Smart-Gantt horizons`() = runBlocking {
        val cards = database.reiDao().getAllCardsOnce()
        val tasks = database.reiDao().getAllTasksOnce()
        val entities = database.reiDao().getAllEntitiesOnce()

        val swimlanes = com.example.ui.screens.DeliverableRunwayBuilder.buildInitiativeSwimlanes(
            tasks = tasks,
            cards = cards,
            entities = entities
        )

        assertTrue(swimlanes.isNotEmpty())
        val sboLane = swimlanes.firstOrNull { it.topicName.equals("Epic SBO", ignoreCase = true) }
        assertNotNull(sboLane)
        assertEquals(com.example.ui.screens.DeliverableRagStatus.RED_SLA_RISK, sboLane?.ragStatus)
        assertTrue(sboLane!!.nodes.isNotEmpty())
        assertTrue(sboLane.peopleInvolved.contains("Corrie"))
    }
}

