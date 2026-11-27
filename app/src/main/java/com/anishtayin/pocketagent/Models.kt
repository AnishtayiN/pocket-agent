package com.anishtayin.pocketagent

import org.json.JSONArray
import org.json.JSONObject

/** One chat message as shown in the UI and kept in history. */
data class ChatMsg(
    val role: String,                 // "user" | "assistant" | "tool-ui"
    val content: String,
    val imagePath: String? = null,    // generated image attached to this message
    val toolLabel: String? = null     // for "tool-ui" activity rows
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
        imagePath?.let { put("imagePath", it) }
        toolLabel?.let { put("toolLabel", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): ChatMsg = ChatMsg(
            role = o.optString("role", "assistant"),
            content = o.optString("content", ""),
            imagePath = o.optString("imagePath").ifBlank { null },
            toolLabel = o.optString("toolLabel").ifBlank { null }
        )

        fun listToJson(list: List<ChatMsg>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(text: String): List<ChatMsg> = try {
            val arr = JSONArray(text)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
