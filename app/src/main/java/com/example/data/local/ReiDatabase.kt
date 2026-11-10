package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReiDao {
    // --- Knowledge Cards & FTS Search ---
    @Query("SELECT * FROM knowledge_cards ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllCards(): Flow<List<KnowledgeCard>>

    @Query("SELECT * FROM knowledge_cards ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllCardsOnce(): List<KnowledgeCard>

    @Query("SELECT * FROM knowledge_cards WHERE id = :cardId LIMIT 1")
    suspend fun getCardById(cardId: Long): KnowledgeCard?

    @Query(
        """
        SELECT knowledge_cards.* FROM knowledge_cards
        JOIN knowledge_cards_fts ON knowledge_cards.id = knowledge_cards_fts.rowid
        WHERE knowledge_cards_fts MATCH :ftsQuery
        ORDER BY knowledge_cards.isPinned DESC, knowledge_cards.updatedAt DESC
        """
    )
    suspend fun searchCardsFts(ftsQuery: String): List<KnowledgeCard>

    @Query(
        """
        SELECT * FROM knowledge_cards
        WHERE title LIKE '%' || :rawQuery || '%'
           OR tldrSummary LIKE '%' || :rawQuery || '%'
           OR outlineSectionsJson LIKE '%' || :rawQuery || '%'
           OR peopleTags LIKE '%' || :rawQuery || '%'
           OR topicTags LIKE '%' || :rawQuery || '%'
           OR rawSourceText LIKE '%' || :rawQuery || '%'
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    suspend fun searchCardsFallback(rawQuery: String): List<KnowledgeCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: KnowledgeCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<KnowledgeCard>)

    @Update
    suspend fun updateCard(card: KnowledgeCard)

    @Query("DELETE FROM knowledge_cards WHERE id = :cardId")
    suspend fun deleteCardById(cardId: Long)

    @Query("SELECT COUNT(*) FROM knowledge_cards")
    suspend fun countCards(): Int

    // --- ReiTasks & FTS Search ---
    @Query("SELECT * FROM rei_tasks ORDER BY status ASC, updatedAt DESC")
    fun observeAllTasks(): Flow<List<ReiTask>>

    @Query("SELECT * FROM rei_tasks ORDER BY status ASC, updatedAt DESC")
    suspend fun getAllTasksOnce(): List<ReiTask>

    @Query("SELECT * FROM rei_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): ReiTask?

    @Query(
        """
        SELECT rei_tasks.* FROM rei_tasks
        JOIN rei_tasks_fts ON rei_tasks.id = rei_tasks_fts.rowid
        WHERE rei_tasks_fts MATCH :ftsQuery
        ORDER BY rei_tasks.updatedAt DESC
        """
    )
    suspend fun searchTasksFts(ftsQuery: String): List<ReiTask>

    @Query(
        """
        SELECT * FROM rei_tasks
        WHERE title LIKE '%' || :rawQuery || '%'
           OR microStepsJson LIKE '%' || :rawQuery || '%'
           OR breadcrumbBlocker LIKE '%' || :rawQuery || '%'
           OR peopleTags LIKE '%' || :rawQuery || '%'
           OR topicTags LIKE '%' || :rawQuery || '%'
        ORDER BY updatedAt DESC
        """
    )
    suspend fun searchTasksFallback(rawQuery: String): List<ReiTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ReiTask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<ReiTask>)

    @Update
    suspend fun updateTask(task: ReiTask)

    @Query("DELETE FROM rei_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: Long)

    // --- 4-Part Contextual Meeting Prep SQLite Queries ---
    // 1. MUST DO BEFORE: Open ReiTask commitments (status != DONE) tagged with person/topic
    @Query(
        """
        SELECT * FROM rei_tasks
        WHERE status != 'DONE'
          AND (peopleTags LIKE '%' || :entityTag || '%' OR topicTags LIKE '%' || :entityTag || '%')
        ORDER BY
          CASE quadrant
            WHEN 'Q1_DO_NOW' THEN 1
            WHEN 'Q2_PROTECT_BUILD' THEN 2
            WHEN 'Q3_BATCH_CONTAIN' THEN 3
            ELSE 4
          END ASC,
          updatedAt DESC
        """
    )
    suspend fun getMustDoTasksForEntity(entityTag: String): List<ReiTask>

    // 2. YOUR MOVE (Unblockers): PAUSED_BREADCRUMB tasks where blocker or tags match person/topic
    @Query(
        """
        SELECT * FROM rei_tasks
        WHERE status = 'PAUSED_BREADCRUMB'
          AND (
            breadcrumbBlocker LIKE '%' || :entityTag || '%'
            OR peopleTags LIKE '%' || :entityTag || '%'
            OR topicTags LIKE '%' || :entityTag || '%'
          )
        ORDER BY deferralCount DESC, updatedAt DESC
        """
    )
    suspend fun getBreadcrumbUnblockersForEntity(entityTag: String): List<ReiTask>

    // 3. MUST KNOW: Recent KnowledgeCard summaries and outline decisions from last 14 days
    @Query(
        """
        SELECT * FROM knowledge_cards
        WHERE createdAt >= :sinceEpochMs
          AND (peopleTags LIKE '%' || :entityTag || '%' OR topicTags LIKE '%' || :entityTag || '%')
        ORDER BY isPinned DESC, updatedAt DESC
        """
    )
    suspend fun getRecentCardsForEntity(entityTag: String, sinceEpochMs: Long): List<KnowledgeCard>

    // 4. WATCH OUT: Any cards/tasks flagged with hasRiskOrBlocker == true or high deferralCount
    @Query(
        """
        SELECT * FROM knowledge_cards
        WHERE hasRiskOrBlocker = 1
          AND (peopleTags LIKE '%' || :entityTag || '%' OR topicTags LIKE '%' || :entityTag || '%')
        ORDER BY updatedAt DESC
        """
    )
    suspend fun getRiskCardsForEntity(entityTag: String): List<KnowledgeCard>

    @Query(
        """
        SELECT * FROM rei_tasks
        WHERE status != 'DONE'
          AND deferralCount >= 2
          AND (peopleTags LIKE '%' || :entityTag || '%' OR topicTags LIKE '%' || :entityTag || '%')
        ORDER BY deferralCount DESC
        """
    )
    suspend fun getStalledTasksForEntity(entityTag: String): List<ReiTask>

    // --- Entity Context Weight (VIP & Priority Learning Table) ---
    @Query("SELECT * FROM entity_context_weights ORDER BY interactionCount DESC, entityName ASC")
    fun observeAllEntities(): Flow<List<EntityContextWeight>>

    @Query("SELECT * FROM entity_context_weights ORDER BY interactionCount DESC, entityName ASC")
    suspend fun getAllEntitiesOnce(): List<EntityContextWeight>

    @Query("SELECT * FROM entity_context_weights WHERE entityName = :name LIMIT 1")
    suspend fun getEntityWeight(name: String): EntityContextWeight?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntityWeight(weight: EntityContextWeight)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntityWeights(weights: List<EntityContextWeight>)

    @Query("DELETE FROM entity_context_weights WHERE entityName = :name")
    suspend fun deleteEntityWeight(name: String)

    // --- Behavioral Telemetry (Energy Rhythm & Priority Override Log) ---
    @Query("SELECT * FROM behavioral_telemetry ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentTelemetry(): Flow<List<BehavioralTelemetry>>

    @Query("SELECT * FROM behavioral_telemetry ORDER BY timestamp DESC")
    suspend fun getAllTelemetryOnce(): List<BehavioralTelemetry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(event: BehavioralTelemetry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetryList(events: List<BehavioralTelemetry>)

    // --- Full Snapshot Replace for Local Backup & Restore ---
    @Query("DELETE FROM knowledge_cards")
    suspend fun clearAllCards()

    @Query("DELETE FROM rei_tasks")
    suspend fun clearAllTasks()

    @Query("DELETE FROM entity_context_weights")
    suspend fun clearAllEntityWeights()

    @Query("DELETE FROM behavioral_telemetry")
    suspend fun clearAllTelemetry()

    @Query("INSERT INTO knowledge_cards_fts(knowledge_cards_fts) VALUES('rebuild')")
    suspend fun rebuildCardsFts()

    @Query("INSERT INTO rei_tasks_fts(rei_tasks_fts) VALUES('rebuild')")
    suspend fun rebuildTasksFts()

    @Transaction
    suspend fun restoreFullSnapshot(
        cards: List<KnowledgeCard>,
        tasks: List<ReiTask>,
        entities: List<EntityContextWeight>,
        telemetry: List<BehavioralTelemetry>
    ) {
        clearAllCards()
        clearAllTasks()
        clearAllEntityWeights()
        clearAllTelemetry()
        insertCards(cards)
        insertTasks(tasks)
        upsertEntityWeights(entities)
        insertTelemetryList(telemetry)
        rebuildCardsFts()
        rebuildTasksFts()
    }

    @Transaction
    suspend fun clearCardsAndTasksKeepVipRules() {
        clearAllCards()
        clearAllTasks()
        rebuildCardsFts()
        rebuildTasksFts()
    }
}

@Database(
    entities = [
        KnowledgeCard::class,
        KnowledgeCardFts::class,
        ReiTask::class,
        ReiTaskFts::class,
        EntityContextWeight::class,
        BehavioralTelemetry::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(ReiTypeConverters::class)
abstract class ReiDatabase : RoomDatabase() {
    abstract fun reiDao(): ReiDao

    companion object {
        const val DB_NAME = "rei_second_brain.db"

        @Volatile
        private var INSTANCE: ReiDatabase? = null

        fun getInstance(context: Context): ReiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReiDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object ReiSeedData {
    fun buildSeedEntityWeights(now: Long): List<EntityContextWeight> = listOf(
        EntityContextWeight(
            entityName = "Corrie",
            entityType = EntityType.PERSON,
            roleOrContext = "VP Financial Services • Executive Sponsor for Revenue Cycle & AR Strategy",
            learnedQuadrantBias = TaskQuadrant.Q1_DO_NOW,
            learnedEnergyBias = EnergyLoad.DEEP_WORK,
            interactionCount = 18,
            lastReferencedAt = now - 3_600_000L
        ),
        EntityContextWeight(
            entityName = "Debbie",
            entityType = EntityType.PERSON,
            roleOrContext = "AVP Revenue Cycle • Epic SBO Governance & Escalation Sign-Off",
            learnedQuadrantBias = TaskQuadrant.Q1_DO_NOW,
            learnedEnergyBias = EnergyLoad.DEEP_WORK,
            interactionCount = 15,
            lastReferencedAt = now - 5_400_000L
        ),
        EntityContextWeight(
            entityName = "Wayne",
            entityType = EntityType.PERSON,
            roleOrContext = "Director of Contact Center Ops • Queue SLA, Telephony & WFM Capacity",
            learnedQuadrantBias = TaskQuadrant.Q2_PROTECT_BUILD,
            learnedEnergyBias = EnergyLoad.STEADY_WORK,
            interactionCount = 14,
            lastReferencedAt = now - 7_200_000L
        ),
        EntityContextWeight(
            entityName = "Lauren",
            entityType = EntityType.PERSON,
            roleOrContext = "Co-Manager • Frontline Coaching, Resolution Room Triage & Broadcast Sync",
            learnedQuadrantBias = TaskQuadrant.Q2_PROTECT_BUILD,
            learnedEnergyBias = EnergyLoad.STEADY_WORK,
            interactionCount = 12,
            lastReferencedAt = now - 9_000_000L
        ),
        EntityContextWeight(
            entityName = "Diana",
            entityType = EntityType.PERSON,
            roleOrContext = "Sr. Manager Specialized Servicing • High-Balance AR & Complex Disputes",
            learnedQuadrantBias = TaskQuadrant.Q3_BATCH_CONTAIN,
            learnedEnergyBias = EnergyLoad.STEADY_WORK,
            interactionCount = 10,
            lastReferencedAt = now - 14_400_000L
        ),
        EntityContextWeight(
            entityName = "Epic SBO",
            entityType = EntityType.PROJECT,
            roleOrContext = "Single Billing Office Cutover • Self-Pay Routing & Statement Suppression",
            learnedQuadrantBias = TaskQuadrant.Q1_DO_NOW,
            learnedEnergyBias = EnergyLoad.DEEP_WORK,
            interactionCount = 21,
            lastReferencedAt = now - 1_800_000L
        ),
        EntityContextWeight(
            entityName = "Resolution Room",
            entityType = EntityType.PROJECT,
            roleOrContext = "Real-Time Escalation Swarm • Tier-2 Billing & Hold-Time Deflection Protocol",
            learnedQuadrantBias = TaskQuadrant.Q1_DO_NOW,
            learnedEnergyBias = EnergyLoad.STEADY_WORK,
            interactionCount = 16,
            lastReferencedAt = now - 4_200_000L
        ),
        EntityContextWeight(
            entityName = "Teams Broadcast",
            entityType = EntityType.DOMAIN,
            roleOrContext = "Floor-Wide Comms Governance • Shift Alerts & Zero-Noise Protocol",
            learnedQuadrantBias = TaskQuadrant.Q3_BATCH_CONTAIN,
            learnedEnergyBias = EnergyLoad.LOW_DRAIN,
            interactionCount = 9,
            lastReferencedAt = now - 18_000_000L
        ),
        EntityContextWeight(
            entityName = "AR Goals",
            entityType = EntityType.DOMAIN,
            roleOrContext = "Q4 Cash Collections, >90-Day Aging Reduction & Point-of-Service KPIs",
            learnedQuadrantBias = TaskQuadrant.Q2_PROTECT_BUILD,
            learnedEnergyBias = EnergyLoad.DEEP_WORK,
            interactionCount = 13,
            lastReferencedAt = now - 10_800_000L
        )
    )

    fun buildSeedKnowledgeCards(now: Long): List<KnowledgeCard> = listOf(
        KnowledgeCard(
            id = 1L,
            title = "Epic SBO Cutover: Statement Hold & Self-Pay Routing Architecture",
            category = CardCategory.MEETING_RECORD,
            tldrSummary = "Corrie and Debbie aligned on freezing legacy guarantors 48 hours prior to Epic SBO wave-2 activation to prevent duplicate billing statements. Contact Center needs a 1-page warm-transfer decision tree before Thursday's dry run.",
            outlineSectionsJson = ReiJsonCodec.encodeOutlineSections(
                listOf(
                    OutlineSection(
                        heading = "Executive Decisions & Guardrails",
                        bullets = listOf(
                            "Suppress automated statement batching for legacy accounts during the 48-hour cutover window.",
                            "Route all disputed balances > $1,500 directly to Diana's Specialized Servicing pod instead of Tier-1 queues.",
                            "Corrie requested a daily 8:30 AM 5-bullet flash brief showing SBO call arrival vs. abandonment rate."
                        ),
                        annotation = "SLA Risk: Unmapped guarantor splits could spike Tier-1 AHT by 90+ seconds if routing rules slip."
                    ),
                    OutlineSection(
                        heading = "Operational Readiness & Dependencies",
                        bullets = listOf(
                            "Wayne's WFM analysts are modeling a 14% volume surge in week 1 post-go-live.",
                            "Lauren is finalizing the 3-question intake script for frontline reps before warm-transferring to Resolution Room."
                        ),
                        annotation = "Decision Owner: @Debbie signs off on final cutover checklist at 3:00 PM Wednesday."
                    )
                )
            ),
            peopleTags = listOf("Corrie", "Debbie", "Wayne", "Lauren", "Diana"),
            topicTags = listOf("Epic SBO", "Resolution Room", "AR Goals"),
            hasRiskOrBlocker = true,
            rawSourceText = "Sync with Corrie, Debbie, and Wayne on Epic SBO wave 2. Need to freeze legacy statements 48h prior so patients don't get double billed. Disputed balances over 1500 go to Diana's pod. Corrie wants an 8:30am 5-bullet flash brief. SLA risk on unmapped guarantor splits.",
            createdAt = now - 7_200_000L,
            updatedAt = now - 3_600_000L,
            isPinned = true
        ),
        KnowledgeCard(
            id = 2L,
            title = "Resolution Room Swarm Protocol & Teams Broadcast Hygiene",
            category = CardCategory.SOP_DECISION,
            tldrSummary = "Restructured the Resolution Room intake workflow with Lauren and Diana to stop unvetted @here pings in the main floor Teams Broadcast channel. Tier-1 agents must now log claim ID, guarantor DOB, and exact denial code before escalating.",
            outlineSectionsJson = ReiJsonCodec.encodeOutlineSections(
                listOf(
                    OutlineSection(
                        heading = "3-Field Mandatory Escalation Gate",
                        bullets = listOf(
                            "Every Resolution Room ticket requires: (1) Epic Account/HAR ID, (2) Verified Payer/SBO Bucket, (3) Specific Blocker.",
                            "Incomplete escalations are auto-returned with a 1-tap coaching macro rather than clogging live swarm threads."
                        ),
                        annotation = "Reduces floor noise by ~42% and protects Specialized Servicing focus blocks."
                    ),
                    OutlineSection(
                        heading = "Teams Broadcast Channel Governance",
                        bullets = listOf(
                            "Restrict floor-wide @channel alerts to P1 system outages, telephony routing drops, or SBO batch holds.",
                            "Lauren and Co-Managers will publish a consolidated 2:00 PM operational digest instead of ad-hoc thread pings."
                        ),
                        annotation = null
                    )
                )
            ),
            peopleTags = listOf("Lauren", "Diana", "Wayne"),
            topicTags = listOf("Resolution Room", "Teams Broadcast", "Epic SBO"),
            hasRiskOrBlocker = false,
            rawSourceText = "SOP update with Lauren and Diana: stop unvetted pings in Teams Broadcast. Resolution Room escalations now require HAR ID, SBO bucket, and exact blocker. Consolidate shift updates into a 2pm digest.",
            createdAt = now - 86_400_000L,
            updatedAt = now - 43_200_000L,
            isPinned = true
        ),
        KnowledgeCard(
            id = 3L,
            title = "Q4 AR Aging Velocity & Contact Center Capacity Trade-Offs",
            category = CardCategory.BRAIN_DUMP,
            tldrSummary = "Corrie needs the Q4 >90-day AR recovery bridge before Friday's VP review while inbound SBO inquiries are pulling 6 FTEs off outbound follow-ups. We need to protect Diana's high-balance outbound pod from inbound queue overflow.",
            outlineSectionsJson = ReiJsonCodec.encodeOutlineSections(
                listOf(
                    OutlineSection(
                        heading = "Capacity vs. Cash Collection Tension",
                        bullets = listOf(
                            "Blending Diana's specialized AR collectors into general inbound phone queues drops daily cash yield by $38K.",
                            "Proposed fix: Ring-fence 8 dedicated AR specialists from 8:00 AM–1:00 PM and use callback automation for Tier-1 spikes."
                        ),
                        annotation = "Blocker: Waiting on Wayne's Genesys/IVR skill-group ring-fence confirmation."
                    ),
                    OutlineSection(
                        heading = "Executive Narrative for Corrie & Debbie",
                        bullets = listOf(
                            "Frame the ring-fence not as headcount protection, but as a $750K/month Q4 net revenue preservation lever.",
                            "Pair with self-service SMS payment link adoption metrics from Epic SBO."
                        ),
                        annotation = null
                    )
                )
            ),
            peopleTags = listOf("Corrie", "Debbie", "Diana", "Wayne"),
            topicTags = listOf("AR Goals", "Epic SBO"),
            hasRiskOrBlocker = true,
            rawSourceText = "Brain dump on Q4 AR goals: Corrie needs the 90-day aging bridge by Friday. Blending Diana's pod into general inbound queues kills our cash yield. Need Wayne to ring-fence 8 collectors in IVR from 8am-1pm.",
            createdAt = now - 172_800_000L,
            updatedAt = now - 64_800_000L,
            isPinned = false
        ),
        KnowledgeCard(
            id = 4L,
            title = "Leadership Check-In: Frontline Morale & Co-Manager Cadence",
            category = CardCategory.QUICK_NOTE,
            tldrSummary = "Quick 1:1 alignment with Lauren on dividing weekly supervisor huddles so Epic SBO defect tracking doesn't crowd out QA coaching. Agreed to split Tuesday's 45-minute sync into a 15-minute metrics pulse and 30-minute barrier removal.",
            outlineSectionsJson = ReiJsonCodec.encodeOutlineSections(
                listOf(
                    OutlineSection(
                        heading = "Co-Manager Operating Rhythm",
                        bullets = listOf(
                            "Lauren owns Monday/Wednesday frontline calibration and QA rubric spot-checks.",
                            "Executive Hub owns Tuesday/Thursday cross-functional escalation clearing with Debbie and IT build leads."
                        ),
                        annotation = null
                    )
                )
            ),
            peopleTags = listOf("Lauren", "Debbie"),
            topicTags = listOf("Resolution Room", "Teams Broadcast"),
            hasRiskOrBlocker = false,
            rawSourceText = "1:1 with Lauren: split Tuesday huddle into 15m metrics pulse and 30m barrier removal. Lauren takes Mon/Wed QA spot checks, I'll own Tue/Thu cross-functional SBO escalation clearing with Debbie.",
            createdAt = now - 259_200_000L,
            updatedAt = now - 129_600_000L,
            isPinned = false
        )
    )

    fun buildSeedTasks(now: Long): List<ReiTask> = listOf(
        ReiTask(
            id = 101L,
            title = "Finalize Epic SBO Wave-2 5-Bullet Executive Flash Brief for Corrie & Debbie",
            linkedCardId = 1L,
            quadrant = TaskQuadrant.Q1_DO_NOW,
            energyLoad = EnergyLoad.DEEP_WORK,
            status = TaskStatus.ACTIVE,
            microStepsJson = ReiJsonCodec.encodeMicroSteps(
                listOf(
                    MicroStep("s1", "Open yesterday's SBO call-arrival export & paste top 3 numbers (< 90 sec)", isCompleted = true, estMinutes = 1),
                    MicroStep("s2", "Write the 2-sentence guarantor hold guardrail & SLA impact line", isCompleted = false, estMinutes = 2),
                    MicroStep("s3", "Run '99% Check' & drop into Corrie/Debbie executive thread before 8:30 AM", isCompleted = false, estMinutes = 2)
                )
            ),
            deferralCount = 0,
            peopleTags = listOf("Corrie", "Debbie"),
            topicTags = listOf("Epic SBO", "AR Goals"),
            dueDateNote = "Today • Hard 8:30 AM Executive Sync",
            updatedAt = now - 1_200_000L
        ),
        ReiTask(
            id = 102L,
            title = "Lock IVR Skill-Group Ring-Fence for Diana's 8 Specialized AR Collectors",
            linkedCardId = 3L,
            quadrant = TaskQuadrant.Q1_DO_NOW,
            energyLoad = EnergyLoad.STEADY_WORK,
            status = TaskStatus.PAUSED_BREADCRUMB,
            microStepsJson = ReiJsonCodec.encodeMicroSteps(
                listOf(
                    MicroStep("s1", "Open Genesys queue threshold table row 14 (Specialized Servicing)", isCompleted = true, estMinutes = 1),
                    MicroStep("s2", "Confirm overflow timer change from 60s -> 300s with Wayne", isCompleted = false, estMinutes = 2),
                    MicroStep("s3", "Send confirmation note to Diana & update Q4 AR capacity tracker", isCompleted = false, estMinutes = 2)
                )
            ),
            breadcrumbStoppedAt = "Drafted the 8:00 AM–1:00 PM skill-group ring-fence rules for the 8 high-balance collectors",
            breadcrumbBlocker = "Waiting on @Wayne to confirm Tuesday peak telephony abandonment buffer",
            breadcrumbResumeStep = "Ping @Wayne with the 1-line threshold table screenshot and ask for thumbs-up",
            deferralCount = 2,
            peopleTags = listOf("Wayne", "Diana", "Corrie"),
            topicTags = listOf("AR Goals", "Epic SBO"),
            dueDateNote = "Today • Stalled 2x (Needs Unblock)",
            updatedAt = now - 2_400_000L
        ),
        ReiTask(
            id = 103L,
            title = "Publish Resolution Room 3-Field Mandatory Intake Gate & Coaching Macro",
            linkedCardId = 2L,
            quadrant = TaskQuadrant.Q2_PROTECT_BUILD,
            energyLoad = EnergyLoad.STEADY_WORK,
            status = TaskStatus.ACTIVE,
            microStepsJson = ReiJsonCodec.encodeMicroSteps(
                listOf(
                    MicroStep("s1", "Copy the 3-field intake template (HAR ID / SBO Bucket / Blocker) into pinned header (< 90 sec)", isCompleted = true, estMinutes = 1),
                    MicroStep("s2", "Review auto-return coaching macro phrasing with Lauren", isCompleted = false, estMinutes = 2),
                    MicroStep("s3", "Pin SOP card in Resolution Room channel & archive legacy thread", isCompleted = false, estMinutes = 2)
                )
            ),
            deferralCount = 0,
            peopleTags = listOf("Lauren", "Diana"),
            topicTags = listOf("Resolution Room", "Teams Broadcast"),
            dueDateNote = "Thu 2:00 PM Rollout",
            updatedAt = now - 5_400_000L
        ),
        ReiTask(
            id = 104L,
            title = "Architect Q4 >90-Day AR Recovery ROI Bridge Slide for VP Review",
            linkedCardId = 3L,
            quadrant = TaskQuadrant.Q2_PROTECT_BUILD,
            energyLoad = EnergyLoad.DEEP_WORK,
            status = TaskStatus.ACTIVE,
            microStepsJson = ReiJsonCodec.encodeMicroSteps(
                listOf(
                    MicroStep("s1", "Create blank 3-column table: Baseline Aging | SBO Self-Service Lift | Ring-Fence Yield (< 2 min)", isCompleted = false, estMinutes = 2),
                    MicroStep("s2", "Plug in $38K/day collector protection delta from Diana's pod tracker", isCompleted = false, estMinutes = 3),
                    MicroStep("s3", "Run 'Draft It -> Executive Positioning ROI' to sharpen headline for Corrie", isCompleted = false, estMinutes = 2)
                )
            ),
            deferralCount = 3,
            peopleTags = listOf("Corrie", "Debbie", "Diana"),
            topicTags = listOf("AR Goals", "Epic SBO"),
            dueDateNote = "Fri VP Review • Stalled 3x",
            updatedAt = now - 7_800_000L
        ),
        ReiTask(
            id = 105L,
            title = "Consolidate Floor Shift Updates into 2:00 PM Teams Broadcast Digest",
            linkedCardId = 2L,
            quadrant = TaskQuadrant.Q3_BATCH_CONTAIN,
            energyLoad = EnergyLoad.LOW_DRAIN,
            status = TaskStatus.ACTIVE,
            microStepsJson = ReiJsonCodec.encodeMicroSteps(
                listOf(
                    MicroStep("s1", "Grab the 3 open shift notes from Co-Manager scratchpad (< 60 sec)", isCompleted = false, estMinutes = 1),
                    MicroStep("s2", "Format into 3 crisp bullets under Lauren's 2:00 PM broadcast header", isCompleted = false, estMinutes = 2),
                    MicroStep("s3", "Post digest & mute ad-hoc reply notifications for 2 hours", isCompleted = false, estMinutes = 1)
                )
            ),
            deferralCount = 0,
            peopleTags = listOf("Lauren"),
            topicTags = listOf("Teams Broadcast", "Resolution Room"),
            dueDateNote = "Daily 2:00 PM Batch",
            updatedAt = now - 10_000_000L
        ),
        ReiTask(
            id = 106L,
            title = "Explore Automated SBO Statement Hold Audit Script for Q1 Roadmap",
            linkedCardId = 1L,
            quadrant = TaskQuadrant.Q4_PARKED_LOT,
            energyLoad = EnergyLoad.LOW_DRAIN,
            status = TaskStatus.ACTIVE,
            microStepsJson = ReiJsonCodec.encodeMicroSteps(
                listOf(
                    MicroStep("s1", "Jot 3 criteria for flagging duplicate guarantor addresses (< 90 sec)", isCompleted = false, estMinutes = 1),
                    MicroStep("s2", "Tag IT reporting lead for post-cutover backlog grooming", isCompleted = false, estMinutes = 2),
                    MicroStep("s3", "Revisit after Wave-2 SBO stabilization", isCompleted = false, estMinutes = 2)
                )
            ),
            deferralCount = 1,
            peopleTags = listOf("Debbie", "Wayne"),
            topicTags = listOf("Epic SBO"),
            dueDateNote = "Parked • Post-Cutover",
            updatedAt = now - 25_000_000L
        )
    )

    fun buildSeedTelemetry(now: Long): List<BehavioralTelemetry> = listOf(
        BehavioralTelemetry(
            eventType = TelemetryEventType.ENERGY_CHECKIN,
            subjectKey = "SHARP",
            detailValue = "Morning Peak Architecture Window (8:00 AM - 11:30 AM)",
            hourOfDay = 9,
            dayOfWeek = 3,
            timestamp = now - 86_400_000L
        ),
        BehavioralTelemetry(
            eventType = TelemetryEventType.ENERGY_CHECKIN,
            subjectKey = "STEADY",
            detailValue = "Midday Operational Sync Window (12:00 PM - 3:30 PM)",
            hourOfDay = 14,
            dayOfWeek = 3,
            timestamp = now - 68_400_000L
        ),
        BehavioralTelemetry(
            eventType = TelemetryEventType.ENERGY_CHECKIN,
            subjectKey = "FRIED",
            detailValue = "Late Afternoon Quick-Win & Batch Triage (4:00 PM+)",
            hourOfDay = 16,
            dayOfWeek = 3,
            timestamp = now - 57_600_000L
        ),
        BehavioralTelemetry(
            eventType = TelemetryEventType.QUADRANT_OVERRIDE,
            subjectKey = "Corrie",
            detailValue = "Promoted @Corrie & #Epic SBO deliverables to Q1_DO_NOW",
            hourOfDay = 9,
            dayOfWeek = 4,
            timestamp = now - 7_200_000L
        ),
        BehavioralTelemetry(
            eventType = TelemetryEventType.BREADCRUMB_CREATED,
            subjectKey = "Wayne",
            detailValue = "Paused IVR Ring-Fence task waiting on @Wayne telephony buffer",
            hourOfDay = 11,
            dayOfWeek = 4,
            timestamp = now - 2_400_000L
        )
    )
}
