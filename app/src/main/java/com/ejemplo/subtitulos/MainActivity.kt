package com.ejemplo.subtitulos

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.TypedValue
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

class MainActivity : AppCompatActivity(), SpeechEngine.Listener {

    private lateinit var tvStatus: TextView
    private lateinit var tvLiveEn: TextView
    private lateinit var tvLiveEs: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnMic: MaterialButton
    private lateinit var swOffline: MaterialSwitch

    private lateinit var speech: SpeechEngine
    private val translator = TranslatorEngine()
    private val adapter = SubtitleAdapter()

    private var listening = false
    private var textScale = 1f
    private var liveSeq = 0          // evita mostrar traducciones parciales viejas
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
        val root = findViewById<android.view.View>(R.id.root)
        val basePad = root.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(basePad + bars.left, basePad + bars.top, basePad + bars.right, basePad + bars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        tvLiveEn = findViewById(R.id.tvLiveEn)
        tvLiveEs = findViewById(R.id.tvLiveEs)
        rvHistory = findViewById(R.id.rvHistory)
        btnMic = findViewById(R.id.btnMic)
        swOffline = findViewById(R.id.swOffline)

        rvHistory.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvHistory.adapter = adapter

        speech = SpeechEngine(this, this)

        btnMic.setOnClickListener { if (listening) stopListening() else askAndStart() }
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            adapter.clear()
            tvLiveEn.text = ""
            tvLiveEs.text = ""
        }
        findViewById<MaterialButton>(R.id.btnClear).setOnLongClickListener {
            copyTranscript(); true
        }
        findViewById<MaterialButton>(R.id.btnBigger).setOnClickListener { changeScale(+0.15f) }
        findViewById<MaterialButton>(R.id.btnSmaller).setOnClickListener { changeScale(-0.15f) }
        swOffline.setOnCheckedChangeListener { _, checked ->
            speech.preferOffline = checked
            if (listening) { speech.stop(); speech.start() }
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "Este teléfono no tiene servicio de reconocimiento de voz. " +
                "Instala o actualiza la app de Google."
            return
        }
        prepareTranslator()
    }

    private fun prepareTranslator() {
        tvStatus.text = "Descargando modelo de traducción inglés→español (~30 MB, solo la primera vez)…"
        tvStatus.setOnClickListener(null)
        translator.prepare(
            onReady = {
                tvStatus.text = "Listo. Pulsa Escuchar y acerca el teléfono al audio en inglés."
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
        muteBeep(false)
        btnMic.text = "▶  Escuchar"
        btnMic.setBackgroundColor(ContextCompat.getColor(this, R.color.acento))
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tvStatus.text = "En pausa. Mantén pulsado Borrar para copiar la transcripción."
    }

    // ---- SpeechEngine.Listener ----

    override fun onPartial(text: String) {
        tvLiveEn.text = text
        val seq = ++liveSeq
        translator.translate(text) { es ->
            if (seq == liveSeq) tvLiveEs.text = es
        }
    }

    override fun onFinal(text: String) {
        val line = Line(text, "…")
        adapter.add(line)
        rvHistory.scrollToPosition(adapter.itemCount - 1)
        liveSeq++
        tvLiveEn.text = ""
        tvLiveEs.text = ""
        translator.translate(text) { es ->
            line.spanish = es
            adapter.refresh(line)
        }
    }

    override fun onStatus(text: String) {
        if (listening) tvStatus.text = text
    }

    override fun onFatalError(message: String) {
        stopListening()
        tvStatus.text = message
    }

    // ---- Utilidades ----

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
        tvLiveEn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * textScale)
        tvLiveEs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * textScale)
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
        translator.close()
    }
}
