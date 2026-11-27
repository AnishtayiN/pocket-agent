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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs the agent loop: model -> tool calls -> results -> model, until a final answer.
 *  The agent has a name and a long-term memory that is injected into every session. */
class AgentViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    val memoryStore = MemoryStore(app.filesDir)
    private val client = AiClient()
    private val tools = ToolExecutor(app, memoryStore)

    val messages = mutableStateListOf<ChatMsg>()
    val isBusy = mutableStateOf(false)
    val statusText = mutableStateOf("")
    val agentName = mutableStateOf(settings.agentName)
    val memoryEntries = mutableStateListOf<String>()

    private val historyFile = File(app.filesDir, "history.json")

    init {
        if (historyFile.exists()) {
            messages.addAll(ChatMsg.listFromJson(historyFile.readText()))
        }
        refreshMemory()
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

    fun setAgentName(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        settings.agentName = clean
        agentName.value = clean
    }

    fun deleteMemoryAt(index: Int) {
        memoryStore.removeAt(index)
        refreshMemory()
    }

    fun clearMemory() {
        memoryStore.clear()
        refreshMemory()
    }

    private fun refreshMemory() {
        memoryEntries.clear()
        memoryEntries.addAll(memoryStore.all())
    }

    private fun runAgent() {
        isBusy.value = true
        viewModelScope.launch {
            try {
                val apiMessages = buildApiMessages()
                var finished = false
                var rounds = 0
                while (!finished && rounds < 8) {
                    rounds++
                    statusText.value = "در حال فکر کردن…"
                    val reply = client.chat(apiMessages, ToolExecutor.specs())
                    if (reply.toolCalls.isEmpty()) {
                        val answer = reply.content?.takeIf { it.isNotBlank() }
                            ?: "پاسخی برنگشت. دوباره تلاش کن."
                        messages.add(ChatMsg(role = "assistant", content = answer))
                        finished = true
                    } else {
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
                            if (tc.name == "save_memory" || tc.name == "delete_memory") {
                                refreshMemory()
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
            put("content", buildSystemPrompt())
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

    private fun buildSystemPrompt(): String {
        val today = SimpleDateFormat("EEEE d MMMM yyyy", Locale("fa", "IR")).format(Date())
        return """تو «${agentName.value}» هستی؛ یک ایجنت هوش مصنوعی شخصی که روی گوشی اندرویدی کاربر زندگی می‌کنی — یک همراه و دستیار همیشه‌در‌دسترس، نه یک چت‌بات بی‌نام‌ونشان. امروز $today است.

شخصیت: گرم، باهوش، کنجکاو و کمی شوخ‌طبع. با کاربر مثل یک دوست صمیمی و قابل‌اعتماد حرف می‌زنی. به همان زبانی جواب بده که کاربر می‌نویسد (معمولاً فارسی). جواب‌ها را به‌اندازه نگه دار: نه خشک و تک‌کلمه‌ای، نه پرحرف.

حافظه بلندمدت: تو حافظه داری و بین گفتگوها یادت می‌ماند. هر وقت چیز مهم و ماندگاری درباره کاربر فهمیدی — اسمش، شهرش، علاقه‌ها و سلیقه‌اش، آدم‌های مهم زندگی‌اش، هدف‌ها، پروژه‌ها و تصمیم‌هایش — همان موقع با ابزار save_memory ذخیره‌اش کن، بی‌آن‌که لازم باشد هر بار اعلامش کنی. اگر کاربر گفت چیزی را فراموش کنی، با delete_memory پاکش کن. از چیزهایی که در حافظه داری طبیعی استفاده کن (مثلاً اسمش را صدا بزن) ولی فهرست‌وار تکرارشان نکن.
آنچه همین حالا در حافظه بلندمدتت هست:
${memoryStore.asPromptText()}

ابزارهایت: اطلاعات باتری و مشخصات گوشی، ساعت و تاریخ، جستجوی زنده در وب و خواندن صفحات وب، ساخت عکس، فایل‌های کاری خصوصی (خواندن/نوشتن/فهرست) و حافظه بلندمدت.
قوانین:
- برای اطلاعات تازه یا نامطمئن (اخبار، قیمت‌ها، برنامه مسابقات، هوا و...) حتماً از web_search استفاده کن و در صورت نیاز صفحه را با web_fetch بخوان؛ از حافظه حدس نزن.
- وقتی کاربر عکس یا تصویر خواست، با generate_image بساز و پرامپت را انگلیسی و با جزئیات بنویس.
- برای سؤال درباره شارژ یا گوشی، از ابزار باتری و مشخصات استفاده کن.
- اگر ابزاری خطا داد، صادقانه و کوتاه بگو."""
    }

    private fun persist() {
        try {
            historyFile.writeText(ChatMsg.listToJson(messages.toList()))
        } catch (e: Exception) {
            // history is best-effort
        }
    }
}
