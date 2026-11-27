package com.anishtayin.pocketagent

import android.content.Context
import android.content.SharedPreferences

/** Internal AI provider config — deliberately NOT shown in the app UI.
 *  Any OpenAI-compatible endpoint works; change here and rebuild.
 *  Primary: keyless free API with reasoning mode. Fallback: Pollinations
 *  (keyless, reasons natively) — used automatically only if the primary
 *  fails before answering, so a dead provider never kills the agent. */
object AiConfig {
    const val BASE_URL = "https://api.apisfreellm.com/v1"
    const val MODEL = "free-ai"
    const val API_KEY = ""
    const val FALLBACK_BASE_URL = "https://text.pollinations.ai/openai"
    const val FALLBACK_MODEL = "openai"
}

/** User-facing settings: only the agent's identity lives here now. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pocket_agent_settings", Context.MODE_PRIVATE)

    var agentName: String
        get() = prefs.getString("agent_name", DEFAULT_AGENT_NAME) ?: DEFAULT_AGENT_NAME
        set(value) = prefs.edit().putString("agent_name", value.trim()).apply()

    companion object {
        const val DEFAULT_AGENT_NAME = "جیبی"
    }
}
