
package br.com.luaassistenteia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var status: TextView
    private lateinit var conversation: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var voicePicker: Spinner
    private var recognizer: SpeechRecognizer? = null
    private val history = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(0xFF101020.toInt())
        }

        fun label(text: String, size: Float, color: Int) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
            }

        root.addView(label("LUA", 36f, 0xFFE2C7FF.toInt()))
        root.addView(
            label("Sua assistente pessoal", 18f, 0xFFFFFFFF.toInt())
        )

        status = label("Pronta para conversar", 15f, 0xFFBDB5D5.toInt())
        root.addView(status)

        conversation = label(
            "Olá! Eu sou a Lua. Toque no botão abaixo para falar comigo.",
            17f,
            0xFFFFFFFF.toInt()
        )
        conversation.gravity = Gravity.TOP or Gravity.START
        conversation.setPadding(8, 20, 8, 20)

        val scroll = ScrollView(this)
        scroll.addView(conversation)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        voicePicker = Spinner(this)
        voicePicker.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Voz feminina", "Voz masculina")
        )
        voicePicker.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    selectVoice(position)
                }
            }
        root.addView(voicePicker)

        val talk = Button(this).apply {
            text = "🎙 Falar com a Lua"
            setOnClickListener { requestMicrophoneAndListen() }
        }
        root.addView(talk)

        val clear = Button(this).apply {
            text = "Limpar conversa"
            setOnClickListener {
                history.clear()
                conversation.text = "Conversa limpa."
                status.text = "Pronta para conversar"
            }
        }
        root.addView(clear)

        setContentView(root)
    }

    private fun requestMicrophoneAndListen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
            return
        }
        startListening()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )
        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            status.text = "Permissão do microfone necessária"
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Reconhecimento de voz indisponível"
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(object :
            android.speech.RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                status.text = "Estou ouvindo..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status.text = "Processando..."
            }

            override fun onError(error: Int) {
                status.text = "Não consegui ouvir. Tente novamente."
            }

            override fun onResults(results: Bundle?) {
                val words = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )?.firstOrNull().orEmpty()

                if (words.isBlank()) {
                    status.text = "Não entendi"
                    return
                }

                history.add("Você: $words")
                val answer = respond(words)
                history.add("Lua: $answer")
                conversation.text = history.joinToString("\n\n")
                status.text = "Resposta pronta"

                if (::tts.isInitialized) {
                    tts.speak(
                        answer,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "lua-answer"
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com a Lua")
        }

        recognizer?.startListening(intent)
    }

    private fun respond(words: String): String {
        val text = words.lowercase(Locale("pt", "BR"))
        return when {
            "olá" in text || "oi" in text ->
                "Olá! Que bom falar com você. Como posso ajudar?"
            "seu nome" in text || "quem é você" in text ->
                "Meu nome é Lua, sua assistente pessoal."
            "que horas" in text ->
                "Você pode conferir o horário no relógio do seu celular."
            "obrigado" in text || "obrigada" in text ->
                "Por nada! Estou aqui para ajudar."
            else ->
                "Entendi o que você disse: $words. Ainda preciso conectar um serviço de inteligência artificial para responder perguntas livremente."
        }
    }

    private fun selectVoice(position: Int) {
        if (!::tts.isInitialized) return
        val voices = tts.voices ?: return
        val candidates = voices.filter {
            it.locale.language == "pt" &&
            !it.isNetworkConnectionRequired
        }
        val selected = candidates.firstOrNull {
            if (position == 0) {
                it.name.contains("female", true) ||
                    it.name.contains("femin", true)
            } else {
                it.name.contains("male", true) ||
                    it.name.contains("masc", true)
            }
        }
        if (selected != null) tts.voice = selected
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            tts.language = Locale("pt", "BR")
            selectVoice(voicePicker.selectedItemPosition.coerceAtLeast(0))
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}

