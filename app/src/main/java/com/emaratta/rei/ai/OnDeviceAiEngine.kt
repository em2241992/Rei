package com.emaratta.rei.ai

import android.content.Context
import android.net.Uri
import com.emaratta.rei.data.local.AiBackendConfig
import com.emaratta.rei.data.local.AiBackendMode
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
        badgeText = "🟡 AICore Downloading…",
        detailLabel = "Downloading Gemini Nano weights via Android AICore",
        isHardwareNano = true
    ),
    LITERT_BUNDLE_READY(
        badgeText = "🟢 LiteRT (.litertlm)",
        detailLabel = "Local .litertlm Model Bundle Mounted + Safe Fallback",
        isHardwareNano = true
    ),
    LOCAL_LAN_ACTIVE(
        badgeText = "🟣 Local LAN / Loopback",
        detailLabel = "Ollama / OpenAI-Compatible HTTP Endpoint Active",
        isHardwareNano = false
    ),
    CLOUD_GEMINI_ACTIVE(
        badgeText = "⚡ Cloud Gemini REST",
        detailLabel = "Google Generative Language REST API Active",
        isHardwareNano = false
    ),
    AICORE_SIMULATED_OVERRIDE(
        badgeText = "🟢 Schema Probe Active",
        detailLabel = "JSON Schema Repair Pipeline Active",
        isHardwareNano = true
    ),
    SIM_FALLBACK(
        badgeText = "🔵 Offline Heuristic",
        detailLabel = "Deterministic Local Heuristic Engine (Zero-Crash Fallback)",
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
    val repairNotes: List<String> = listOf("Ready — Select AI Backend in Settings or run Probe"),
    val lastRawModelOutput: String = "",
    val lastRepairedJsonOutput: String = "",
    val downloadProgressLabel: String? = null
)

interface OnDeviceAiEngine {
    val downloadProgressFlow: StateFlow<String?>
    fun updateBackendConfig(config: AiBackendConfig)
    fun currentBackendConfig(): AiBackendConfig
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
 * Production 3-Backend AI Execution Engine (+ Deterministic Offline Heuristic Fallback):
 * 1. On-Device Gemini Nano AICore (`com.google.mlkit:genai-prompt` + optional `.litertlm` SAF file bundle)
 * 2. Local Loopback / LAN (`Ollama` `/api/generate` & OpenAI-compatible `/v1/chat/completions` via `OkHttpClient`)
 * 3. Cloud Gemini REST API (`generativelanguage.googleapis.com` via `OkHttpClient`)
 *
 * Every external/hardware call is wrapped safely so missing AICore services, unreachable LAN servers,
 * or missing API keys fall back cleanly to `LocalSmartSimEngine` without crashing.
 */
open class MultiBackendAiEngine(
    private val localSimEngine: LocalSmartSimEngine,
    private val appContext: Context? = null,
    initialConfig: AiBackendConfig = AiBackendConfig()
) : OnDeviceAiEngine {

    @Volatile
    private var backendConfig: AiBackendConfig = initialConfig

    private var generativeModel: GenerativeModel? = null
    private var hardwareAvailable: Boolean = false
    private var lastRawStatus: String = "UNCHECKED"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val _downloadProgressFlow = MutableStateFlow<String?>(null)
    override val downloadProgressFlow: StateFlow<String?> = _downloadProgressFlow.asStateFlow()

    override fun updateBackendConfig(config: AiBackendConfig) {
        backendConfig = config
    }

    override fun currentBackendConfig(): AiBackendConfig = backendConfig

    private fun obtainAiCoreModelSafely(): GenerativeModel? {
        if (generativeModel != null) return generativeModel
        return try {
            Generation.getClient().also { generativeModel = it }
        } catch (t: Throwable) {
            lastRawStatus = "AICORE_UNSUPPORTED (${t.javaClass.simpleName})"
            null
        }
    }

    private fun verifyLiteRtBundleReadable(uriString: String): Pair<Boolean, String> {
        if (uriString.isBlank() || appContext == null) return false to "No .litertlm URI selected"
        return try {
            val uri = Uri.parse(uriString)
            appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val sizeMb = if (afd.length > 0) "${afd.length / (1024 * 1024)} MB" else "readable stream"
                true to ".litertlm bundle verified ($sizeMb)"
            } ?: (false to ".litertlm URI stream unavailable")
        } catch (t: Throwable) {
            false to ".litertlm read error (${t.javaClass.simpleName})"
        }
    }

    override suspend fun checkAvailability(): EngineRuntimeStatus = withContext(Dispatchers.IO) {
        val cfg = backendConfig
        when (cfg.mode) {
            AiBackendMode.OFFLINE_HEURISTIC -> {
                lastRawStatus = "Offline Heuristic Mode Selected"
                EngineRuntimeStatus.SIM_FALLBACK
            }
            AiBackendMode.LOCAL_LOOPBACK_LAN -> {
                probeLocalLanAvailability(cfg)
            }
            AiBackendMode.CLOUD_GEMINI_REST -> {
                if (cfg.cloudGeminiApiKey.isNotBlank()) {
                    lastRawStatus = "Cloud Gemini REST Configured (${cfg.cloudGeminiModel})"
                    EngineRuntimeStatus.CLOUD_GEMINI_ACTIVE
                } else {
                    lastRawStatus = "Cloud Gemini API Key empty -> Offline Heuristic Fallback"
                    EngineRuntimeStatus.SIM_FALLBACK
                }
            }
            AiBackendMode.AICORE_NANO -> {
                checkAiCoreOrLiteRtAvailability(cfg)
            }
        }
    }

    private suspend fun checkAiCoreOrLiteRtAvailability(cfg: AiBackendConfig): EngineRuntimeStatus {
        try {
            val model = obtainAiCoreModelSafely()
            if (model != null) {
                when (val status = model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> {
                        hardwareAvailable = true
                        _downloadProgressFlow.value = null
                        lastRawStatus = "FeatureStatus.AVAILABLE (Hardware NPU Ready)"
                        runCatching { model.warmup() }
                        return EngineRuntimeStatus.AICORE_ACTIVE
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
                            return EngineRuntimeStatus.AICORE_ACTIVE
                        }
                    }
                    FeatureStatus.DOWNLOADING -> {
                        lastRawStatus = "FeatureStatus.DOWNLOADING (AICore background sync)"
                        _downloadProgressFlow.value = "AICore Nano: Background sync in progress…"
                        return EngineRuntimeStatus.AICORE_DOWNLOADING
                    }
                    else -> {
                        hardwareAvailable = false
                        _downloadProgressFlow.value = null
                        lastRawStatus = "FeatureStatus.UNAVAILABLE (Status=$status)"
                    }
                }
            }
        } catch (t: Throwable) {
            hardwareAvailable = false
            _downloadProgressFlow.value = null
            lastRawStatus = "No AICore Binding (${t.javaClass.simpleName})"
        }

        // Check optional SAF .litertlm bundle if AICore system service isn't available
        val (litertOk, litertMsg) = verifyLiteRtBundleReadable(cfg.litertModelUri)
        if (litertOk) {
            lastRawStatus = "$litertMsg • ${cfg.litertModelName.ifBlank { "local.litertlm" }}"
            return EngineRuntimeStatus.LITERT_BUNDLE_READY
        }

        return EngineRuntimeStatus.SIM_FALLBACK
    }

    private fun probeLocalLanAvailability(cfg: AiBackendConfig): EngineRuntimeStatus {
        val base = cfg.localEndpointUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            lastRawStatus = "Local Endpoint URL empty -> Offline Heuristic Fallback"
            return EngineRuntimeStatus.SIM_FALLBACK
        }
        return try {
            // Try Ollama /api/tags or OpenAI /v1/models quick health check
            val tagsReq = Request.Builder().url("$base/api/tags").get().build()
            httpClient.newCall(tagsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    lastRawStatus = "Connected to Ollama ($base • model=${cfg.localModelName})"
                    return EngineRuntimeStatus.LOCAL_LAN_ACTIVE
                }
            }
            val modelsReq = Request.Builder().url("$base/v1/models").get().build()
            httpClient.newCall(modelsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    lastRawStatus = "Connected to OpenAI-compatible Server ($base • model=${cfg.localModelName})"
                    return EngineRuntimeStatus.LOCAL_LAN_ACTIVE
                }
            }
            lastRawStatus = "Local endpoint configured ($base) — unreachable right now, fallback armed"
            EngineRuntimeStatus.SIM_FALLBACK
        } catch (t: Throwable) {
            lastRawStatus = "Local endpoint unreachable ($base: ${t.javaClass.simpleName}) -> Offline Fallback"
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
        val cfg = backendConfig
        val fullPrompt = buildString {
            appendLine(contextHeader.trim())
            appendLine("---")
            appendLine(instructionAndPayload.trim().take(3200))
        }
        val estimatedTokens = (fullPrompt.length / 4).coerceAtLeast(12)

        when (cfg.mode) {
            AiBackendMode.AICORE_NANO -> {
                executeAiCoreNanoAttempt(fullPrompt, skillName, contextHeader, estimatedTokens, startMs, fallbackGenerator)
                    ?.let { return@withContext it }
            }
            AiBackendMode.LOCAL_LOOPBACK_LAN -> {
                executeLocalLanAttempt(cfg, contextHeader, instructionAndPayload, fullPrompt, skillName, estimatedTokens, startMs, fallbackGenerator)
                    ?.let { return@withContext it }
            }
            AiBackendMode.CLOUD_GEMINI_REST -> {
                executeCloudGeminiAttempt(cfg, fullPrompt, skillName, contextHeader, estimatedTokens, startMs, fallbackGenerator)
                    ?.let { return@withContext it }
            }
            AiBackendMode.OFFLINE_HEURISTIC -> {
                lastRawStatus = "Offline Deterministic Heuristic Active"
            }
        }

        // Clean zero-crash fallback to deterministic LocalSmartSimEngine
        val simOutput = localSimEngine.executeFallback(fallbackGenerator)
        val prettySim = AiCoreSchemaRepair.prettyFormatJson(simOutput)
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(4L)
        simOutput to AiEngineDiagnostics(
            status = EngineRuntimeStatus.SIM_FALLBACK,
            rawFeatureStatusCode = lastRawStatus,
            lastSkillExecuted = skillName,
            lastPromptTokenEstimate = estimatedTokens,
            lastContextHeaderPreview = contextHeader,
            lastLatencyMs = elapsed,
            schemaRepairApplied = false,
            repairNotes = listOf("Deterministic LocalSkillRouter fallback (${cfg.mode.shortBadge} -> safe fallback: $lastRawStatus)"),
            lastRawModelOutput = prettySim,
            lastRepairedJsonOutput = prettySim,
            downloadProgressLabel = _downloadProgressFlow.value
        )
    }

    private suspend fun executeAiCoreNanoAttempt(
        fullPrompt: String,
        skillName: String,
        contextHeader: String,
        estimatedTokens: Int,
        startMs: Long,
        fallbackGenerator: () -> String
    ): Pair<String, AiEngineDiagnostics>? {
        if (!hardwareAvailable) {
            // Re-check once in case AICore finished warming up
            val status = checkAiCoreOrLiteRtAvailability(backendConfig)
            if (status != EngineRuntimeStatus.AICORE_ACTIVE) {
                return null
            }
        }
        return try {
            val model = obtainAiCoreModelSafely() ?: return null
            val request = generateContentRequest(TextPart(fullPrompt)) {
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 650
            }
            val response = model.generateContent(request)
            val responseText = response.candidates.firstOrNull()?.text?.trim().orEmpty()
            if (responseText.isBlank()) return null

            val (repairedText, wasRepaired, notes) = AiCoreSchemaRepair.repairRawModelOutputWithNotes(
                rawText = responseText,
                fallbackGenerator = fallbackGenerator
            )
            val prettyRepaired = AiCoreSchemaRepair.prettyFormatJson(repairedText)
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            repairedText to AiEngineDiagnostics(
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
        } catch (t: Throwable) {
            lastRawStatus = "AICore Inference Fallback (${t.javaClass.simpleName})"
            null
        }
    }

    private fun executeLocalLanAttempt(
        cfg: AiBackendConfig,
        contextHeader: String,
        instructionAndPayload: String,
        fullPrompt: String,
        skillName: String,
        estimatedTokens: Int,
        startMs: Long,
        fallbackGenerator: () -> String
    ): Pair<String, AiEngineDiagnostics>? {
        val base = cfg.localEndpointUrl.trim().trimEnd('/')
        if (base.isBlank()) {
            lastRawStatus = "Local LAN URL blank"
            return null
        }
        val jsonMedia = "application/json; charset=utf-8".toMediaType()

        // 1. Try OpenAI-compatible /v1/chat/completions (supported by Ollama v0.1.24+, LM Studio, llama.cpp, vLLM)
        val openAiUrl = if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
        val chatBody = JSONObject().apply {
            put("model", cfg.localModelName)
            put("temperature", 0.2)
            put("stream", false)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", "$contextHeader\nRespond ONLY with valid JSON."))
                    put(JSONObject().put("role", "user").put("content", instructionAndPayload.take(3600)))
                }
            )
        }.toString()

        var rawReply: String? = null
        try {
            val req = Request.Builder()
                .url(openAiUrl)
                .post(chatBody.toRequestBody(jsonMedia))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val parsed = JSONObject(bodyStr)
                    rawReply = parsed.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                }
            }
        } catch (_: Throwable) {
            // Will try native Ollama /api/generate next
        }

        // 2. Fallback to native Ollama /api/generate if /v1/chat/completions wasn't exposed
        if (rawReply.isNullOrBlank()) {
            try {
                val ollamaBody = JSONObject().apply {
                    put("model", cfg.localModelName)
                    put("prompt", "$fullPrompt\nRespond ONLY with valid JSON.")
                    put("stream", false)
                    put("format", "json")
                }.toString()
                val req = Request.Builder()
                    .url("$base/api/generate")
                    .post(ollamaBody.toRequestBody(jsonMedia))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        rawReply = JSONObject(bodyStr).optString("response").trim()
                    } else {
                        lastRawStatus = "Local HTTP ${resp.code} from $base"
                    }
                }
            } catch (t: Throwable) {
                lastRawStatus = "Local HTTP unreachable ($base: ${t.javaClass.simpleName})"
                return null
            }
        }

        val nonEmptyReply = rawReply?.takeIf { it.isNotBlank() } ?: return null
        lastRawStatus = "HTTP 200 OK ($base • ${cfg.localModelName})"
        val (repairedText, wasRepaired, notes) = AiCoreSchemaRepair.repairRawModelOutputWithNotes(
            rawText = nonEmptyReply,
            fallbackGenerator = fallbackGenerator
        )
        val prettyRepaired = AiCoreSchemaRepair.prettyFormatJson(repairedText)
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        return repairedText to AiEngineDiagnostics(
            status = EngineRuntimeStatus.LOCAL_LAN_ACTIVE,
            rawFeatureStatusCode = lastRawStatus,
            lastSkillExecuted = skillName,
            lastPromptTokenEstimate = estimatedTokens,
            lastContextHeaderPreview = contextHeader,
            lastLatencyMs = elapsed,
            schemaRepairApplied = wasRepaired,
            repairNotes = notes,
            lastRawModelOutput = nonEmptyReply,
            lastRepairedJsonOutput = prettyRepaired,
            downloadProgressLabel = null
        )
    }

    private fun executeCloudGeminiAttempt(
        cfg: AiBackendConfig,
        fullPrompt: String,
        skillName: String,
        contextHeader: String,
        estimatedTokens: Int,
        startMs: Long,
        fallbackGenerator: () -> String
    ): Pair<String, AiEngineDiagnostics>? {
        val apiKey = cfg.cloudGeminiApiKey.trim()
        if (apiKey.isBlank()) {
            lastRawStatus = "Cloud Gemini API key not set in Settings"
            return null
        }
        val modelName = cfg.cloudGeminiModel.trim().ifBlank { "gemini-2.5-flash" }.removePrefix("models/")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val jsonMedia = "application/json; charset=utf-8".toMediaType()

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", fullPrompt))
                    )
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                }
            )
        }.toString()

        return try {
            val req = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMedia))
                .build()
            val responseText = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    lastRawStatus = "Cloud Gemini HTTP ${resp.code} ($modelName)"
                    return null
                }
                val bodyStr = resp.body?.string().orEmpty()
                val root = JSONObject(bodyStr)
                root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()
            }
            if (responseText.isBlank()) {
                lastRawStatus = "Cloud Gemini returned empty candidate ($modelName)"
                return null
            }
            lastRawStatus = "Cloud Gemini 200 OK ($modelName)"
            val (repairedText, wasRepaired, notes) = AiCoreSchemaRepair.repairRawModelOutputWithNotes(
                rawText = responseText,
                fallbackGenerator = fallbackGenerator
            )
            val prettyRepaired = AiCoreSchemaRepair.prettyFormatJson(repairedText)
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            repairedText to AiEngineDiagnostics(
                status = EngineRuntimeStatus.CLOUD_GEMINI_ACTIVE,
                rawFeatureStatusCode = lastRawStatus,
                lastSkillExecuted = skillName,
                lastPromptTokenEstimate = estimatedTokens,
                lastContextHeaderPreview = contextHeader,
                lastLatencyMs = elapsed,
                schemaRepairApplied = wasRepaired,
                repairNotes = notes,
                lastRawModelOutput = responseText,
                lastRepairedJsonOutput = prettyRepaired,
                downloadProgressLabel = null
            )
        } catch (t: Throwable) {
            lastRawStatus = "Cloud Gemini Network Error (${t.javaClass.simpleName})"
            null
        }
    }

    override fun close() {
        runCatching { generativeModel?.close() }
        generativeModel = null
    }
}

/**
 * Backwards-compatible convenience subclass defaulting to AICORE_NANO with clean fallback.
 */
class AiCoreGeminiNanoEngine(
    localSimEngine: LocalSmartSimEngine,
    appContext: Context? = null,
    initialConfig: AiBackendConfig = AiBackendConfig()
) : MultiBackendAiEngine(localSimEngine, appContext, initialConfig)

/**
 * Deterministic, context-aware local heuristic engine used in Offline Heuristic mode
 * and as the automatic zero-crash fallback for all 3 LLM backends.
 */
class LocalSmartSimEngine {
    fun executeFallback(fallbackGenerator: () -> String): String {
        return runCatching { fallbackGenerator() }.getOrElse { "" }
    }
}

/**
 * Strict Model Output Parser & JSON Schema Repair.
 * Handles Markdown code fences, trailing commas, or semi-structured key:value lines
 * emitted by quantized local/cloud models, merging safely with fallback defaults.
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

        val hadMarkdownFence = trimmed.contains("```")
        var stripped = trimmed
            .replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*```$"), "")
            .trim()
        if (hadMarkdownFence) {
            notes.add("Stripped ```json Markdown code fences from model output")
        }

        val hadTrailingComma = Regex(",\\s*([}\\]])").containsMatchIn(stripped)
        stripped = stripped.replace(Regex(",\\s*([}\\]])"), "$1")
        if (hadTrailingComma) {
            notes.add("Removed trailing comma(s) before closing brace/bracket")
        }

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
