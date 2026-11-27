package com.anishtayin.pocketagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolCall(val id: String, val name: String, val arguments: String)

data class AiReply(val content: String?, val toolCalls: List<ToolCall>)

class AiException(message: String) : Exception(message)

/** Minimal OpenAI-compatible Chat Completions client.
 *  - Works with any provider whose base URL + model are set in Settings.
 *  - Retries 429/503 up to 3 times with backoff; paces requests to ~1/second
 *    (the default free provider allows 1 request/second per IP). */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val paceMutex = Mutex()
    private var lastCallAt = 0L

    private suspend fun pace() {
        paceMutex.withLock {
            val now = System.currentTimeMillis()
            if (lastCallAt > 0) {
                val wait = 1100L - (now - lastCallAt)
                if (wait > 0) delay(wait)
            }
            lastCallAt = System.currentTimeMillis()
        }
    }

    suspend fun chat(messages: JSONArray, tools: JSONArray?): AiReply {
        val body = JSONObject().apply {
            put("model", AiConfig.MODEL)
            put("messages", messages)
            put("max_tokens", 4096)
            if (tools != null) {
                put("tools", tools)
                put("tool_choice", "auto")
            }
        }.toString()

        var lastError = "خطای ناشناخته از سرویس"
        for (attempt in 1..3) {
            pace()
            val (code, text) = executeOnce(body)
            if (code == 429 || code == 503) {
                lastError = serverMessage(text) ?: "سرویس شلوغ است (HTTP $code)"
                if (attempt < 3) delay(2000L * attempt)
                continue
            }
            if (code !in 200..299) {
                throw AiException(serverMessage(text) ?: "HTTP $code")
            }
            return parseReply(text)
        }
        throw AiException(lastError)
    }

    private suspend fun executeOnce(body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(AiConfig.BASE_URL + "/chat/completions")
            .header("Content-Type", "application/json")
            .apply {
                if (AiConfig.API_KEY.isNotBlank()) {
                    header("Authorization", "Bearer " + AiConfig.API_KEY)
                }
            }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(req).execute().use { resp ->
                Pair(resp.code, resp.body?.string() ?: "")
            }
        } catch (e: Exception) {
            Pair(-1, e.message ?: "network error")
        }
    }

    private fun serverMessage(text: String): String? = try {
        val root = JSONObject(text)
        if (root.has("error")) root.getJSONObject("error").optString("message").ifBlank { null }
        else null
    } catch (e: Exception) {
        null
    }

    private fun parseReply(text: String): AiReply {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw AiException("پاسخ نامعتبر از سرویس")
        }
        if (root.has("error")) {
            throw AiException(
                root.getJSONObject("error").optString("message").ifBlank { "خطای سرویس" }
            )
        }
        val choices = root.optJSONArray("choices")
            ?: throw AiException("پاسخ خالی از سرویس")
        if (choices.length() == 0) throw AiException("پاسخ خالی از سرویس")
        val msg = choices.getJSONObject(0).getJSONObject("message")
        val content = if (msg.isNull("content")) null
        else msg.optString("content").ifBlank { null }
        val calls = mutableListOf<ToolCall>()
        val arr = msg.optJSONArray("tool_calls")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val tc = arr.getJSONObject(i)
                val fn = tc.optJSONObject("function") ?: continue
                calls.add(
                    ToolCall(
                        id = tc.optString("id").ifBlank { "call_$i" },
                        name = fn.optString("name"),
                        arguments = fn.optString("arguments").ifBlank { "{}" }
                    )
                )
            }
        }
        return AiReply(content, calls)
    }
}
