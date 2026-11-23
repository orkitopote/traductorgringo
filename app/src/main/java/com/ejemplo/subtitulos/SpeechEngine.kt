package com.ejemplo.subtitulos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Reconocimiento de voz en inglés continuo.
 * El SpeechRecognizer de Android se detiene tras cada frase, así que lo
 * reiniciamos automáticamente para que escuche sin parar.
 */
class SpeechEngine(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onStatus(text: String)
        fun onFatalError(message: String)
    }

    var preferOffline = false
    var language = "en-US"

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var lastPartial = ""

    fun start() {
        if (active) return
        active = true
        createRecognizer()
        listenNow()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
        lastPartial = ""
    }

    private fun createRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(language))
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        // Cortes de frase más rápidos = la frase final (y su traducción) llega antes
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
    }

    private fun listenNow() {
        if (!active) return
        lastPartial = ""
        try {
            recognizer?.startListening(buildIntent())
        } catch (e: Exception) {
            restart(delayMs = 500, recreate = true)
        }
    }

    private fun restart(delayMs: Long, recreate: Boolean = false) {
        if (!active) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (active) {
                if (recreate) createRecognizer()
                listenNow()
            }
        }, delayMs)
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            .orEmpty()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener.onStatus("🎙️ Escuchando…")
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (text.isNotEmpty() && text != lastPartial) {
                lastPartial = text
                listener.onPartial(text)
            }
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results).ifEmpty { lastPartial }
            lastPartial = ""
            if (text.isNotEmpty()) listener.onFinal(text)
            restart(delayMs = 0)
        }

        override fun onError(error: Int) {
            // Si el motor se cortó con texto parcial, no lo perdemos
            if (lastPartial.isNotEmpty()) {
                listener.onFinal(lastPartial)
                lastPartial = ""
            }
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop()
                    listener.onFatalError("Falta el permiso de micrófono.")
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    stop()
                    listener.onFatalError(
                        "El reconocimiento de voz para $language no está disponible. " +
                            "Instala ese idioma en Ajustes > Google > Voz, " +
                            "o desactiva \"Preferir sin internet\"."
                    )
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    listener.onStatus("Sin conexión con el reconocedor, reintentando…")
                    restart(delayMs = 1500, recreate = true)
                }
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> restart(delayMs = 2500, recreate = true)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> restart(delayMs = 300, recreate = true)
                // ERROR_NO_MATCH, ERROR_SPEECH_TIMEOUT: simplemente hubo silencio
                else -> restart(delayMs = 0)
            }
        }
    }
}
