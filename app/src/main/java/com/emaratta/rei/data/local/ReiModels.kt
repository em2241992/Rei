package com.emaratta.rei.data.local

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

enum class AiBackendMode(
    val title: String,
    val shortBadge: String,
    val description: String
) {
    AICORE_NANO(
        title = "On-Device Gemini Nano (AICore / .litertlm)",
        shortBadge = "Nano AICore",
        description = "Hardware NPU via ML Kit GenAI Prompt API or local .litertlm bundle (falls back cleanly if unavailable)"
    ),
    LOCAL_LOOPBACK_LAN(
        title = "Local Loopback / LAN (Ollama / OpenAI HTTP)",
        shortBadge = "Local LAN",
        description = "Connects via OkHttp to local Ollama or OpenAI-compatible server (e.g. http://127.0.0.1:11434)"
    ),
    CLOUD_GEMINI_REST(
        title = "Cloud Gemini REST API",
        shortBadge = "Cloud Gemini",
        description = "Direct HTTPS calls to generativelanguage.googleapis.com with your API key & selected model"
    ),
    OFFLINE_HEURISTIC(
        title = "Offline Deterministic Heuristic",
        shortBadge = "Offline Rule",
        description = "100% air-gapped instant local heuristic decomposition + learned entity weights"
    )
}

data class AiBackendConfig(
    val mode: AiBackendMode = AiBackendMode.AICORE_NANO,
    val litertModelUri: String = "",
    val litertModelName: String = "",
    val localEndpointUrl: String = "http://127.0.0.1:11434",
    val localModelName: String = "llama3.2",
    val cloudGeminiApiKey: String = "",
    val cloudGeminiModel: String = "gemini-2.5-flash"
)

enum class CardCategory(val label: String, val badgeCode: String) {
    MEETING_RECORD("Meeting Record", "MTG"),
    BRAIN_DUMP("Brain Dump", "DUMP"),
    SOP_DECISION("SOP / Decision", "SOP"),
    QUICK_NOTE("Quick Note", "NOTE")
}

enum class TaskQuadrant(
    val shortCode: String,
    val title: String,
    val subtitle: String
) {
    Q1_DO_NOW("Q1", "Q1 • Do Now", "Urgent & High-Impact Execution"),
    Q2_PROTECT_BUILD("Q2", "Q2 • Protect & Build", "Strategic Systems & Architecture"),
    Q3_BATCH_CONTAIN("Q3", "Q3 • Batch & Contain", "Operational Alignment & Follow-ups"),
    Q4_PARKED_LOT("Q4", "Q4 • Parked Lot", "Stashed Tangents & Someday Ideas")
}

enum class EnergyLoad(val label: String, val badge: String) {
    DEEP_WORK("Deep Architecture", "⚡ Deep Work"),
    STEADY_WORK("Steady Execution", "🟡 Steady Flow"),
    LOW_DRAIN("Quick Win / Low Drain", "🔋 Low Drain")
}

enum class TaskStatus(val label: String) {
    ACTIVE("Active"),
    PAUSED_BREADCRUMB("Paused w/ Breadcrumb"),
    VERIFY_99("99% Finish Check"),
    DONE("Completed")
}

enum class EntityType(val prefix: String, val label: String) {
    PERSON("@", "Stakeholder / Leader"),
    PROJECT("#", "Strategic Initiative"),
    DOMAIN("#", "Operational Domain")
}

enum class TelemetryEventType(val label: String) {
    QUADRANT_OVERRIDE("Quadrant Priority Override"),
    ENERGY_CHECKIN("Bandwidth Check-In"),
    TASK_STALL_SHRINK("Stall Decomposed (Shrink It)"),
    BREADCRUMB_CREATED("Breadcrumb Context Saved")
}

enum class EnergyBandwidthState(
    val label: String,
    val emoji: String,
    val targetLoad: EnergyLoad,
    val description: String
) {
    SHARP("Peak", "⚡", EnergyLoad.DEEP_WORK, "Peak cognitive bandwidth — tackle urgent Q1/Q2 architecture"),
    STEADY("Standard", "●", EnergyLoad.STEADY_WORK, "Standard operational rhythm — steady execution & syncs"),
    FRIED("Quick Win", "🟢", EnergyLoad.LOW_DRAIN, "Low energy override — bypass matrix & surface easiest 1-2m quick win")
}

enum class CommsStrategyLens(val title: String, val subtitle: String) {
    GROUNDED_COMMS("Grounded Communication", "Audit defensive reflexes • Collaborative vs. Crisp BLUF"),
    PARETO_80_20("Pareto 80/20 Strategist", "Root Cause • Control Audit • 20% High-Leverage Move"),
    EXEC_POSITIONING_ROI("Exec Positioning: ROI Brief", "Mode A • Translate ops wins into Revenue, Capacity & Speed"),
    EXEC_POSITIONING_CLINICAL("Exec Positioning: Paper-Trail", "Mode B • Clinical, zero-friction workflow gap documentation")
}

data class OutlineSection(
    val heading: String,
    val bullets: List<String>,
    val annotation: String? = null
)

data class MicroStep(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false,
    val estMinutes: Int = 2
)

@Entity(
    tableName = "knowledge_cards",
    indices = [
        Index(value = ["isPinned", "updatedAt"]),
        Index(value = ["createdAt"]),
        Index(value = ["hasRiskOrBlocker"]),
        Index(value = ["category"])
    ]
)
data class KnowledgeCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: CardCategory = CardCategory.BRAIN_DUMP,
    val tldrSummary: String,
    val outlineSectionsJson: String,
    val peopleTags: List<String> = emptyList(),
    val topicTags: List<String> = emptyList(),
    val hasRiskOrBlocker: Boolean = false,
    val rawSourceText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
) {
    val outlineSections: List<OutlineSection>
        get() = ReiJsonCodec.decodeOutlineSections(outlineSectionsJson)
}

@Fts4(contentEntity = KnowledgeCard::class)
@Entity(tableName = "knowledge_cards_fts")
data class KnowledgeCardFts(
    val title: String,
    val tldrSummary: String,
    val outlineSectionsJson: String,
    val peopleTags: List<String>,
    val topicTags: List<String>,
    val rawSourceText: String
)

@Entity(
    tableName = "rei_tasks",
    indices = [
        Index(value = ["status", "updatedAt"]),
        Index(value = ["quadrant"]),
        Index(value = ["linkedCardId"]),
        Index(value = ["deferralCount"])
    ]
)
data class ReiTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val linkedCardId: Long? = null,
    val quadrant: TaskQuadrant = TaskQuadrant.Q1_DO_NOW,
    val energyLoad: EnergyLoad = EnergyLoad.STEADY_WORK,
    val status: TaskStatus = TaskStatus.ACTIVE,
    val microStepsJson: String,
    val breadcrumbStoppedAt: String = "",
    val breadcrumbBlocker: String = "",
    val breadcrumbResumeStep: String = "",
    val deferralCount: Int = 0,
    val peopleTags: List<String> = emptyList(),
    val topicTags: List<String> = emptyList(),
    val dueDateNote: String = "Today",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val microSteps: List<MicroStep>
        get() = ReiJsonCodec.decodeMicroSteps(microStepsJson)

    val isStalled: Boolean
        get() = deferralCount >= 2 && status != TaskStatus.DONE
}

@Fts4(contentEntity = ReiTask::class)
@Entity(tableName = "rei_tasks_fts")
data class ReiTaskFts(
    val title: String,
    val microStepsJson: String,
    val breadcrumbStoppedAt: String,
    val breadcrumbBlocker: String,
    val breadcrumbResumeStep: String,
    val peopleTags: List<String>,
    val topicTags: List<String>,
    val dueDateNote: String
)

@Entity(
    tableName = "entity_context_weights",
    indices = [
        Index(value = ["interactionCount"]),
        Index(value = ["lastReferencedAt"])
    ]
)
data class EntityContextWeight(
    @PrimaryKey val entityName: String,
    val entityType: EntityType,
    val roleOrContext: String,
    val learnedQuadrantBias: TaskQuadrant? = null,
    val learnedEnergyBias: EnergyLoad? = null,
    val interactionCount: Int = 1,
    val lastReferencedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "behavioral_telemetry",
    indices = [
        Index(value = ["eventType"]),
        Index(value = ["timestamp"])
    ]
)
data class BehavioralTelemetry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: TelemetryEventType,
    val subjectKey: String,
    val detailValue: String,
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val timestamp: Long = System.currentTimeMillis()
)

object ReiJsonCodec {
    fun encodeOutlineSections(sections: List<OutlineSection>): String {
        val arr = JSONArray()
        for (section in sections) {
            val obj = JSONObject()
            obj.put("heading", section.heading)
            val bulletsArr = JSONArray()
            section.bullets.forEach { bulletsArr.put(it) }
            obj.put("bullets", bulletsArr)
            if (!section.annotation.isNullOrBlank()) {
                obj.put("annotation", section.annotation)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeOutlineSections(json: String): List<OutlineSection> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<OutlineSection>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val heading = obj.optString("heading", "Key Points")
                val bulletsArr = obj.optJSONArray("bullets") ?: JSONArray()
                val bullets = mutableListOf<String>()
                for (j in 0 until bulletsArr.length()) {
                    val b = bulletsArr.optString(j)
                    if (b.isNotBlank()) bullets.add(b)
                }
                val annotation = obj.optString("annotation", "").takeIf { it.isNotBlank() }
                list.add(OutlineSection(heading = heading, bullets = bullets, annotation = annotation))
            }
            list
        } catch (_: Exception) {
            listOf(OutlineSection("Captured Notes", listOf(json)))
        }
    }

    fun encodeMicroSteps(steps: List<MicroStep>): String {
        val arr = JSONArray()
        for (step in steps) {
            val obj = JSONObject()
            obj.put("id", step.id)
            obj.put("text", step.text)
            obj.put("isCompleted", step.isCompleted)
            obj.put("estMinutes", step.estMinutes)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeMicroSteps(json: String): List<MicroStep> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<MicroStep>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    MicroStep(
                        id = obj.optString("id", "step_${i + 1}"),
                        text = obj.optString("text", ""),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        estMinutes = obj.optInt("estMinutes", 2)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeStringList(list: List<String>): String {
        val arr = JSONArray()
        list.distinct().forEach { if (it.isNotBlank()) arr.put(it.trim()) }
        return arr.toString()
    }

    fun decodeStringList(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotBlank()) out.add(item)
            }
            out
        } catch (_: Exception) {
            raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
    }
}

class ReiTypeConverters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String =
        ReiJsonCodec.encodeStringList(value ?: emptyList())

    @TypeConverter
    fun toStringList(value: String?): List<String> =
        ReiJsonCodec.decodeStringList(value ?: "")

    @TypeConverter
    fun fromCardCategory(value: CardCategory?): String =
        (value ?: CardCategory.BRAIN_DUMP).name

    @TypeConverter
    fun toCardCategory(value: String?): CardCategory =
        runCatching { CardCategory.valueOf(value ?: "") }.getOrDefault(CardCategory.BRAIN_DUMP)

    @TypeConverter
    fun fromTaskQuadrant(value: TaskQuadrant?): String? = value?.name

    @TypeConverter
    fun toTaskQuadrant(value: String?): TaskQuadrant? =
        if (value.isNullOrBlank()) null else runCatching { TaskQuadrant.valueOf(value) }.getOrNull()

    @TypeConverter
    fun fromEnergyLoad(value: EnergyLoad?): String? = value?.name

    @TypeConverter
    fun toEnergyLoad(value: String?): EnergyLoad? =
        if (value.isNullOrBlank()) null else runCatching { EnergyLoad.valueOf(value) }.getOrNull()

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus?): String =
        (value ?: TaskStatus.ACTIVE).name

    @TypeConverter
    fun toTaskStatus(value: String?): TaskStatus =
        runCatching { TaskStatus.valueOf(value ?: "") }.getOrDefault(TaskStatus.ACTIVE)

    @TypeConverter
    fun fromEntityType(value: EntityType?): String =
        (value ?: EntityType.PERSON).name

    @TypeConverter
    fun toEntityType(value: String?): EntityType =
        runCatching { EntityType.valueOf(value ?: "") }.getOrDefault(EntityType.PERSON)

    @TypeConverter
    fun fromTelemetryEventType(value: TelemetryEventType?): String =
        (value ?: TelemetryEventType.ENERGY_CHECKIN).name

    @TypeConverter
    fun toTelemetryEventType(value: String?): TelemetryEventType =
        runCatching { TelemetryEventType.valueOf(value ?: "") }.getOrDefault(TelemetryEventType.ENERGY_CHECKIN)
}
