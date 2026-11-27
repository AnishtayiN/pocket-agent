package com.anishtayin.pocketagent

import android.content.Context
import android.content.SharedPreferences

/** Internal AI provider config — deliberately NOT shown in the app UI.
 *  Primary: Pollinations (keyless) serving a real reasoning model
 *  (gpt-oss) that thinks natively and streams its reasoning — this is the
 *  brain. Fallback: APIsFreeLLM (keyless, reasoning mode on), used
 *  automatically only if the primary fails before answering (e.g. its
 *  daily anonymous cap), so a dead provider never kills the agent. */
object AiConfig {
    const val BASE_URL = "https://text.pollinations.ai/openai"
    const val MODEL = "openai"
    const val API_KEY = ""
    const val FALLBACK_BASE_URL = "https://api.apisfreellm.com/v1"
    const val FALLBACK_MODEL = "free-ai"
    const val FALLBACK_API_KEY = ""
}

/** User-facing settings: identity, appearance and voice. */
class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pocket_agent_settings", Context.MODE_PRIVATE)

    var agentName: String
        get() = prefs.getString("agent_name", DEFAULT_AGENT_NAME) ?: DEFAULT_AGENT_NAME
        set(value) = prefs.edit().putString("agent_name", value.trim()).apply()

    /** "system" | "light" | "dark" */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    /** Read every finished answer aloud automatically (TTS). */
    var autoSpeak: Boolean
        get() = prefs.getBoolean("auto_speak", false)
        set(value) = prefs.edit().putBoolean("auto_speak", value).apply()

    /** Deep-thinking mode: native reasoning + research nudge + answer
     *  verification. Off = fast mode: the plain API answer, no extra passes. */
    var thinkingMode: Boolean
        get() = prefs.getBoolean("thinking_mode", true)
        set(value) = prefs.edit().putBoolean("thinking_mode", value).apply()

    companion object {
        const val DEFAULT_AGENT_NAME = "آورا"
    }
}
