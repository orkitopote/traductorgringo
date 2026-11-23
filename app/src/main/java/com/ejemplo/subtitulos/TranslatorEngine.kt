package com.ejemplo.subtitulos

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Traductor inglés -> español con ML Kit. Funciona en el dispositivo:
 * la primera vez descarga un modelo (~30 MB) y después no necesita internet.
 */
class TranslatorEngine {

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
    )

    var isReady = false
        private set

    fun prepare(onReady: () -> Unit, onError: (Exception) -> Unit) {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                isReady = true
                onReady()
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun translate(text: String, onResult: (String) -> Unit) {
        if (!isReady || text.isBlank()) return
        translator.translate(text)
            .addOnSuccessListener { result -> onResult(result) }
            .addOnFailureListener { /* se ignora: la siguiente frase volverá a intentarlo */ }
    }

    fun close() = translator.close()
}
