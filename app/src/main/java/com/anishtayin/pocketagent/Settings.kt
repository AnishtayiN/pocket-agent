package com.anishtayin.pocketagent

import android.content.Context
import android.content.SharedPreferences

/** App settings: AI provider (OpenAI-compatible), model and optional API key.
 *  Stored only on the device, never in the repo. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pocket_agent_settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply()

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("model", value.trim()).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.apisfreellm.com/v1"
        const val DEFAULT_MODEL = "free-ai"
    }
}
