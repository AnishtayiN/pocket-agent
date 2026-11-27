package com.anishtayin.pocketagent

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs the agent loop: model (streaming, with thinking) -> tool calls ->
 *  results -> model, until a final answer. The agent has a name and a
 *  long-term memory that is injected into every session. Web search is its
 *  default mode for factual questions. */
class AgentViewModel(app: Application) : AndroidViewModel(app) {

    val settings = Settings(app)
    val memoryStore = MemoryStore(app.filesDir)
    private val client = AiClient()
    private val tools = ToolExecutor(app, memoryStore)

    val messages = mutableStateListOf<ChatMsg>()
    val isBusy = mutableStateOf(false)
    val statusText = mutableStateOf("")
    val isProcessing = mutableStateOf(false)
    val processingText = mutableStateOf("")
    val agentName = mutableStateOf(settings.agentName)
    val memoryEntries = mutableStateListOf<String>()

    /** Live streaming state for the in-flight answer. */
    val streamingText = mutableStateOf("")
    val reasoningText = mutableStateOf("")

    private var agentJob: Job? = null
    @Volatile
    private var stopRequested = false

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

    /** Stop button: cancel the in-flight request and the agent loop. */
    fun stop() {
        if (!isBusy.value) return
        stopRequested = true
        client.cancelActive()
        agentJob?.cancel()
    }

    /** Re-run the last user message, dropping the previous answer. */
    fun regenerate() {
        if (isBusy.value || isProcessing.value) return
        val lastUser = messages.indexOfLast { it.role == "user" }
        if (lastUser < 0) return
        while (messages.size > lastUser + 1) {
            messages.removeAt(messages.size - 1)
        }
        persist()
        runAgent()
    }

    /** User picked an image: copy it in, process it on-device (OCR, colors,
     *  dimensions), show it in the chat and let the agent react to it. */
    fun attachImage(uri: Uri) {
        if (isBusy.value || isProcessing.value) return
        val app = getApplication<Application>()
        isProcessing.value = true
        processingText.value = "در حال پردازش عکس…"
        viewModelScope.launch {
            try {
                val dir = File(app.filesDir, "attachments").apply { mkdirs() }
                val ext = when (app.contentResolver.getType(uri)) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
                val dest = File(dir, "attach_${System.currentTimeMillis()}.$ext")
                if (copyUriToFile(uri, dest)) {
                    val note = Processing.imageNote(app, dest)
                    messages.add(
                        ChatMsg(
                            role = "user",
                            content = "",
                            imagePath = dest.absolutePath,
                            attachmentNote = note
                        )
                    )
                    persist()
                    runAgent()
                } else {
                    messages.add(ChatMsg(role = "assistant", content = "⚠️ عکس خوانده نشد. دوباره تلاش کن."))
                }
            } finally {
                isProcessing.value = false
                processingText.value = ""
            }
        }
    }

    /** User picked a document: copy it into the workspace, extract its text
     *  on-device (PDF / text / CSV) and let the agent work with it. */
    fun attachDocument(uri: Uri) {
        if (isBusy.value || isProcessing.value) return
        val app = getApplication<Application>()
        isProcessing.value = true
        processingText.value = "در حال پردازش فایل…"
        viewModelScope.launch {
            try {
                val rawName = displayName(uri) ?: "file_${System.currentTimeMillis()}"
                val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val dir = File(app.filesDir, "workspace").apply { mkdirs() }
                val dest = File(dir, safeName)
                if (copyUriToFile(uri, dest)) {
                    val note = Processing.fileNote(app, dest)
                    messages.add(
                        ChatMsg(
                            role = "user",
                            content = "📄 فایل پیوست شد: $safeName",
                            attachmentNote = note
                        )
                    )
                    persist()
                    runAgent()
                } else {
                    messages.add(ChatMsg(role = "assistant", content = "⚠️ فایل خوانده نشد. دوباره تلاش کن."))
                }
            } finally {
                isProcessing.value = false
                processingText.value = ""
            }
        }
    }

    private fun copyUriToFile(uri: Uri, dest: File): Boolean {
        return try {
            val cr = getApplication<Application>().contentResolver
            cr.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun displayName(uri: Uri): String? {
        return try {
            val cr = getApplication<Application>().contentResolver
            cr.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: Exception) {
            null
        }
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

    /** Short human detail shown on a tool activity card. */
    private fun toolDetail(name: String, argumentsJson: String): String? {
        val args = try {
            JSONObject(argumentsJson.ifBlank { "{}" })
        } catch (e: Exception) {
            return null
        }
        return when (name) {
            "web_search", "web_research" -> args.optString("query").ifBlank { null }
            "web_fetch" -> args.optString("url").ifBlank { null }
            "write_file", "read_file", "run_python_file", "process_file" ->
                args.optString("name").ifBlank { null }
            "generate_image" -> args.optString("prompt").take(70).ifBlank { null }
            "save_memory" -> args.optString("fact").take(70).ifBlank { null }
            "delete_memory" -> args.optString("text").ifBlank { null }
            else -> null
        }
    }

    private fun runAgent() {
        isBusy.value = true
        stopRequested = false
        streamingText.value = ""
        reasoningText.value = ""
        agentJob = viewModelScope.launch {
            try {
                val apiMessages = buildApiMessages()
                var finished = false
                var rounds = 0
                var lastWrittenFile: String? = null
                val turnSources = LinkedHashSet<String>()
                var turnReasoning: String? = null
                while (!finished && rounds < 12) {
                    rounds++
                    statusText.value = "در حال فکر کردن…"
                    streamingText.value = ""
                    reasoningText.value = ""
                    val reply = client.chat(apiMessages, ToolExecutor.specs()) { ev ->
                        when (ev) {
                            is StreamEvent.Content -> {
                                streamingText.value += ev.text
                                statusText.value = "در حال نوشتن پاسخ…"
                            }
                            is StreamEvent.Reasoning -> {
                                reasoningText.value += ev.text
                                statusText.value = "در حال فکر کردن…"
                            }
                        }
                    }
                    if (!reply.reasoning.isNullOrBlank()) {
                        turnReasoning = reply.reasoning
                    } else if (reasoningText.value.isNotBlank()) {
                        turnReasoning = reasoningText.value
                    }
                    val streamed = streamingText.value
                    streamingText.value = ""
                    if (reply.toolCalls.isEmpty()) {
                        val answer = reply.content?.takeIf { it.isNotBlank() }
                            ?: streamed.takeIf { it.isNotBlank() }
                            ?: "پاسخی برنگشت. دوباره تلاش کن."
                        messages.add(
                            ChatMsg(
                                role = "assistant",
                                content = answer,
                                filePath = lastWrittenFile,
                                sources = if (turnSources.isEmpty()) null
                                else turnSources.joinToString("\n"),
                                reasoning = turnReasoning
                            )
                        )
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
                            val rowIndex = messages.size
                            messages.add(
                                ChatMsg(
                                    role = "tool-ui",
                                    content = "",
                                    toolLabel = label,
                                    toolName = tc.name,
                                    toolDetail = toolDetail(tc.name, tc.arguments),
                                    toolDone = false
                                )
                            )
                            persist()
                            val outcome = tools.execute(tc.name, tc.arguments)
                            if (rowIndex < messages.size && messages[rowIndex].role == "tool-ui") {
                                messages[rowIndex] = messages[rowIndex].copy(toolDone = true)
                            }
                            turnSources.addAll(outcome.sources)
                            if (tc.name == "write_file") {
                                val fname = try {
                                    JSONObject(tc.arguments).optString("name")
                                } catch (e: Exception) {
                                    ""
                                }
                                if (fname.isNotBlank()) {
                                    val f = File(getApplication<Application>().filesDir, "workspace/$fname")
                                    if (f.exists()) lastWrittenFile = f.absolutePath
                                }
                            }
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
            } catch (e: CancellationException) {
                val partial = streamingText.value
                if (partial.isNotBlank()) {
                    messages.add(
                        ChatMsg(
                            role = "assistant",
                            content = partial + "\n\n⏹ پاسخ متوقف شد."
                        )
                    )
                }
            } catch (e: AiException) {
                if (!stopRequested) {
                    messages.add(ChatMsg(role = "assistant", content = "⚠️ خطا از سرویس هوش مصنوعی:\n${e.message}"))
                }
            } catch (e: Exception) {
                if (!stopRequested) {
                    messages.add(ChatMsg(role = "assistant", content = "⚠️ خطا: ${e.message ?: "نامشخص"}"))
                }
            } finally {
                isBusy.value = false
                statusText.value = ""
                streamingText.value = ""
                reasoningText.value = ""
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
                    put("role", "user")
                    val sb = StringBuilder()
                    if (m.imagePath != null && m.content.isBlank()) {
                        sb.append("(کاربر یک تصویر پیوست کرده است)")
                    } else {
                        sb.append(m.content)
                    }
                    if (m.attachmentNote != null) {
                        sb.append("\n\n[داده پیوست — نتیجه پردازش روی گوشی]\n")
                        sb.append(m.attachmentNote)
                    }
                    put("content", sb.toString())
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

روش کار پیش‌فرض — فکر کردن و جستجو (خیلی مهم):
- حالت thinking برایت فعال است: قبل از هر جواب، قدم‌به‌قدم و با دقت فکر کن؛ عجله نکن.
- تو یک ایجنتِ محقق هستی، نه یک دانشنامه ثابت و قدیمی. جستجوی وب حالت عادی توست: برای هر سؤال اطلاعاتی و واقعی — حتی سؤال‌هایی که فکر می‌کنی جوابشان را می‌دانی — اول در وب جستجو کن و جواب را بر اساس نتایج تازه و واقعی بساز، نه حدس از حافظه. دانش درونی‌ات ممکن است قدیمی یا ناقص باشد؛ وب، منبع اصلی توست.
- برای سؤال‌های مهم، پیچیده یا چندبخشی از web_research استفاده کن (چند منبع را کامل می‌خواند و اطلاعات کامل جمع می‌کند). برای سؤال‌های ساده‌تر یک یا دو web_search کافی است و اگر خلاصه‌ها کافی نبود، نتیجه مهم را با web_fetch کامل بخوان. اگر نتایج اول ناقص بود، با عبارت متفاوت دوباره جستجو کن.
- فقط این‌ها بدون جستجو جواب داده می‌شوند: گپ‌وگفت و احوال‌پرسی، ریاضی و منطق و کدنویسی، سؤال درباره خود گوشی کاربر (با ابزارهای دستگاه)، و چیزهایی که در حافظه بلندمدتت از کاربر داری.
- جواب نهایی را کامل، دقیق و ساخت‌یافته بنویس: وقتی جواب طولانی است از تیتر (##)، فهرست و **متن پررنگ** استفاده کن. در پایانِ جواب‌هایی که از وب استفاده کرده‌ای، بخش کوتاهی به نام «منابع» با لینک‌های واقعی نتایج بیاور.

حافظه بلندمدت: تو حافظه داری و بین گفتگوها یادت می‌ماند. هر وقت چیز مهم و ماندگاری درباره کاربر فهمیدی — اسمش، شهرش، علاقه‌ها و سلیقه‌اش، آدم‌های مهم زندگی‌اش، هدف‌ها، پروژه‌ها و تصمیم‌هایش — همان موقع با ابزار save_memory ذخیره‌اش کن، بی‌آن‌که لازم باشد هر بار اعلامش کنی. اگر کاربر گفت چیزی را فراموش کنی، با delete_memory پاکش کن. از چیزهایی که در حافظه داری طبیعی استفاده کن (مثلاً اسمش را صدا بزن) ولی فهرست‌وار تکرارشان نکن.
آنچه همین حالا در حافظه بلندمدتت هست:
${memoryStore.asPromptText()}

ابزارهایت: تحقیق و جستجوی زنده در وب و خواندن کامل صفحات، اطلاعات باتری و مشخصات گوشی، ساعت و تاریخ، ساخت عکس، فایل‌های کاری خصوصی (خواندن/نوشتن/فهرست/پردازش)، اجرای واقعی پایتون روی گوشی، و حافظه بلندمدت.
کدنویسی و تحویل فایل (خیلی مهم): وقتی کاربر از تو کد می‌خواهد (پایتون، HTML یا هر زبان):
۱) کد را کامل و تمیز با write_file در فایلی با پسوند درست بنویس (مثلاً script.py یا index.html) — کد نصفه یا نمونه‌وار نده.
۲) اگر پایتون است، حتماً با run_python_file اجرایش کن. اگر خطا داد، traceback را بخوان، فایل را با write_file اصلاح کن و دوباره اجرا کن؛ این چرخه را تا اجرای موفق ادامه بده. اگر بعد از چند تلاش درست نشد، صادقانه بگو کجا گیر کرده.
۳) در پیام پایانی خیلی کوتاه بگو چه ساخته شد، اسم فایل چیست و (برای پایتون) نتیجه اجرا چه بود. فایل به‌صورت خودکار همراه همان پیام به کاربر تحویل داده می‌شود، پس لازم نیست کل کد را در متن پیام تکرار کنی؛ مگر کاربر خودش بخواهد.
پیوست‌ها: کاربر می‌تواند عکس و فایل پیوست کند. تو خودت تصویر را نمی‌بینی؛ اما نتیجه پردازش روی گوشی (متن OCR، رنگ‌های غالب، ابعاد، متن استخراج‌شده از PDF یا فایل) با برچسب [داده پیوست] در گفتگو می‌آید — بر اساس همان داده‌ها جواب بده و اگر داده کافی نیست صادقانه بگو. فایل‌های پیوست‌شده داخل پوشه کاری ذخیره می‌شوند و بعداً هم با process_file و read_file در دسترس‌اند.
قوانین:
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
