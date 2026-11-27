package com.anishtayin.pocketagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** The agent's voice: on-device TTS (speaks answers, `speak` tool) and
 *  speech recognition (the mic button). Everything is best-effort: if the
 *  device has no Persian voice or recognizer, we degrade gracefully. */
class Voice(private val context: Context) {

    // ---------- text to speech ----------

    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val fa = Locale("fa", "IR")
                    val result = engine.setLanguage(fa)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine.setLanguage(Locale.US)
                    }
                    ttsReady = true
                }
            }
        } catch (e: Exception) {
            // no TTS engine on this device — speaking is simply unavailable
        }
    }

    val canSpeak: Boolean get() = ttsReady

    /** Speak text aloud, replacing anything currently being spoken. */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (!ttsReady) return false
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("[#*_`>\\[\\]()]"), " ")
            .replace(Regex("https?://\\S+"), " ")
            .trim()
            .take(3500)
        if (clean.isBlank()) return false
        return try {
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "pocket_${System.currentTimeMillis()}") ==
                TextToSpeech.SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // already idle
        }
    }

    val isSpeaking: Boolean get() = try { tts?.isSpeaking == true } catch (e: Exception) { false }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // best-effort
        }
        tts = null
        ttsReady = false
        stopListening()
    }

    // ---------- speech recognition (mic button) ----------

    private var recognizer: SpeechRecognizer? = null

    val canListen: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Start listening. [onPartial] receives live text, [onResult] the final
     *  transcript, [onDone] fires when listening ends (error or success). */
    fun listen(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onDone: (String?) -> Unit
    ): Boolean {
        if (!canListen) return false
        return try {
            stopListening()
            val r = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            recognizer = r
            r.setRecognitionListener(object : RecognitionListener {
                private fun text(results: Bundle?): String? =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onPartialResults(partialResults: Bundle?) {
                    text(partialResults)?.let(onPartial)
                }

                override fun onResults(results: Bundle?) {
                    val final = text(results)
                    if (final != null) onResult(final)
                    onDone(null)
                    stopListening()
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "چیزی شنیده نشد"
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "اتصال سرویس تشخیص صدا برقرار نشد"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازه میکروفون داده نشده"
                        else -> "تشخیص صدا ممکن نشد"
                    }
                    onDone(msg)
                    stopListening()
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            r.startListening(intent)
            true
        } catch (e: Exception) {
            stopListening()
            false
        }
    }

    fun stopListening() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            // already gone
        }
        recognizer = null
    }
}