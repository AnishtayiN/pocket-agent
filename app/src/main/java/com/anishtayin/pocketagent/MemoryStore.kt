package com.anishtayin.pocketagent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The agent's long-term memory: durable facts about the user, kept on the
 *  device across conversations (filesDir/agent_memory.json). Injected into
 *  the system prompt every session and editable by the agent via tools and
 *  by the user in Settings. */
class MemoryStore(appFilesDir: File) {

    private val file = File(appFilesDir, "agent_memory.json")
    private val entries = mutableListOf<String>()

    init {
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val text = o.optString("text")
                    if (text.isNotBlank()) entries.add(text)
                }
            } catch (e: Exception) {
                // corrupted memory starts fresh rather than crashing
            }
        }
    }

    fun all(): List<String> = entries.toList()

    fun add(fact: String): Boolean {
        val clean = fact.trim()
        if (clean.isBlank()) return false
        if (entries.any { it.equals(clean, ignoreCase = true) }) return false
        entries.add(clean)
        persist()
        return true
    }

    /** Remove entries containing [fragment] (case-insensitive). Returns how many were removed. */
    fun removeMatching(fragment: String): Int {
        val frag = fragment.trim()
        if (frag.isBlank()) return 0
        val before = entries.size
        entries.removeAll { it.contains(frag, ignoreCase = true) }
        val removed = before - entries.size
        if (removed > 0) persist()
        return removed
    }

    fun removeAt(index: Int) {
        if (index in entries.indices) {
            entries.removeAt(index)
            persist()
        }
    }

    fun clear() {
        entries.clear()
        persist()
    }

    /** Compact text block for the system prompt. */
    fun asPromptText(): String {
        if (entries.isEmpty()) return "(هنوز چیزی در حافظه بلندمدتت نیست)"
        return entries.mapIndexed { i, e -> "${i + 1}. $e" }.joinToString("\n")
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("text", e)
                    put("saved", stamp)
                })
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // memory is best-effort
        }
    }
}
