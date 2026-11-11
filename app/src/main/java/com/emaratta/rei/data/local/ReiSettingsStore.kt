package com.emaratta.rei.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent local settings store for Rei production preferences:
 * - Debug Sandbox Mode (Default: OFF — zero auto-seeded demo data on first boot)
 * - 3-Backend AI Execution configuration (AICore Nano / .litertlm, Local Loopback/LAN HTTP, Cloud Gemini REST, Offline Heuristic)
 */
class ReiSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDebugSandboxEnabled(): Boolean =
        prefs.getBoolean(KEY_DEBUG_SANDBOX_MODE, false)

    fun setDebugSandboxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_SANDBOX_MODE, enabled).apply()
    }

    fun isDarkThemeEnabled(): Boolean =
        prefs.getBoolean(KEY_DARK_THEME, true)

    fun setDarkThemeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
    }

    fun loadAiBackendConfig(): AiBackendConfig {
        val rawMode = prefs.getString(KEY_AI_BACKEND_MODE, AiBackendMode.AICORE_NANO.name)
        val mode = runCatching { AiBackendMode.valueOf(rawMode ?: "") }.getOrDefault(AiBackendMode.AICORE_NANO)
        return AiBackendConfig(
            mode = mode,
            litertModelUri = prefs.getString(KEY_LITERT_MODEL_URI, "").orEmpty(),
            litertModelName = prefs.getString(KEY_LITERT_MODEL_NAME, "").orEmpty(),
            localEndpointUrl = prefs.getString(KEY_LOCAL_ENDPOINT_URL, DEFAULT_LOCAL_URL).orEmpty().ifBlank { DEFAULT_LOCAL_URL },
            localModelName = prefs.getString(KEY_LOCAL_MODEL_NAME, DEFAULT_LOCAL_MODEL).orEmpty().ifBlank { DEFAULT_LOCAL_MODEL },
            cloudGeminiApiKey = prefs.getString(KEY_CLOUD_GEMINI_API_KEY, "").orEmpty(),
            cloudGeminiModel = prefs.getString(KEY_CLOUD_GEMINI_MODEL, DEFAULT_CLOUD_MODEL).orEmpty().ifBlank { DEFAULT_CLOUD_MODEL }
        )
    }

    fun saveAiBackendConfig(config: AiBackendConfig) {
        prefs.edit()
            .putString(KEY_AI_BACKEND_MODE, config.mode.name)
            .putString(KEY_LITERT_MODEL_URI, config.litertModelUri.trim())
            .putString(KEY_LITERT_MODEL_NAME, config.litertModelName.trim())
            .putString(KEY_LOCAL_ENDPOINT_URL, config.localEndpointUrl.trim().ifBlank { DEFAULT_LOCAL_URL })
            .putString(KEY_LOCAL_MODEL_NAME, config.localModelName.trim().ifBlank { DEFAULT_LOCAL_MODEL })
            .putString(KEY_CLOUD_GEMINI_API_KEY, config.cloudGeminiApiKey.trim())
            .putString(KEY_CLOUD_GEMINI_MODEL, config.cloudGeminiModel.trim().ifBlank { DEFAULT_CLOUD_MODEL })
            .apply()
    }

    companion object {
        const val PREFS_NAME = "rei_production_settings"
        private const val KEY_DEBUG_SANDBOX_MODE = "debug_sandbox_mode"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_AI_BACKEND_MODE = "ai_backend_mode"
        private const val KEY_LITERT_MODEL_URI = "litert_model_uri"
        private const val KEY_LITERT_MODEL_NAME = "litert_model_name"
        private const val KEY_LOCAL_ENDPOINT_URL = "local_endpoint_url"
        private const val KEY_LOCAL_MODEL_NAME = "local_model_name"
        private const val KEY_CLOUD_GEMINI_API_KEY = "cloud_gemini_api_key"
        private const val KEY_CLOUD_GEMINI_MODEL = "cloud_gemini_model"

        const val DEFAULT_LOCAL_URL = "http://127.0.0.1:11434"
        const val DEFAULT_LOCAL_MODEL = "llama3.2"
        const val DEFAULT_CLOUD_MODEL = "gemini-2.5-flash"
    }
}
