package com.anishtayin.pocketagent

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ToolOutcome(val text: String, val imagePath: String? = null)

/** The agent's hands: device info, web (Keenable, keyless public tier),
 *  image generation (Pollinations, keyless), a private file workspace,
 *  and long-term memory about the user. */
class ToolExecutor(private val context: Context, private val memory: MemoryStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val workspace: File by lazy {
        File(context.filesDir, "workspace").apply { mkdirs() }
    }

    suspend fun execute(name: String, argumentsJson: String): ToolOutcome {
        val args = try {
            JSONObject(argumentsJson.ifBlank { "{}" })
        } catch (e: Exception) {
            JSONObject()
        }
        return try {
            when (name) {
                "get_battery_info" -> ToolOutcome(batteryInfo())
                "get_device_info" -> ToolOutcome(deviceInfo())
                "get_current_time" -> ToolOutcome(currentTime())
                "web_search" -> ToolOutcome(webSearch(args.getString("query")))
                "web_fetch" -> ToolOutcome(webFetch(args.getString("url")))
                "generate_image" -> generateImage(args.getString("prompt"))
                "write_file" -> ToolOutcome(writeFile(args.getString("name"), args.getString("content")))
                "read_file" -> ToolOutcome(readFile(args.getString("name")))
                "list_files" -> ToolOutcome(listFiles())
                "save_memory" -> ToolOutcome(saveMemory(args.getString("fact")))
                "read_memory" -> ToolOutcome(readMemory())
                "delete_memory" -> ToolOutcome(deleteMemory(args.getString("text")))
                "process_file" -> ToolOutcome(processFile(args.getString("name")))
                "run_python" -> ToolOutcome(runPython(args.getString("code")))
                "run_python_file" -> ToolOutcome(runPythonFile(args.getString("name")))
                else -> ToolOutcome("ابزار ناشناخته: $name")
            }
        } catch (e: Exception) {
            ToolOutcome("خطا در اجرای ابزار $name: ${e.message ?: "نامشخص"}")
        }
    }

    // ---------- device ----------

    private fun batteryInfo(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "اطلاعات باتری در دسترس نیست"
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "در حال شارژ"
            BatteryManager.BATTERY_STATUS_FULL -> "کامل (۱۰۰٪)"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "در حال تخلیه (شارژ نیست)"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "به شارژر وصل است ولی شارژ نمی‌شود"
            else -> "نامشخص"
        }
        val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "خوب"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "داغ کرده"
            BatteryManager.BATTERY_HEALTH_DEAD -> "خراب"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "ولتاژ بالا"
            BatteryManager.BATTERY_HEALTH_COLD -> "سرد"
            else -> "نامشخص"
        }
        return "درصد شارژ: $pct٪\nوضعیت: $statusText\nدمای باتری: $tempC درجه سانتی‌گراد\nولتاژ: $voltageMv میلی‌ولت\nسلامت باتری: $health"
    }

    private fun deviceInfo(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / (1024.0 * 1024 * 1024)
        val freeGb = stat.availableBytes / (1024.0 * 1024 * 1024)
        val totalRamGb = mem.totalMem / (1024.0 * 1024 * 1024)
        return buildString {
            appendLine("سازنده: ${Build.MANUFACTURER}")
            appendLine("مدل: ${Build.MODEL}")
            appendLine("اندروید: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("رم کل: ${"%.1f".format(totalRamGb)} گیگابایت")
            append("حافظه داخلی: ${"%.1f".format(freeGb)} از ${"%.1f".format(totalGb)} گیگابایت آزاد")
        }
    }

    private fun currentTime(): String {
        val now = Date()
        val fa = SimpleDateFormat("EEEE d MMMM yyyy، ساعت HH:mm", Locale("fa", "IR")).format(now)
        val en = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(now)
        return "زمان فعلی: $fa\n($en)"
    }

    // ---------- web (Keenable public, keyless) ----------

    private suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("query", query)
            put("max_results", 5)
        }.toString()
        val req = Request.Builder()
            .url("https://api.keenable.ai/v1/search/public")
            .header("Content-Type", "application/json")
            .header("X-Keenable-Title", "PocketAgent")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext "جستجو ناموفق بود (HTTP ${resp.code})"
            val root = JSONObject(text)
            val results = root.optJSONArray("results") ?: return@withContext "نتیجه‌ای پیدا نشد"
            if (results.length() == 0) return@withContext "نتیجه‌ای پیدا نشد"
            val sb = StringBuilder("نتایج جستجو برای «$query»:\n")
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                sb.appendLine()
                sb.appendLine("${i + 1}. ${r.optString("title")}")
                sb.appendLine(r.optString("url"))
                val snip = r.optString("snippet").ifBlank { r.optString("description") }
                if (snip.isNotBlank()) sb.appendLine(snip.take(400))
            }
            sb.toString().take(6000)
        }
    }

    private suspend fun webFetch(url: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("urls", JSONArray().put(url))
        }.toString()
        val req = Request.Builder()
            .url("https://api.keenable.ai/v1/fetch/public")
            .header("Content-Type", "application/json")
            .header("X-Keenable-Title", "PocketAgent")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) return@withContext "خواندن صفحه ناموفق بود (HTTP ${resp.code})"
            val root = JSONObject(text)
            val results = root.optJSONArray("results") ?: return@withContext "محتوایی برنگشت"
            if (results.length() == 0) return@withContext "محتوایی برنگشت"
            val r = results.getJSONObject(0)
            val title = r.optString("title")
            val content = r.optString("content")
            "عنوان: $title\n\n$content".take(6000)
        }
    }

    // ---------- image (Pollinations, keyless) ----------

    private suspend fun generateImage(prompt: String): ToolOutcome = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
        val seed = (1000..999999).random()
        val url = "https://image.pollinations.ai/prompt/$encoded?width=1024&height=1024&nologo=true&seed=$seed"
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                return@withContext ToolOutcome("ساخت عکس ناموفق بود (HTTP ${resp.code})")
            }
            val bytes = resp.body?.bytes() ?: return@withContext ToolOutcome("عکس خالی برگشت")
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            ToolOutcome(
                text = "عکس ساخته و به کاربر نشان داده شد. (پرامپت: $prompt)",
                imagePath = file.absolutePath
            )
        }
    }

    // ---------- files (private workspace) ----------

    private fun safeFile(name: String): File? {
        val f = File(workspace, name)
        val ws = workspace.canonicalPath
        return if (f.canonicalPath.startsWith(ws)) f else null
    }

    private fun writeFile(name: String, content: String): String {
        val f = safeFile(name) ?: return "نام فایل مجاز نیست"
        f.parentFile?.mkdirs()
        f.writeText(content)
        return "فایل «$name» نوشته شد (${content.length} نویسه)"
    }

    private fun readFile(name: String): String {
        val f = safeFile(name) ?: return "نام فایل مجاز نیست"
        if (!f.exists()) return "فایل «$name» وجود ندارد"
        return f.readText().take(6000)
    }

    private fun listFiles(): String {
        val files = workspace.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return "پوشه کاری خالی است"
        return files.joinToString("\n") {
            it.relativeTo(workspace).path + " (${it.length()} بایت)"
        }
    }

    private suspend fun processFile(name: String): String {
        val f = safeFile(name) ?: return "نام فایل مجاز نیست"
        if (!f.exists()) return "فایل «$name» وجود ندارد"
        return Processing.fileNote(context, f)
    }

    // ---------- Python execution (Chaquopy, on-device) ----------

    private suspend fun runPythonFile(name: String): String {
        val f = safeFile(name) ?: return "نام فایل مجاز نیست"
        if (!f.exists()) return "فایل «$name» وجود ندارد"
        return runPython(f.readText())
    }

    private suspend fun runPython(code: String): String {
        val json = withTimeoutOrNull(25_000) {
            withContext(Dispatchers.IO) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context))
                }
                val module = Python.getInstance().getModule("runner")
                module.callAttr("run", code).toString()
            }
        } ?: return "اجرا بیش از ۲۵ ثانیه طول کشید و متوقف شد (احتمال حلقه بی‌نهایت یا انتظار برای ورودی). ورودی تعاملی (input) پشتیبانی نمی‌شود."
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return "خطای داخلی موتور پایتون"
        }
        val ok = root.optBoolean("ok")
        val out = root.optString("stdout")
        val err = root.optString("stderr")
        return buildString {
            appendLine(if (ok) "✅ اجرا موفق بود." else "❌ اجرا با خطا مواجه شد.")
            if (out.isNotBlank()) {
                appendLine("خروجی (stdout):")
                appendLine(out.take(4000))
            }
            if (err.isNotBlank()) {
                appendLine(if (ok) "هشدارها (stderr):" else "خطا (traceback):")
                appendLine(err.take(4000))
            }
            if (out.isBlank() && err.isBlank()) appendLine("(بدون خروجی)")
        }.trim()
    }

    // ---------- long-term memory ----------

    private fun saveMemory(fact: String): String {
        return if (memory.add(fact)) "در حافظه بلندمدت ذخیره شد: $fact"
        else "این مورد از قبل در حافظه بود یا خالی بود"
    }

    private fun readMemory(): String {
        val all = memory.all()
        if (all.isEmpty()) return "حافظه بلندمدت خالی است"
        return all.mapIndexed { i, e -> "${i + 1}. $e" }.joinToString("\n")
    }

    private fun deleteMemory(text: String): String {
        val removed = memory.removeMatching(text)
        return if (removed > 0) "$removed مورد از حافظه حذف شد"
        else "موردی مطابق «$text» در حافظه پیدا نشد"
    }

    companion object {
        /** Persian UI label for each tool, shown as an activity row in the chat. */
        fun label(name: String): String = when (name) {
            "get_battery_info" -> "بررسی باتری"
            "get_device_info" -> "مشخصات گوشی"
            "get_current_time" -> "ساعت و تاریخ"
            "web_search" -> "جستجو در وب"
            "web_fetch" -> "خواندن صفحه وب"
            "generate_image" -> "ساخت عکس"
            "write_file" -> "نوشتن فایل"
            "read_file" -> "خواندن فایل"
            "list_files" -> "فهرست فایل‌ها"
            "save_memory" -> "ذخیره در حافظه"
            "read_memory" -> "خواندن حافظه"
            "delete_memory" -> "حذف از حافظه"
            "process_file" -> "پردازش فایل"
            "run_python" -> "اجرای پایتون"
            "run_python_file" -> "اجرا و دیباگ پایتون"
            else -> name
        }

        /** OpenAI-format tool definitions sent with every chat request. */
        fun specs(): JSONArray {
            fun fn(name: String, desc: String, props: JSONObject, required: List<String>): JSONObject =
                JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", name)
                        put("description", desc)
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", props)
                            put("required", JSONArray(required))
                        })
                    })
                }

            fun strProp(desc: String): JSONObject =
                JSONObject().apply { put("type", "string"); put("description", desc) }

            val none = JSONObject()
            return JSONArray().apply {
                put(fn("get_battery_info", "Get the phone's current battery level, charging status, temperature, voltage and health.", none, emptyList()))
                put(fn("get_device_info", "Get phone model, manufacturer, Android version, RAM and storage.", none, emptyList()))
                put(fn("get_current_time", "Get the current date and time.", none, emptyList()))
                put(fn("web_search", "Search the live web. Returns titles, links and snippets. Use for fresh facts, news and anything you are unsure about.",
                    JSONObject().put("query", strProp("Search query")), listOf("query")))
                put(fn("web_fetch", "Fetch and read the text content of a web page URL.",
                    JSONObject().put("url", strProp("Full URL, including https://")), listOf("url")))
                put(fn("generate_image", "Generate an image from a text prompt and show it to the user. Write the prompt in English for best results.",
                    JSONObject().put("prompt", strProp("Detailed image description in English")), listOf("prompt")))
                put(fn("write_file", "Write a text file into the app's private workspace (for code, notes, drafts).",
                    JSONObject().put("name", strProp("File name, e.g. note.txt or Main.kt")).put("content", strProp("Full file content")),
                    listOf("name", "content")))
                put(fn("read_file", "Read a text file from the app's private workspace.",
                    JSONObject().put("name", strProp("File name")), listOf("name")))
                put(fn("list_files", "List files in the app's private workspace.", none, emptyList()))
                put(fn("save_memory", "Save an important, durable fact about the user into your long-term memory (their name, city, preferences, important people, goals, decisions). Save facts as you learn them; do not announce every save.",
                    JSONObject().put("fact", strProp("One clear, self-contained fact about the user")), listOf("fact")))
                put(fn("read_memory", "Read everything currently in your long-term memory about the user.", none, emptyList()))
                put(fn("delete_memory", "Forget long-term memory entries that match a text fragment (when the user asks you to forget something, or a fact turns out to be wrong).",
                    JSONObject().put("text", strProp("Fragment that matches the memory entries to remove")), listOf("text")))
                put(fn("process_file", "Extract and read the content of a file in the workspace: PDF text, plain-text/code files, or a CSV summary. Use it for files the user attached or that you wrote earlier when you need their full content.",
                    JSONObject().put("name", strProp("File name in the workspace")), listOf("name")))
                put(fn("run_python", "Run a Python 3.11 code snippet on the device and get its stdout/stderr back. No interactive input; 25-second limit. Use it to test and debug code.",
                    JSONObject().put("code", strProp("Python code to execute")), listOf("code")))
                put(fn("run_python_file", "Run a Python file from the workspace on the device and get its output or traceback back. This is your debug step after writing a .py file: run it, read the error, fix the file with write_file, run again.",
                    JSONObject().put("name", strProp("Python file name in the workspace, e.g. script.py")), listOf("name")))
            }
        }
    }
}
