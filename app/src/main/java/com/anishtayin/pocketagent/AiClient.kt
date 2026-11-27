package com.anishtayin.pocketagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolCall(val id: String, val name: String, val arguments: String)

data class AiReply(val content: String?, val reasoning: String?, val toolCalls: List<ToolCall>)

class AiException(message: String, val httpCode: Int = 0) : Exception(message)

/** Live stream events while the model answers: visible text and thinking. */
sealed interface StreamEvent {
    data class Content(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
}

/** OpenAI-compatible Chat Completions client with:
 *  - STREAMING responses (SSE): content + reasoning deltas are pushed to the
 *    UI as they arrive, like the big AI apps.
 *  - REASONING mode on the primary provider ("reasoning": true).
 *  - An automatic fallback provider if the primary fails before answering.
 *  - Request pacing (~1/second on the primary, its per-IP limit). */
class AiClient {

    private data class Provider(
        val baseUrl: String,
        val model: String,
        val apiKey: String,
        val reasoningParam: Boolean,
        val paceMs: Long
    )

    // Primary (Pollinations / gpt-oss) reasons natively — no param needed.
    // Fallback (APIsFreeLLM) needs its explicit reasoning switch.
    private val primary = Provider(AiConfig.BASE_URL, AiConfig.MODEL, AiConfig.API_KEY, false, 1200L)
    private val fallback = Provider(AiConfig.FALLBACK_BASE_URL, AiConfig.FALLBACK_MODEL, AiConfig.FALLBACK_API_KEY, true, 1100L)

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var activeCall: Call? = null

    /** Cancels the in-flight request (the Stop button). */
    fun cancelActive() {
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            // already finished
        }
    }

    private val paceMutex = Mutex()
    private var lastCallAt = 0L

    private suspend fun pace(ms: Long) {
        paceMutex.withLock {
            val now = System.currentTimeMillis()
            if (lastCallAt > 0) {
                val wait = ms - (now - lastCallAt)
                if (wait > 0) delay(wait)
            }
            lastCallAt = System.currentTimeMillis()
        }
    }

    /** One agent round. Tries, in order: primary streaming, primary plain,
     *  fallback streaming, fallback plain. A streaming attempt that already
     *  produced output returns that partial output instead of failing over,
     *  so the user never loses a half-written answer. */
    suspend fun chat(
        messages: JSONArray,
        tools: JSONArray?,
        onEvent: suspend (StreamEvent) -> Unit
    ): AiReply {
        var lastError: AiException? = null
        val attempts = listOf(
            primary to true, primary to false,
            fallback to true, fallback to false
        )
        for ((provider, stream) in attempts) {
            var tries = 0
            while (tries < 2) {
                tries++
                try {
                    pace(provider.paceMs)
                    return if (stream) {
                        streamChat(provider, messages, tools, onEvent)
                    } else {
                        plainChat(provider, messages, tools, onEvent)
                    }
                } catch (e: AiException) {
                    lastError = e
                    if ((e.httpCode == 429 || e.httpCode == 503) && tries < 2) {
                        delay(2500)
                        continue
                    }
                    break
                }
            }
        }
        throw lastError ?: AiException("خطای ناشناخته از سرویس")
    }

    private fun buildBody(
        provider: Provider,
        messages: JSONArray,
        tools: JSONArray?,
        stream: Boolean
    ): String = JSONObject().apply {
        put("model", provider.model)
        put("messages", messages)
        put("max_tokens", 8000)
        if (provider.reasoningParam) put("reasoning", true)
        if (stream) put("stream", true)
        if (tools != null) {
            put("tools", tools)
            put("tool_choice", "auto")
        }
    }.toString()

    private fun buildRequest(provider: Provider, body: String): Request =
        Request.Builder()
            .url(provider.baseUrl + "/chat/completions")
            .header("Content-Type", "application/json")
            .apply {
                if (provider.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer " + provider.apiKey)
                }
            }
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

    private suspend fun plainChat(
        provider: Provider,
        messages: JSONArray,
        tools: JSONArray?,
        onEvent: suspend (StreamEvent) -> Unit
    ): AiReply = withContext(Dispatchers.IO) {
        val call = http.newCall(buildRequest(provider, buildBody(provider, messages, tools, false)))
        activeCall = call
        try {
            call.execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (resp.code !in 200..299) {
                    throw AiException(serverMessage(text) ?: "HTTP ${resp.code}", resp.code)
                }
                val reply = parseReply(text)
                reply.reasoning?.let { if (it.isNotBlank()) onEvent(StreamEvent.Reasoning(it)) }
                reply.content?.let { if (it.isNotBlank()) onEvent(StreamEvent.Content(it)) }
                reply
            }
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException(e.message ?: "خطای شبکه")
        } finally {
            activeCall = null
        }
    }

    private class ToolAcc {
        var id: String = ""
        val name = StringBuilder()
        val args = StringBuilder()
    }

    private suspend fun streamChat(
        provider: Provider,
        messages: JSONArray,
        tools: JSONArray?,
        onEvent: suspend (StreamEvent) -> Unit
    ): AiReply = withContext(Dispatchers.IO) {
        val call = http.newCall(buildRequest(provider, buildBody(provider, messages, tools, true)))
        activeCall = call
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolAccs = sortedMapOf<Int, ToolAcc>()
        var receivedAny = false
        try {
            call.execute().use { resp ->
                if (resp.code !in 200..299) {
                    val text = resp.body?.string() ?: ""
                    throw AiException(serverMessage(text) ?: "HTTP ${resp.code}", resp.code)
                }
                val reader = resp.body?.byteStream()?.bufferedReader(Charsets.UTF_8)
                    ?: throw AiException("پاسخ خالی از سرویس")
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.substring(5).trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    val chunk = try {
                        JSONObject(payload)
                    } catch (e: Exception) {
                        continue
                    }
                    if (chunk.has("error")) {
                        val msg = chunk.getJSONObject("error").optString("message")
                        throw AiException(msg.ifBlank { "خطای سرویس" })
                    }
                    val choices = chunk.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    if (!delta.isNull("reasoning_content")) {
                        val frag = delta.optString("reasoning_content")
                        if (frag.isNotEmpty()) {
                            reasoning.append(frag)
                            receivedAny = true
                            onEvent(StreamEvent.Reasoning(frag))
                        }
                    }
                    if (!delta.isNull("reasoning")) {
                        val frag = delta.optString("reasoning")
                        if (frag.isNotEmpty()) {
                            reasoning.append(frag)
                            receivedAny = true
                            onEvent(StreamEvent.Reasoning(frag))
                        }
                    }
                    if (!delta.isNull("content")) {
                        val frag = delta.optString("content")
                        if (frag.isNotEmpty()) {
                            content.append(frag)
                            receivedAny = true
                            onEvent(StreamEvent.Content(frag))
                        }
                    }
                    val tcs = delta.optJSONArray("tool_calls")
                    if (tcs != null) {
                        for (i in 0 until tcs.length()) {
                            val tc = tcs.getJSONObject(i)
                            val idx = tc.optInt("index", 0)
                            val acc = toolAccs.getOrPut(idx) { ToolAcc() }
                            tc.optString("id").takeIf { it.isNotBlank() }?.let { acc.id = it }
                            val fn = tc.optJSONObject("function")
                            if (fn != null) {
                                acc.name.append(fn.optString("name"))
                                acc.args.append(fn.optString("arguments"))
                            }
                            receivedAny = true
                        }
                    }
                }
            }
        } catch (e: AiException) {
            if (!receivedAny) throw e
            // else: keep the partial answer below
        } catch (e: Exception) {
            if (!receivedAny) throw AiException(e.message ?: "خطای شبکه")
            // else: stream died mid-answer (or user pressed Stop) — keep partial
        } finally {
            activeCall = null
        }
        if (!receivedAny) throw AiException("پاسخ خالی از سرویس")
        val calls = toolAccs.entries.map { (idx, acc) ->
            ToolCall(
                id = acc.id.ifBlank { "call_$idx" },
                name = acc.name.toString(),
                arguments = acc.args.toString().ifBlank { "{}" }
            )
        }.filter { it.name.isNotBlank() }
        AiReply(
            content = content.toString().ifBlank { null },
            reasoning = reasoning.toString().ifBlank { null },
            toolCalls = calls
        )
    }

    private fun serverMessage(text: String): String? = try {
        val root = JSONObject(text)
        if (root.has("error")) {
            val err = root.get("error")
            when (err) {
                is JSONObject -> err.optString("message").ifBlank { null }
                is String -> err.ifBlank { null }
                else -> null
            }
        } else {
            null
        }
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
                serverMessage(text) ?: "خطای سرویس",
                root.optInt("status", 0)
            )
        }
        val choices = root.optJSONArray("choices")
            ?: throw AiException("پاسخ خالی از سرویس")
        if (choices.length() == 0) throw AiException("پاسخ خالی از سرویس")
        val msg = choices.getJSONObject(0).getJSONObject("message")
        val content = if (msg.isNull("content")) null
        else msg.optString("content").ifBlank { null }
        val reasoning = when {
            !msg.isNull("reasoning") -> msg.optString("reasoning").ifBlank { null }
            !msg.isNull("reasoning_content") -> msg.optString("reasoning_content").ifBlank { null }
            else -> null
        }
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
        return AiReply(content, reasoning, calls)
    }
}
