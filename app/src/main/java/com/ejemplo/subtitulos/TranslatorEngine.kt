package com.ejemplo.subtitulos

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/** Sentido de la traducción: qué idioma se escucha y a cuál se traduce. */
enum class Direction(
    val speechLocale: String,
    val from: String,
    val to: String,
    val label: String,
    val sourceHint: String,
    val targetHint: String,
) {
    EN_ES("en-US", TranslateLanguage.ENGLISH, TranslateLanguage.SPANISH, "🇺🇸 Inglés → Español 🇪🇸", "English…", "Español…"),
    ES_EN("es-MX", TranslateLanguage.SPANISH, TranslateLanguage.ENGLISH, "🇪🇸 Español → Inglés 🇺🇸", "Español…", "English…");

    val sourceIsEnglish get() = this == EN_ES
}

/**
 * Traductor inglés <-> español con ML Kit, en el propio dispositivo.
 *
 * Optimización para audio rápido: los textos parciales NO se encolan.
 * Solo hay una traducción parcial en curso; si llegan más mientras tanto,
 * se guarda únicamente la más reciente. Así la traducción nunca se queda
 * atrás aunque la persona hable muy rápido.
 */
class TranslatorEngine {

    private val clients = mutableMapOf<Direction, Translator>()
    var direction = Direction.EN_ES

    var isReady = false
        private set

    private var partialBusy = false
    private var pendingPartial: Pair<String, (String) -> Unit>? = null

    private fun client(d: Direction): Translator = clients.getOrPut(d) {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(d.from)
                .setTargetLanguage(d.to)
                .build()
        )
    }

    /** Descarga el modelo de español (sirve para ambos sentidos) y "calienta" los traductores. */
    fun prepare(onReady: () -> Unit, onError: (Exception) -> Unit) {
        val conditions = DownloadConditions.Builder().build()
        client(Direction.EN_ES).downloadModelIfNeeded(conditions)
            .continueWithTask { client(Direction.ES_EN).downloadModelIfNeeded(conditions) }
            .addOnSuccessListener {
                isReady = true
                // La primera traducción carga el modelo en memoria y es lenta: la hacemos ya.
                client(Direction.EN_ES).translate("Hello, how are you?")
                client(Direction.ES_EN).translate("Hola, ¿cómo estás?")
                onReady()
            }
            .addOnFailureListener { e -> onError(e) }
    }

    /** Frase terminada: siempre se traduce y en orden. */
    fun translateFinal(text: String, onResult: (String) -> Unit) {
        if (!isReady || text.isBlank()) return
        pendingPartial = null // la frase completa reemplaza a cualquier parcial pendiente
        client(direction).translate(text)
            .addOnSuccessListener { result -> onResult(result) }
    }

    /** Texto parcial (mientras se habla): se descarta lo viejo y se traduce lo más reciente. */
    fun translatePartial(text: String, onResult: (String) -> Unit) {
        if (!isReady || text.isBlank()) return
        if (partialBusy) {
            pendingPartial = text to onResult
            return
        }
        runPartial(text, onResult)
    }

    private fun runPartial(text: String, onResult: (String) -> Unit) {
        partialBusy = true
        client(direction).translate(text).addOnCompleteListener { task ->
            if (task.isSuccessful) onResult(task.result)
            partialBusy = false
            pendingPartial?.let { (nextText, nextCb) ->
                pendingPartial = null
                runPartial(nextText, nextCb)
            }
        }
    }

    fun cancelPartials() {
        pendingPartial = null
    }

    fun close() = clients.values.forEach { it.close() }
}
