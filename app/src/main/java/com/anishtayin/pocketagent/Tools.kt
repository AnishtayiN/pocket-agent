package com.anishtayin.pocketagent

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.CalendarContract
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

data class ToolOutcome(
    val text: String,
    val imagePath: String? = null,
    val sources: List<String> = emptyList()
)

/** The agent's hands: device info & controls (flashlight, vibration,
 *  clipboard, network), web (Keenable, keyless), weather (wttr.in, keyless),
 *  image generation (Pollinations, keyless), alarms/timer/calendar, opening
 *  links & sharing, a speaking voice, a private file workspace, planning,
 *  and long-term memory about the user. */
class ToolExecutor(
    private val context: Context,
    private val memory: MemoryStore,
    private val voice: Voice
) {

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
                "web_search" -> webSearch(args.getString("query"))
                "web_fetch" -> ToolOutcome(webFetch(args.getString("url")))
                "web_research" -> webResearch(args.getString("query"))
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
                "get_weather" -> getWeather(args.getString("city"))
                "set_timer" -> ToolOutcome(setTimer(args.getInt("seconds"), args.optString("label")))
                "set_alarm" -> ToolOutcome(setAlarm(args.getInt("hour"), args.getInt("minute"), args.optString("message")))
                "add_calendar_event" -> ToolOutcome(
                    addCalendarEvent(
                        args.getString("title"),
                        args.optString("description"),
                        args.optInt("in_minutes", 60),
                        args.optInt("duration_minutes", 60)
                    )
                )
                "open_url" -> ToolOutcome(openUrl(args.getString("url")))
                "share_text" -> ToolOutcome(shareText(args.getString("text")))
                "speak" -> ToolOutcome(speak(args.getString("text")))
                "set_flashlight" -> ToolOutcome(setFlashlight(args.getBoolean("on")))
                "vibrate" -> ToolOutcome(vibrate(args.optInt("milliseconds", 300)))
                "get_network_info" -> ToolOutcome(networkInfo())
                "clipboard_read" -> clipboardRead()
                "clipboard_write" -> clipboardWrite(args.getString("text"))
                "update_plan" -> ToolOutcome(updatePlan(args.getString("plan")))
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

    private data class SearchHit(
        val title: String,
        val url: String,
        val snippet: String,
        val date: String
    )

    private suspend fun searchRaw(query: String, maxResults: Int): List<SearchHit> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("query", query)
                put("max_results", maxResults)
            }.toString()
            val req = Request.Builder()
                .url("https://api.keenable.ai/v1/search/public")
                .header("Content-Type", "application/json")
                .header("X-Keenable-Title", "PocketAgent")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = JSONObject(text)
                val results = root.optJSONArray("results") ?: return@withContext emptyList()
                val hits = mutableListOf<SearchHit>()
                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val url = r.optString("url")
                    if (url.isBlank()) continue
                    val date = sequenceOf("published_at", "publishedAt", "published_date", "date")
                        .map { r.optString(it) }
                        .firstOrNull { it.isNotBlank() } ?: ""
                    hits.add(
                        SearchHit(
                            title = r.optString("title"),
                            url = url,
                            snippet = r.optString("snippet")
                                .ifBlank { r.optString("description") },
                            date = date
                        )
                    )
                }
                hits
            }
        }

    private suspend fun fetchRaw(url: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
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
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(text)
                val results = root.optJSONArray("results") ?: return@withContext null
                if (results.length() == 0) return@withContext null
                val r = results.getJSONObject(0)
                Pair(r.optString("title"), r.optString("content"))
            }
        }

    private suspend fun webSearch(query: String): ToolOutcome {
        val hits = searchRaw(query, 8)
        if (hits.isEmpty()) return ToolOutcome("نتیجه‌ای پیدا نشد یا جستجو ناموفق بود")
        val sb = StringBuilder("نتایج جستجو برای «$query»:\n")
        hits.forEachIndexed { i, h ->
            sb.appendLine()
            sb.appendLine("${i + 1}. ${h.title}")
            sb.appendLine(h.url)
            if (h.date.isNotBlank()) sb.appendLine("تاریخ: ${h.date}")
            if (h.snippet.isNotBlank()) sb.appendLine(h.snippet.take(600))
        }
        return ToolOutcome(
            text = sb.toString().take(9000),
            sources = hits.map { it.url }
        )
    }

    private suspend fun webFetch(url: String): String {
        val fetched = fetchRaw(url) ?: return "خواندن صفحه ناموفق بود"
        return "عنوان: ${fetched.first}\n\n${fetched.second}".take(12000)
    }

    /** Deep research in ONE call: search wide, then actually read the top
     *  pages and return their combined content with sources — this is what
     *  makes answers complete instead of snippet-thin. */
    private suspend fun webResearch(query: String): ToolOutcome {
        val hits = searchRaw(query, 8)
        if (hits.isEmpty()) return ToolOutcome("نتیجه‌ای پیدا نشد یا جستجو ناموفق بود")
        val sb = StringBuilder("تحقیق وب برای «$query»\n")
        sb.appendLine("\n— نتایج جستجو —")
        hits.forEachIndexed { i, h ->
            sb.appendLine("${i + 1}. ${h.title} — ${h.url}")
            if (h.snippet.isNotBlank()) sb.appendLine("   ${h.snippet.take(300)}")
        }
        val top = hits.take(3)
        sb.appendLine("\n— متن کامل منابع برتر —")
        for ((i, h) in top.withIndex()) {
            val fetched = try {
                fetchRaw(h.url)
            } catch (e: Exception) {
                null
            }
            sb.appendLine()
            sb.appendLine("=== منبع ${i + 1}: ${h.title} (${h.url}) ===")
            if (fetched != null && fetched.second.isNotBlank()) {
                sb.appendLine(fetched.second.take(4500))
            } else {
                sb.appendLine("(خواندن کامل صفحه ممکن نشد؛ خلاصه جستجو: ${h.snippet.take(600)})")
            }
        }
        sb.appendLine()
        sb.appendLine("دستور: بر اساس همین منابع یک جواب کامل و ساخت‌یافته بنویس و در پایان، منابع را با همین لینک‌ها ذکر کن.")
        return ToolOutcome(
            text = sb.toString().take(17000),
            sources = hits.map { it.url }
        )
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

    // ---------- weather (wttr.in, keyless) ----------

    private suspend fun getWeather(city: String): ToolOutcome = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(city.trim(), "UTF-8").replace("+", "%20")
        val req = Request.Builder()
            .url("https://wttr.in/$encoded?format=j1")
            .header("Accept-Language", "fa")
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful || text.isBlank()) {
                return@withContext ToolOutcome("گرفتن آب‌وهوا ناموفق بود (HTTP ${resp.code})")
            }
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                return@withContext ToolOutcome("پاسخ آب‌وهوا قابل خواندن نبود")
            }
            val current = root.optJSONArray("current_condition")?.optJSONObject(0)
                ?: return@withContext ToolOutcome("اطلاعات آب‌وهوای «$city» پیدا نشد")
            val descFa = current.optJSONArray("lang_fa")?.optJSONObject(0)?.optString("value")
            val desc = descFa?.ifBlank { null }
                ?: current.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value") ?: ""
            val sb = StringBuilder("آب‌وهوای فعلی $city:\n")
            sb.appendLine("وضعیت: $desc")
            sb.appendLine("دما: ${current.optString("temp_C")}°C (حس واقعی: ${current.optString("FeelsLikeC")}°C)")
            sb.appendLine("رطوبت: ${current.optString("humidity")}٪")
            sb.appendLine("باد: ${current.optString("windspeedKmph")} کیلومتر بر ساعت")
            sb.appendLine("دید افق: ${current.optString("visibility")} کیلومتر")
            val today = root.optJSONArray("weather")?.optJSONObject(0)
            if (today != null) {
                sb.appendLine("امروز: کمینه ${today.optString("mintempC")}°C، بیشینه ${today.optString("maxtempC")}°C")
            }
            ToolOutcome(sb.toString().trim(), sources = listOf("https://wttr.in/$encoded"))
        }
    }

    // ---------- device actions ----------

    private fun launchIntent(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun setTimer(seconds: Int, label: String): String {
        if (seconds !in 1..86400) return "مدت تایمر باید بین ۱ تا ۸۶۴۰۰ ثانیه باشد"
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        return if (launchIntent(intent)) "تایمر $seconds ثانیه‌ای${if (label.isNotBlank()) " با برچسب «$label»" else ""} تنظیم شد"
        else "برنامه ساعت برای تنظیم تایمر در دسترس نیست"
    }

    private fun setAlarm(hour: Int, minute: Int, message: String): String {
        if (hour !in 0..23 || minute !in 0..59) return "ساعت نامعتبر است"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (message.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, message)
        }
        return if (launchIntent(intent)) "آلارم برای %02d:%02d تنظیم شد".format(hour, minute)
        else "برنامه ساعت برای تنظیم آلارم در دسترس نیست"
    }

    private fun addCalendarEvent(title: String, description: String, inMinutes: Int, durationMinutes: Int): String {
        val begin = System.currentTimeMillis() + inMinutes.coerceAtLeast(0) * 60_000L
        val end = begin + durationMinutes.coerceAtLeast(5) * 60_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            if (description.isNotBlank()) putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }
        return if (launchIntent(intent)) "رویداد «$title» برای $inMinutes دقیقه دیگر در تقویم باز شد تا تأیید شود"
        else "برنامه تقویم در دسترس نیست"
    }

    private fun openUrl(url: String): String {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return "فقط لینک‌های http/https باز می‌شوند"
        }
        return if (launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse(clean)))) "لینک در مرورگر باز شد: $clean"
        else "مرورگری برای باز کردن لینک پیدا نشد"
    }

    private fun shareText(text: String): String {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "منوی اشتراک‌گذاری باز شد"
        } catch (e: Exception) {
            "اشتراک‌گذاری ممکن نشد"
        }
    }

    private fun speak(text: String): String {
        return if (voice.speak(text)) "متن با صدا خوانده شد"
        else "موتور گفتار روی این گوشی در دسترس نیست یا هنوز آماده نشده"
    }

    private fun setFlashlight(on: Boolean): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "این گوشی چراغ‌قوه ندارد"
            cm.setTorchMode(id, on)
            if (on) "چراغ‌قوه روشن شد 🔦" else "چراغ‌قوه خاموش شد"
        } catch (e: Exception) {
            "کنترل چراغ‌قوه ممکن نشد: ${e.message ?: "نامشخص"}"
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(milliseconds: Int): String {
        val ms = milliseconds.coerceIn(50, 3000).toLong()
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            "گوشی به مدت $ms میلی‌ثانیه لرزید"
        } catch (e: Exception) {
            "لرزش ممکن نشد"
        }
    }

    private fun networkInfo(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "گوشی به هیچ شبکه‌ای وصل نیست (آفلاین)"
            val caps = cm.getNetworkCapabilities(network) ?: return "اطلاعات شبکه در دسترس نیست"
            val type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "وای‌فای"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "اینترنت سیم‌کارت"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "اترنت"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "نامشخص"
            }
            val validated = if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "اتصال اینترنت تأیید شده" else "اتصال به اینترنت تأیید نشده"
            "نوع اتصال: $type\nوضعیت: $validated"
        } catch (e: Exception) {
            "اطلاعات شبکه در دسترس نیست"
        }
    }

    private suspend fun clipboardRead(): ToolOutcome = withContext(Dispatchers.Main) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            if (text.isNullOrBlank()) ToolOutcome("کلیپ‌بورد خالی است یا متن ندارد")
            else ToolOutcome("متن کلیپ‌بورد:\n${text.take(4000)}")
        } catch (e: Exception) {
            ToolOutcome("خواندن کلیپ‌بورد ممکن نشد")
        }
    }

    private suspend fun clipboardWrite(text: String): ToolOutcome = withContext(Dispatchers.Main) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Pocket Agent", text))
            ToolOutcome("متن در کلیپ‌بورد کپی شد")
        } catch (e: Exception) {
            ToolOutcome("کپی در کلیپ‌بورد ممکن نشد")
        }
    }

    // ---------- planning ----------

    /** The plan itself is rendered by the ViewModel as a live checklist card;
     *  here we only validate and acknowledge it. */
    private fun updatePlan(plan: String): String {
        val steps = plan.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (steps.isEmpty()) return "پلن خالی است"
        return "پلن ${steps.size} مرحله‌ای به کاربر نشان داده شد. بعد از هر مرحله، وضعیت را با update_plan به‌روز کن."
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
            "web_research" -> "تحقیق عمیق در وب"
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
            "get_weather" -> "آب‌وهوا"
            "set_timer" -> "تنظیم تایمر"
            "set_alarm" -> "تنظیم آلارم"
            "add_calendar_event" -> "رویداد تقویم"
            "open_url" -> "باز کردن لینک"
            "share_text" -> "اشتراک‌گذاری متن"
            "speak" -> "خواندن با صدا"
            "set_flashlight" -> "چراغ‌قوه"
            "vibrate" -> "لرزش گوشی"
            "get_network_info" -> "وضعیت شبکه"
            "clipboard_read" -> "خواندن کلیپ‌بورد"
            "clipboard_write" -> "کپی در کلیپ‌بورد"
            "update_plan" -> "پلن کار"
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
                put(fn("web_search", "Search the live web. Returns titles, links, dates and snippets. This is your DEFAULT for any factual question — search first, then answer from the results, not from memory.",
                    JSONObject().put("query", strProp("Search query")), listOf("query")))
                put(fn("web_fetch", "Fetch and read the full text content of a web page URL. Use it on the most relevant search result when snippets are not enough.",
                    JSONObject().put("url", strProp("Full URL, including https://")), listOf("url")))
                put(fn("web_research", "Deep web research in one call: searches wide AND reads the full text of the top pages, returning combined content with sources. Use for important, complex or multi-part factual questions where a complete answer matters.",
                    JSONObject().put("query", strProp("Research topic / question")), listOf("query")))
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
                put(fn("run_python", "Run a Python 3 code snippet on the device and get its stdout/stderr back. No interactive input; 25-second limit. Use it to test and debug code.",
                    JSONObject().put("code", strProp("Python code to execute")), listOf("code")))
                put(fn("run_python_file", "Run a Python file from the workspace on the device and get its output or traceback back. This is your debug step after writing a .py file: run it, read the error, fix the file with write_file, run again.",
                    JSONObject().put("name", strProp("Python file name in the workspace, e.g. script.py")), listOf("name")))
                put(fn("get_weather", "Get the CURRENT live weather for a city (temperature, feels-like, humidity, wind, today's min/max). Keyless and instant — prefer it over web search for weather questions.",
                    JSONObject().put("city", strProp("City name in English, e.g. Tehran, Kermanshah, London")), listOf("city")))
                put(fn("set_timer", "Set a countdown timer on the phone's clock app.",
                    JSONObject().put("seconds", JSONObject().apply { put("type", "integer"); put("description", "Timer length in seconds (1..86400)") })
                        .put("label", strProp("Optional timer label")), listOf("seconds")))
                put(fn("set_alarm", "Set an alarm on the phone's clock app (24-hour time).",
                    JSONObject().put("hour", JSONObject().apply { put("type", "integer"); put("description", "Hour, 0-23") })
                        .put("minute", JSONObject().apply { put("type", "integer"); put("description", "Minute, 0-59") })
                        .put("message", strProp("Optional alarm label")), listOf("hour", "minute")))
                put(fn("add_calendar_event", "Create an event in the user's calendar app (opens pre-filled for the user to confirm).",
                    JSONObject().put("title", strProp("Event title"))
                        .put("description", strProp("Optional event description"))
                        .put("in_minutes", JSONObject().apply { put("type", "integer"); put("description", "Minutes from now when the event starts (default 60)") })
                        .put("duration_minutes", JSONObject().apply { put("type", "integer"); put("description", "Event length in minutes (default 60)") }),
                    listOf("title")))
                put(fn("open_url", "Open a http/https link in the user's browser (e.g. a source the user wants to see).",
                    JSONObject().put("url", strProp("Full URL")), listOf("url")))
                put(fn("share_text", "Open the Android share sheet with a text, so the user can send it to any app.",
                    JSONObject().put("text", strProp("Text to share")), listOf("text")))
                put(fn("speak", "Read a text aloud on the phone with the built-in voice (TTS). Use when the user asks you to say/read something out loud.",
                    JSONObject().put("text", strProp("Text to speak (Persian preferred)")), listOf("text")))
                put(fn("set_flashlight", "Turn the phone's flashlight (camera torch) on or off.",
                    JSONObject().put("on", JSONObject().apply { put("type", "boolean"); put("description", "true = on, false = off") }), listOf("on")))
                put(fn("vibrate", "Vibrate the phone briefly (e.g. as a notification the task is done).",
                    JSONObject().put("milliseconds", JSONObject().apply { put("type", "integer"); put("description", "Duration 50..3000 ms") }), emptyList()))
                put(fn("get_network_info", "Get the phone's current network connection type (Wi-Fi / cellular / offline) and whether internet access is validated.", none, emptyList()))
                put(fn("clipboard_read", "Read the text currently on the user's clipboard (works while the app is in the foreground).", none, emptyList()))
                put(fn("clipboard_write", "Copy a text to the user's clipboard.",
                    JSONObject().put("text", strProp("Text to copy")), listOf("text")))
                put(fn("update_plan", "Show/update a live task plan checklist in the chat for multi-step work. Each line is one step, prefixed with its status: '[x] ' done, '[~] ' in progress, '[ ] ' pending. Call it at the start of a multi-step task and after each step completes. Keep steps short.",
                    JSONObject().put("plan", strProp("Full plan, one step per line, e.g.\n[x] جستجوی منابع\n[~] خواندن مقاله اصلی\n[ ] نوشتن جمع‌بندی")), listOf("plan")))
            }
        }
    }
}
