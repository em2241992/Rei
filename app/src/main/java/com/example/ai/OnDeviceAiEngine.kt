package com.example.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class EngineRuntimeStatus(
    val badgeText: String,
    val detailLabel: String,
    val isHardwareNano: Boolean
) {
    AICORE_ACTIVE(
        badgeText = "🟢 S25U AICore Nano",
        detailLabel = "Hardware Gemini Nano (ML Kit GenAI) Active",
        isHardwareNano = true
    ),
    AICORE_DOWNLOADING(
        badgeText = "🟡 AICore Nano Downloading…",
        detailLabel = "Downloading Gemini Nano weights via Android AICore",
        isHardwareNano = true
    ),
    AICORE_SIMULATED_OVERRIDE(
        badgeText = "🟢 S25U AICore Nano (Test Mode)",
        detailLabel = "AICore Prompt Pipeline Active (Preview Hardware Emulation)",
        isHardwareNano = true
    ),
    SIM_FALLBACK(
        badgeText = "🔵 Preview Sim Engine",
        detailLabel = "Deterministic Local Heuristic Engine (Zero-NPU Emulator Mode)",
        isHardwareNano = false
    )
}

data class AiEngineDiagnostics(
    val status: EngineRuntimeStatus,
    val rawFeatureStatusCode: String,
    val lastSkillExecuted: String = "Engine Initialization",
    val lastPromptTokenEstimate: Int = 0,
    val lastContextHeaderPreview: String = "",
    val lastLatencyMs: Long = 0L,
    val schemaRepairApplied: Boolean = false,
    val repairNotes: List<String> = listOf("Ready — Run any skill or S25U Self-Test"),
    val lastRawModelOutput: String = "",
    val lastRepairedJsonOutput: String = "",
    val downloadProgressLabel: String? = null
)

interface OnDeviceAiEngine {
    val downloadProgressFlow: StateFlow<String?>
    suspend fun checkAvailability(): EngineRuntimeStatus
    suspend fun runAtomicPrompt(
        skillName: String,
        contextHeader: String,
        instructionAndPayload: String,
        fallbackGenerator: () -> String
    ): Pair<String, AiEngineDiagnostics>
    fun close()
}

/**
 * Connects directly to Android AICore (Gemini Nano) via ML Kit GenAI Prompt API
 * (`com.google.mlkit.genai.prompt.Generation.getClient()`).
 * Every hardware call is wrapped in `try / catch (Throwable)` so missing NPU / AICore
 * services in the browser emulator never crash the app and gracefully delegate to `LocalSmartSimEngine`.
 */
class AiCoreGeminiNanoEngine(
    private val localSimEngine: LocalSmartSimEngine
) : OnDeviceAiEngine {

    private var generativeModel: GenerativeModel? = null
    private var hardwareAvailable: Boolean = false
    private var lastRawStatus: String = "UNCHECKED"

    private val _downloadProgressFlow = MutableStateFlow<String?>(null)
    override val downloadProgressFlow: StateFlow<String?> = _downloadProgressFlow.asStateFlow()

    private fun obtainModelSafely(): GenerativeModel? {
        if (generativeModel != null) return generativeModel
        return try {
            Generation.getClient().also { generativeModel = it }
        } catch (t: Throwable) {
            lastRawStatus = "AICORE_UNSUPPORTED (${t.javaClass.simpleName})"
            null
        }
    }

    override suspend fun checkAvailability(): EngineRuntimeStatus = withContext(Dispatchers.IO) {
        try {
            val model = obtainModelSafely() ?: return@withContext EngineRuntimeStatus.SIM_FALLBACK
            when (val status = model.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    hardwareAvailable = true
                    _downloadProgressFlow.value = null
                    lastRawStatus = "FeatureStatus.AVAILABLE (Hardware NPU Ready)"
                    runCatching { model.warmup() }
                    EngineRuntimeStatus.AICORE_ACTIVE
                }
                FeatureStatus.DOWNLOADABLE -> {
                    lastRawStatus = "FeatureStatus.DOWNLOADABLE (Starting AICore model download)"
                    _downloadProgressFlow.value = "Starting Gemini Nano AICore download…"
                    var downloaded = false
                    runCatching {
                        model.download().collect { dlStatus ->
                            when (dlStatus) {
                                is DownloadStatus.DownloadStarted -> {
                                    _downloadProgressFlow.value = "AICore Nano: Download started…"
                                }
                                is DownloadStatus.DownloadProgress -> {
                                    val mb = dlStatus.totalBytesDownloaded / (1024 * 1024)
                                    _downloadProgressFlow.value = "AICore Nano: Downloaded ${mb} MB…"
                                    lastRawStatus = "FeatureStatus.DOWNLOADING (${mb} MB)"
                                }
                                is DownloadStatus.DownloadCompleted -> {
                                    downloaded = true
                                    _downloadProgressFlow.value = null
                                }
                                is DownloadStatus.DownloadFailed -> {
                                    _downloadProgressFlow.value = null
                                    lastRawStatus = "DownloadFailed (${dlStatus.e.message?.take(40)})"
                                }
                            }
                        }
                    }
                    hardwareAvailable = downloaded
                    if (downloaded) {
                        lastRawStatus = "FeatureStatus.AVAILABLE (Downloaded & Warmed Up)"
                        runCatching { model.warmup() }
                        EngineRuntimeStatus.AICORE_ACTIVE
                    } else {
                        EngineRuntimeStatus.SIM_FALLBACK
                    }
                }
                FeatureStatus.DOWNLOADING -> {
                    lastRawStatus = "FeatureStatus.DOWNLOADING (AICore background sync)"
                    _downloadProgressFlow.value = "AICore Nano: Background sync in progress…"
                    EngineRuntimeStatus.AICORE_DOWNLOADING
                }
                else -> {
                    hardwareAvailable = false
                    _downloadProgressFlow.value = null
                    lastRawStatus = "FeatureStatus.UNAVAILABLE (Status=$status)"
                    EngineRuntimeStatus.SIM_FALLBACK
                }
            }
        } catch (t: Throwable) {
            hardwareAvailable = false
            _downloadProgressFlow.value = null
            lastRawStatus = "Emulator / No AICore Binding (${t.javaClass.simpleName})"
            EngineRuntimeStatus.SIM_FALLBACK
        }
    }

    override suspend fun runAtomicPrompt(
        skillName: String,
        contextHeader: String,
        instructionAndPayload: String,
        fallbackGenerator: () -> String
    ): Pair<String, AiEngineDiagnostics> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        // Keep atomic prompt < 1,024 tokens to respect Gemini Nano's 4,096-token context window
        val fullPrompt = buildString {
            appendLine(contextHeader.trim())
            appendLine("---")
            appendLine(instructionAndPayload.trim().take(2800))
        }
        val estimatedTokens = (fullPrompt.length / 4).coerceAtLeast(12)

        if (hardwareAvailable) {
            try {
                val model = obtainModelSafely()
                if (model != null) {
                    val request = generateContentRequest(TextPart(fullPrompt)) {
                        temperature = 0.2f
                        topK = 16
                        maxOutputTokens = 650
                    }
                    val response = model.generateContent(request)
                    val responseText = response.candidates.firstOrNull()?.text?.trim().orEmpty()
                    if (responseText.isNotBlank()) {
                        val (repairedText, wasRepaired, notes) = AiCoreSchemaRepair.repairRawModelOutputWithNotes(
                            rawText = responseText,
                            fallbackGenerator = fallbackGenerator
                        )
                        val prettyRepaired = AiCoreSchemaRepair.prettyFormatJson(repairedText)
                        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                        return@withContext repairedText to AiEngineDiagnostics(
                            status = EngineRuntimeStatus.AICORE_ACTIVE,
                            rawFeatureStatusCode = lastRawStatus,
                            lastSkillExecuted = skillName,
                            lastPromptTokenEstimate = estimatedTokens,
                            lastContextHeaderPreview = contextHeader,
                            lastLatencyMs = elapsed,
                            schemaRepairApplied = wasRepaired,
                            repairNotes = notes,
                            lastRawModelOutput = responseText,
                            lastRepairedJsonOutput = prettyRepaired,
                            downloadProgressLabel = _downloadProgressFlow.value
                        )
                    }
                }
            } catch (t: Throwable) {
                lastRawStatus = "AICore Inference Fallback (${t.javaClass.simpleName})"
            }
        }

        // Seamless fallback to LocalSmartSimEngine when running in Browser Preview Emulator
        val simOutput = localSimEngine.executeFallback(fallbackGenerator)
        val prettySim = AiCoreSchemaRepair.prettyFormatJson(simOutput)
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(8L)
        simOutput to AiEngineDiagnostics(
            status = EngineRuntimeStatus.SIM_FALLBACK,
            rawFeatureStatusCode = lastRawStatus,
            lastSkillExecuted = skillName,
            lastPromptTokenEstimate = estimatedTokens,
            lastContextHeaderPreview = contextHeader,
            lastLatencyMs = elapsed,
            schemaRepairApplied = false,
            repairNotes = listOf("Deterministic LocalSmartSimEngine output (Zero-NPU Emulator Mode)"),
            lastRawModelOutput = prettySim,
            lastRepairedJsonOutput = prettySim,
            downloadProgressLabel = _downloadProgressFlow.value
        )
    }

    override fun close() {
        runCatching { generativeModel?.close() }
        generativeModel = null
    }
}

/**
 * Deterministic, context-aware on-device simulation engine that activates automatically
 * when the browser preview emulator lacks hardware AICore / NPU, or when toggled manually.
 */
class LocalSmartSimEngine {
    fun executeFallback(fallbackGenerator: () -> String): String {
        return runCatching { fallbackGenerator() }.getOrElse { "" }
    }
}

/**
 * MVP Step 1: Strict On-Device Gemini Nano Output Parser & JSON Schema Repair.
 * Handles Markdown code fences, trailing commas, unquoted keys, or semi-structured
 * key:value lines emitted by quantized on-device models, merging safely with fallback defaults.
 */
object AiCoreSchemaRepair {

    fun repairRawModelOutput(
        rawText: String,
        fallbackGenerator: () -> String
    ): Pair<String, Boolean> {
        val (repaired, wasRepaired, _) = repairRawModelOutputWithNotes(rawText, fallbackGenerator)
        return repaired to wasRepaired
    }

    fun repairRawModelOutputWithNotes(
        rawText: String,
        fallbackGenerator: () -> String
    ): Triple<String, Boolean, List<String>> {
        val notes = mutableListOf<String>()
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            notes.add("Empty raw model output -> substituted deterministic fallback schema")
            return Triple(fallbackGenerator(), true, notes)
        }

        // 1. Strip markdown code fences (```json ... ```) and fix trailing commas
        val hadMarkdownFence = trimmed.contains("```")
        var stripped = trimmed
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        if (hadMarkdownFence) {
            notes.add("Stripped ```json Markdown code fences from Gemini Nano output")
        }

        val hadTrailingComma = Regex(",\\s*([}\\]])").containsMatchIn(stripped)
        stripped = stripped.replace(Regex(",\\s*([}\\]])"), "$1")
        if (hadTrailingComma) {
            notes.add("Removed trailing comma(s) before closing brace/bracket")
        }

        // 2. Check if valid JSONObject or JSONArray directly
        extractBalancedBlock(stripped, '{', '}')?.let { objCandidate ->
            val sanitized = objCandidate.replace(Regex(",\\s*([}\\]])"), "$1")
            val parsed = runCatching { JSONObject(sanitized) }.getOrNull()
            if (parsed != null) {
                val (merged, filledKeys) = mergeWithFallbackObjectWithNotes(parsed, fallbackGenerator)
                if (filledKeys.isNotEmpty()) {
                    notes.add("Backfilled missing schema key(s): ${filledKeys.joinToString(", ")}")
                }
                val wasModified = hadMarkdownFence || hadTrailingComma || filledKeys.isNotEmpty() || (sanitized != trimmed)
                if (notes.isEmpty()) {
                    notes.add("Validated strict JSON schema (0 repairs needed)")
                }
                return Triple(merged.toString(), wasModified, notes)
            }
        }

        extractBalancedBlock(stripped, '[', ']')?.let { arrCandidate ->
            val sanitized = arrCandidate.replace(Regex(",\\s*([}\\]])"), "$1")
            val parsed = runCatching { JSONArray(sanitized) }.getOrNull()
            if (parsed != null && parsed.length() > 0) {
                if (notes.isEmpty()) {
                    notes.add("Validated JSONArray schema (${parsed.length()} elements)")
                }
                return Triple(parsed.toString(), sanitized != trimmed, notes)
            }
        }

        // 3. Line-by-line key:value repair if Gemini Nano emitted bulleted/labeled text instead of JSON
        val fallbackStr = fallbackGenerator()
        val repairedFromLines = repairLabeledTextIntoFallback(stripped, fallbackStr)
        notes.add("Repaired non-JSON key:value lines into structured schema")
        return Triple(repairedFromLines, true, notes)
    }

    fun parseOrMergeJsonObject(rawText: String, fallbackGenerator: () -> String): JSONObject {
        val (repaired, _) = repairRawModelOutput(rawText, fallbackGenerator)
        return runCatching { JSONObject(repaired) }.getOrElse {
            runCatching { JSONObject(fallbackGenerator()) }.getOrDefault(JSONObject())
        }
    }

    fun parseOrMergeJsonArray(rawText: String, fallbackGenerator: () -> String): JSONArray {
        val (repaired, _) = repairRawModelOutput(rawText, fallbackGenerator)
        return runCatching { JSONArray(repaired) }.getOrElse {
            runCatching { JSONArray(fallbackGenerator()) }.getOrDefault(JSONArray())
        }
    }

    fun prettyFormatJson(rawJson: String): String {
        val trimmed = rawJson.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> trimmed
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun mergeWithFallbackObjectWithNotes(
        modelObj: JSONObject,
        fallbackGenerator: () -> String
    ): Pair<JSONObject, List<String>> {
        val filled = mutableListOf<String>()
        val base = runCatching { JSONObject(fallbackGenerator()) }.getOrNull() ?: return modelObj to filled
        val keys = base.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!modelObj.has(k) || modelObj.optString(k).isBlank()) {
                modelObj.put(k, base.get(k))
                filled.add(k)
            }
        }
        return modelObj to filled
    }

    private fun repairLabeledTextIntoFallback(rawLinesText: String, fallbackJson: String): String {
        return try {
            if (fallbackJson.trim().startsWith("[")) {
                // Expecting a 3-step JSONArray (e.g. Shrink It)
                val bullets = rawLinesText.lines()
                    .map { it.trim().replace(Regex("^[-*•0-9.)]+\\s*"), "").trim() }
                    .filter { it.length > 6 }
                if (bullets.size >= 3) {
                    return JSONArray(bullets.take(3)).toString()
                }
                return fallbackJson
            }

            val base = JSONObject(fallbackJson)
            val lines = rawLinesText.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (line in lines) {
                val lower = line.lowercase()
                val value = line.substringAfter(":", "").trim().trim('"')
                if (value.isBlank()) continue
                when {
                    lower.startsWith("title:") && base.has("title") -> base.put("title", value)
                    (lower.startsWith("summary:") || lower.startsWith("tldr:")) && base.has("tldrSummary") ->
                        base.put("tldrSummary", value)
                    lower.startsWith("goal:") && base.has("goal") -> base.put("goal", value)
                    (lower.startsWith("your move:") || lower.startsWith("move:")) && base.has("yourMove") ->
                        base.put("yourMove", value)
                    (lower.startsWith("watch out:") || lower.startsWith("trap:")) && base.has("watchOutTrap") ->
                        base.put("watchOutTrap", value)
                    (lower.startsWith("option 1:") || lower.startsWith("primary:")) && base.has("primaryBody") ->
                        base.put("primaryBody", value)
                    (lower.startsWith("option 2:") || lower.startsWith("bluf:") || lower.startsWith("secondary:")) && base.has("secondaryBody") ->
                        base.put("secondaryBody", value)
                    (lower.startsWith("rule") || lower.startsWith("stop doing:")) && base.has("ruleOfThumb") ->
                        base.put("ruleOfThumb", value)
                }
            }
            base.toString()
        } catch (_: Exception) {
            fallbackJson
        }
    }

    private fun extractBalancedBlock(text: String, openChar: Char, closeChar: Char): String? {
        val start = text.indexOf(openChar)
        val end = text.lastIndexOf(closeChar)
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }
}
