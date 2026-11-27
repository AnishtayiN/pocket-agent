package com.anishtayin.pocketagent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** On-device processing for images and files the user attaches (or that live
 *  in the workspace). The chat model is text-only, so everything the agent
 *  "knows" about an attachment comes from these notes: OCR text, dominant
 *  colors, dimensions, extracted PDF/text content, CSV structure. */
object Processing {

    @Volatile
    private var pdfReady = false

    private fun ensurePdf(context: Context) {
        if (!pdfReady) {
            synchronized(this) {
                if (!pdfReady) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    pdfReady = true
                }
            }
        }
    }

    // ---------- images ----------

    suspend fun imageNote(context: Context, file: File): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("نوع پیوست: تصویر (${file.name})")
        sb.appendLine("حجم فایل: ${file.length() / 1024} کیلوبایت")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth > 0) {
            sb.appendLine("ابعاد: ${bounds.outWidth}×${bounds.outHeight} پیکسل")
        }

        val bitmap = decodeScaled(file, 1600)
        if (bitmap != null) {
            // dominant colors
            try {
                val palette = Palette.from(bitmap).generate()
                val colors = listOfNotNull(
                    palette.dominantSwatch?.let { "رنگ غالب #%06X".format(it.rgb and 0xFFFFFF) },
                    palette.vibrantSwatch?.let { "رنگ زنده #%06X".format(it.rgb and 0xFFFFFF) },
                    palette.mutedSwatch?.let { "رنگ ملایم #%06X".format(it.rgb and 0xFFFFFF) },
                    palette.darkVibrantSwatch?.let { "رنگ تیره #%06X".format(it.rgb and 0xFFFFFF) },
                    palette.lightVibrantSwatch?.let { "رنگ روشن #%06X".format(it.rgb and 0xFFFFFF) }
                )
                if (colors.isNotEmpty()) sb.appendLine("رنگ‌ها: " + colors.joinToString("، "))
            } catch (e: Exception) {
                // colors are best-effort
            }

            // OCR (Latin script)
            val ocr = ocr(bitmap)
            if (ocr.isNotBlank()) {
                sb.appendLine("متن خوانده‌شده از تصویر (OCR):")
                sb.appendLine(ocr.take(4000))
            } else {
                sb.appendLine("متن لاتین خوانایی در تصویر پیدا نشد (OCR فقط خط لاتین را می‌خواند).")
            }
            bitmap.recycle()
        } else {
            sb.appendLine("تصویر قابل خواندن نبود.")
        }
        sb.toString().trim()
    }

    private fun decodeScaled(file: File, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w > maxDim || h > maxDim) {
            sample *= 2
            w /= 2
            h /= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun ocr(bitmap: Bitmap): String {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)))
            result.text.trim()
        } catch (e: Exception) {
            ""
        } finally {
            client.close()
        }
    }

    // ---------- files ----------

    suspend fun fileNote(context: Context, file: File): String = withContext(Dispatchers.IO) {
        val ext = file.extension.lowercase()
        val sb = StringBuilder()
        sb.appendLine("نوع پیوست: فایل (${file.name})")
        sb.appendLine("حجم فایل: ${file.length() / 1024} کیلوبایت")
        try {
            when (ext) {
                "pdf" -> {
                    ensurePdf(context)
                    PDDocument.load(file).use { doc ->
                        sb.appendLine("تعداد صفحات: ${doc.numberOfPages}")
                        val text = PDFTextStripper().getText(doc).trim()
                        if (text.isNotBlank()) {
                            sb.appendLine("متن استخراج‌شده از PDF:")
                            sb.appendLine(text.take(6000))
                            if (text.length > 6000) sb.appendLine("…(ادامه متن بریده شد)")
                        } else {
                            sb.appendLine("PDF متن قابل استخراج نداشت (احتمالاً اسکن تصویری است).")
                        }
                    }
                }
                "txt", "md", "json", "log", "xml", "kt", "java", "py", "js", "html", "css" -> {
                    val text = file.readText()
                    sb.appendLine("محتوای فایل:")
                    sb.appendLine(text.take(6000))
                    if (text.length > 6000) sb.appendLine("…(ادامه بریده شد)")
                }
                "csv" -> {
                    val lines = file.readLines()
                    sb.appendLine("تعداد ردیف‌ها: ${lines.size}")
                    sb.appendLine("ردیف‌های اول:")
                    lines.take(6).forEach { sb.appendLine(it.take(300)) }
                }
                else -> {
                    sb.appendLine("برای این نوع فایل (.$ext) استخراج متن پشتیبانی نمی‌شود؛ فقط مشخصات کلی در دسترس است.")
                }
            }
        } catch (e: Exception) {
            sb.appendLine("خطا در پردازش فایل: ${e.message ?: "نامشخص"}")
        }
        sb.toString().trim()
    }
}
