package com.anishtayin.pocketagent

import org.json.JSONArray
import org.json.JSONObject

/** One chat message as shown in the UI and kept in history. */
data class ChatMsg(
    val role: String,                 // "user" | "assistant" | "tool-ui"
    val content: String,
    val imagePath: String? = null,    // image attached to this message (generated or picked)
    val toolLabel: String? = null,    // for "tool-ui" activity rows
    val toolName: String? = null,     // raw tool name, picks the activity-card icon
    val toolDetail: String? = null,   // e.g. the search query or file name
    val toolDone: Boolean = false,    // activity card finished state
    val attachmentNote: String? = null, // on-device processing result, fed to the model
    val filePath: String? = null,     // a file the agent delivered with this message
    val sources: String? = null,      // newline-joined web source URLs for this answer
    val reasoning: String? = null,    // the model's thinking text for this answer
    val time: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
        imagePath?.let { put("imagePath", it) }
        toolLabel?.let { put("toolLabel", it) }
        toolName?.let { put("toolName", it) }
        toolDetail?.let { put("toolDetail", it) }
        put("toolDone", toolDone)
        attachmentNote?.let { put("attachmentNote", it) }
        filePath?.let { put("filePath", it) }
        sources?.let { put("sources", it) }
        reasoning?.let { put("reasoning", it) }
        put("time", time)
    }

    companion object {
        fun fromJson(o: JSONObject): ChatMsg = ChatMsg(
            role = o.optString("role", "assistant"),
            content = o.optString("content", ""),
            imagePath = o.optString("imagePath").ifBlank { null },
            toolLabel = o.optString("toolLabel").ifBlank { null },
            toolName = o.optString("toolName").ifBlank { null },
            toolDetail = o.optString("toolDetail").ifBlank { null },
            toolDone = o.optBoolean("toolDone", false),
            attachmentNote = o.optString("attachmentNote").ifBlank { null },
            filePath = o.optString("filePath").ifBlank { null },
            sources = o.optString("sources").ifBlank { null },
            reasoning = o.optString("reasoning").ifBlank { null },
            time = o.optLong("time", 0L)
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
