package com.ejemplo.subtitulos

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Locale

class MainActivity : AppCompatActivity(), SpeechEngine.Listener {

    private lateinit var tvStatus: TextView
    private lateinit var tvLiveSource: TextView
    private lateinit var tvLiveTarget: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnMic: MaterialButton
    private lateinit var btnDirection: MaterialButton
    private lateinit var swOffline: MaterialSwitch

    private lateinit var speech: SpeechEngine
    private val translator = TranslatorEngine()
    private val adapter = SubtitleAdapter { line -> speakEnglish(line) }

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var direction = Direction.EN_ES
    private var listening = false
    private var textScale = 1f
    private var liveGen = 0          // cambia al terminar cada frase: descarta parciales viejos
    private var mutedBeep = false

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else toast("Se necesita el micrófono para escuchar el audio.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Respetar barras del sistema (Android 15 dibuja de borde a borde)
        val root = findViewById<View>(R.id.root)
        val basePad = root.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(basePad + bars.left, basePad + bars.top, basePad + bars.right, basePad + bars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        findViewById<TextView>(R.id.tvTitle).text = "${getString(R.string.app_name)}  v${BuildConfig.VERSION_NAME}"
        tvLiveSource = findViewById(R.id.tvLiveEn)
        tvLiveTarget = findViewById(R.id.tvLiveEs)
        rvHistory = findViewById(R.id.rvHistory)
        btnMic = findViewById(R.id.btnMic)
        btnDirection = findViewById(R.id.btnDirection)
        swOffline = findViewById(R.id.swOffline)

        rvHistory.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvHistory.adapter = adapter

        speech = SpeechEngine(this, this)

        // Recordar el último sentido elegido
        val prefs = getPreferences(MODE_PRIVATE)
        direction = Direction.entries.getOrElse(prefs.getInt("direction", 0)) { Direction.EN_ES }
        applyDirection()

        btnDirection.setOnClickListener {
            direction = if (direction == Direction.EN_ES) Direction.ES_EN else Direction.EN_ES
            prefs.edit().putInt("direction", direction.ordinal).apply()
            applyDirection()
        }
        btnMic.setOnClickListener { if (listening) stopListening() else askAndStart() }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            adapter.clear()
            clearLive()
        }
        findViewById<MaterialButton>(R.id.btnClear).setOnLongClickListener {
            copyTranscript(); true
        }
        findViewById<MaterialButton>(R.id.btnBigger).setOnClickListener { changeScale(+0.15f) }
        findViewById<MaterialButton>(R.id.btnSmaller).setOnClickListener { changeScale(-0.15f) }
        swOffline.setOnCheckedChangeListener { _, checked ->
            speech.preferOffline = checked
            restartIfListening()
        }

        // Voz en inglés para escuchar la pronunciación al tocar una frase
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.US)
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setSpeechRate(0.9f)
            }
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "Este teléfono no tiene servicio de reconocimiento de voz. " +
                "Instala o actualiza la app de Google."
            return
        }
        prepareTranslator()
    }

    private fun applyDirection() {
        btnDirection.text = direction.label
        speech.language = direction.speechLocale
        translator.direction = direction
        tvLiveSource.hint = direction.sourceHint
        tvLiveTarget.hint = direction.targetHint
        clearLive()
        restartIfListening()
    }

    private fun restartIfListening() {
        if (listening) {
            speech.stop()
            speech.start()
        }
    }

    private fun prepareTranslator() {
        tvStatus.text = "Descargando modelo de traducción (~30 MB, solo la primera vez)…"
        tvStatus.setOnClickListener(null)
        translator.prepare(
            onReady = {
                tvStatus.text = "Listo. Pulsa Escuchar. Toca una frase para oírla en inglés."
                btnMic.isEnabled = true
            },
            onError = {
                tvStatus.text = "No se pudo descargar el modelo de traducción. Revisa tu conexión y toca aquí para reintentar."
                tvStatus.setOnClickListener { prepareTranslator() }
            }
        )
    }

    private fun askAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) startListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        tts?.stop()
        listening = true
        btnMic.text = "■  Detener"
        btnMic.setBackgroundColor(ContextCompat.getColor(this, R.color.rojo))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        muteBeep(true)
        speech.start()
    }

    private fun stopListening() {
        listening = false
        speech.stop()
        translator.cancelPartials()
        muteBeep(false)
        btnMic.text = "▶  Escuchar"
        btnMic.setBackgroundColor(ContextCompat.getColor(this, R.color.acento))
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tvStatus.text = "En pausa. Toca una frase para oírla en inglés; mantén Borrar para copiar todo."
    }

    // ---- SpeechEngine.Listener ----

    override fun onPartial(text: String) {
        tvLiveSource.text = text
        // Con audio rápido la frase crece mucho: traducimos solo el final,
        // que es lo que se está diciendo ahora. La frase completa se traduce al terminar.
        val tail = lastWords(text, 28)
        val gen = liveGen
        translator.translatePartial(tail) { result ->
            if (gen == liveGen) {
                tvLiveTarget.text = if (tail.length < text.length) "… $result" else result
            }
        }
    }

    override fun onFinal(text: String) {
        val line = Line(text, "…", direction.sourceIsEnglish)
        adapter.add(line)
        rvHistory.scrollToPosition(adapter.itemCount - 1)
        clearLive()
        translator.translateFinal(text) { result ->
            line.target = result
            adapter.refresh(line)
        }
    }

    override fun onStatus(text: String) {
        if (listening) tvStatus.text = "$text  (${direction.label})"
    }

    override fun onFatalError(message: String) {
        stopListening()
        tvStatus.text = message
    }

    // ---- Práctica: escuchar la pronunciación ----

    private fun speakEnglish(line: Line) {
        val english = line.english
        if (english.isBlank() || english == "…") return
        if (!ttsReady) {
            toast("Instala la voz en inglés en Ajustes > Texto a voz.")
            return
        }
        // Si seguimos escuchando, el micrófono captaría la voz del teléfono
        if (listening) stopListening()
        tts?.speak(english, TextToSpeech.QUEUE_FLUSH, null, "frase")
    }

    // ---- Utilidades ----

    private fun clearLive() {
        liveGen++
        translator.cancelPartials()
        tvLiveSource.text = ""
        tvLiveTarget.text = ""
    }

    private fun lastWords(text: String, max: Int): String {
        val words = text.split(' ').filter { it.isNotBlank() }
        return if (words.size <= max) text else words.takeLast(max).joinToString(" ")
    }

    /** El reconocedor emite un pitido cada vez que se reinicia; lo silenciamos mientras se escucha. */
    private fun muteBeep(mute: Boolean) {
        if (mute == mutedBeep) return
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val dir = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        for (stream in intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM)) {
            try {
                am.adjustStreamVolume(stream, dir, 0)
            } catch (_: SecurityException) {
                // En modo "No molestar" Android no deja cambiarlo; no pasa nada.
            }
        }
        mutedBeep = mute
    }

    private fun changeScale(delta: Float) {
        textScale = (textScale + delta).coerceIn(0.7f, 2.5f)
        tvLiveSource.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * textScale)
        tvLiveTarget.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * textScale)
        adapter.textScale = textScale
    }

    private fun copyTranscript() {
        val text = adapter.asText()
        if (text.isBlank()) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Subtítulos", text))
        toast("Transcripción copiada")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onStop() {
        super.onStop()
        // Android no permite usar el micrófono desde una app en segundo plano
        if (listening) stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        speech.stop()
        muteBeep(false)
        tts?.shutdown()
        translator.close()
    }
}
