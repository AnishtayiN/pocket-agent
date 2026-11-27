package com.anishtayin.pocketagent

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Runs the agent loop: model -> tool calls -> results -> model, until a final answer. */
class AgentViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    private val client = AiClient(settings)
    private val tools = ToolExecutor(app)

    val messages = mutableStateListOf<ChatMsg>()
    val isBusy = mutableStateOf(false)
    val statusText = mutableStateOf("")

    private val historyFile = File(app.filesDir, "history.json")

    init {
        if (historyFile.exists()) {
            messages.addAll(ChatMsg.listFromJson(historyFile.readText()))
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || isBusy.value) return
        messages.add(ChatMsg(role = "user", content = trimmed))
        persist()
        runAgent()
    }

    fun clearChat() {
        if (isBusy.value) return
        messages.clear()
        historyFile.delete()
    }

    fun updateSettings(baseUrl: String, model: String, apiKey: String) {
        if (baseUrl.isNotBlank()) settings.baseUrl = baseUrl
        if (model.isNotBlank()) settings.model = model
        settings.apiKey = apiKey
    }

    private fun runAgent() {
        isBusy.value = true
        viewModelScope.launch {
            try {
                val apiMessages = buildApiMessages()
                var finished = false
                var rounds = 0
                while (!finished && rounds < 6) {
                    rounds++
                    statusText.value = "در حال فکر کردن…"
                    val reply = client.chat(apiMessages, ToolExecutor.specs())
                    if (reply.toolCalls.isEmpty()) {
                        val answer = reply.content?.takeIf { it.isNotBlank() }
                            ?: "پاسخی برنگشت. دوباره تلاش کن."
                        messages.add(ChatMsg(role = "assistant", content = answer))
                        finished = true
                    } else {
                        // Record the assistant tool-call turn in API format.
                        val assistantMsg = JSONObject().apply {
                            put("role", "assistant")
                            put("content", reply.content ?: JSONObject.NULL)
                            val arr = JSONArray()
                            reply.toolCalls.forEach { tc ->
                                arr.put(JSONObject().apply {
                                    put("id", tc.id)
                                    put("type", "function")
                                    put("function", JSONObject().apply {
                                        put("name", tc.name)
                                        put("arguments", tc.arguments)
                                    })
                                })
                            }
                            put("tool_calls", arr)
                        }
                        apiMessages.put(assistantMsg)
                        if (!reply.content.isNullOrBlank()) {
                            messages.add(ChatMsg(role = "assistant", content = reply.content))
                        }
                        for (tc in reply.toolCalls) {
                            val label = ToolExecutor.label(tc.name)
                            statusText.value = label + "…"
                            messages.add(ChatMsg(role = "tool-ui", content = "", toolLabel = label))
                            persist()
                            val outcome = tools.execute(tc.name, tc.arguments)
                            apiMessages.put(JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", tc.id)
                                put("content", outcome.text)
                            })
                            if (outcome.imagePath != null) {
                                messages.add(
                                    ChatMsg(
                                        role = "assistant",
                                        content = "عکس آماده شد 🎨",
                                        imagePath = outcome.imagePath
                                    )
                                )
                            }
                            persist()
                        }
                    }
                }
                if (!finished) {
                    messages.add(ChatMsg(role = "assistant", content = "کار طولانی شد و متوقفش کردم. ساده‌تر بپرس یا دوباره تلاش کن."))
                }
            } catch (e: AiException) {
                messages.add(ChatMsg(role = "assistant", content = "⚠️ خطا از سرویس هوش مصنوعی:\n${e.message}"))
            } catch (e: Exception) {
                messages.add(ChatMsg(role = "assistant", content = "⚠️ خطا: ${e.message ?: "نامشخص"}"))
            } finally {
                isBusy.value = false
                statusText.value = ""
                persist()
            }
        }
    }

    private fun buildApiMessages(): JSONArray {
        val arr = JSONArray()
        arr.put(JSONObject().apply {
            put("role", "system")
            put("content", SYSTEM_PROMPT)
        })
        for (m in messages) {
            when (m.role) {
                "user" -> arr.put(JSONObject().apply {
                    put("role", "user"); put("content", m.content)
                })
                "assistant" -> arr.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", if (m.imagePath != null) m.content + " [یک تصویر ساخته و نشان داده شد]" else m.content)
                })
            }
        }
        return arr
    }

    private fun persist() {
        try {
            historyFile.writeText(ChatMsg.listToJson(messages.toList()))
        } catch (e: Exception) {
            // history is best-effort
        }
    }

    companion object {
        const val SYSTEM_PROMPT = """تو «ایجنت جیبی» هستی؛ یک دستیار هوش مصنوعی که روی گوشی اندرویدی کاربر اجرا می‌شوی و ابزار واقعی داری.
ابزارهایت: اطلاعات باتری و مشخصات گوشی، ساعت و تاریخ، جستجوی زنده در وب و خواندن صفحات وب، ساخت عکس، و فایل‌های کاری خصوصی (خواندن/نوشتن/فهرست).
قوانین:
- به همان زبانی جواب بده که کاربر نوشته (معمولاً فارسی). کوتاه، دقیق و مفید باش.
- برای اطلاعات تازه یا نامطمئن (اخبار، قیمت‌ها، برنامه مسابقات، وضعیت هوا و...) حتماً از web_search استفاده کن و در صورت نیاز صفحه را با web_fetch بخوان؛ از حافظه حدس نزن.
- وقتی کاربر عکس یا تصویر خواست، با generate_image بساز و پرامپت را انگلیسی و با جزئیات بنویس.
- برای سؤال درباره شارژ یا گوشی، از ابزار باتری و مشخصات استفاده کن.
- اگر ابزاری خطا داد، صادقانه به کاربر بگو."""
    }
}
