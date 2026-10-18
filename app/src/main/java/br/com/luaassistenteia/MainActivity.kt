
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    companion object {
        private const val WORKER_URL =
            "https://nameless-tree-0cec.reberson51.workers.dev/"
    }

    private lateinit var status: TextView
    private lateinit var conversation: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var voicePicker: Spinner

    private var recognizer: SpeechRecognizer? = null
    private val executor = Executors.newSingleThreadExecutor()
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
            label("Sua assistente pessoal com IA", 18f, 0xFFFFFFFF.toInt())
        )

        status = label("Pronta para conversar", 15f, 0xFFBDB5D5.toInt())
        root.addView(status)

        conversation = label(
            "Olá! Eu sou a Lua. Toque no botão abaixo para conversar comigo.",
            17f,
            0xFFFFFFFF.toInt()
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setPadding(8, 20, 8, 20)
        }

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

        root.addView(Button(this).apply {
            text = "🎙 Falar com a Lua"
            setOnClickListener { requestMicrophoneAndListen() }
        })

        root.addView(Button(this).apply {
            text = "Limpar conversa"
            setOnClickListener {
                history.clear()
                conversation.text = "Conversa limpa."
                status.text = "Pronta para conversar"
            }
        })

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
        } else if (requestCode == 100) {
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

        recognizer?.setRecognitionListener(
            object : android.speech.RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "Estou ouvindo..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    status.text = "Preparando sua mensagem..."
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
                        status.text = "Não entendi. Tente novamente."
                        return
                    }

                    history.add("Você: $words")
                    updateConversation()
                    status.text = "Lua está pensando..."

                    askWorker(words)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )

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

    private fun askWorker(message: String) {
        executor.execute {
            var connection: HttpURLConnection? = null

            try {
                connection = (URL(WORKER_URL).openConnection()
                    as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type", "application/json; charset=UTF-8"
                    )
                    setRequestProperty("Accept", "application/json")
                }

                val payload = JSONObject()
                    .put("message", message)
                    .toString()

                connection.outputStream.use { output ->
                    output.write(
                        payload.toByteArray(StandardCharsets.UTF_8)
                    )
                }

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream?.bufferedReader(
                    Charsets.UTF_8
                )?.use { it.readText() }.orEmpty()

                val data = JSONObject(
                    responseText.ifBlank { "{}" }
                )

                if (code !in 200..299) {
                    val detail = data.optString(
                        "details",
                        data.optString("error", "Erro HTTP $code")
                    )
                    showError("Erro $code: $detail")
                    return@execute
                }

                val reply = data.optString("reply")
                    .ifBlank { data.optString("response") }
                    .ifBlank { data.optString("text") }

                if (reply.isBlank()) {
                    showError("A IA não retornou uma resposta.")
                    return@execute
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread

                    history.add("Lua: $reply")
                    updateConversation()
                    status.text = "Lua respondeu"

                    if (::tts.isInitialized) {
                        tts.speak(
                            reply,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "lua-answer"
                        )
                    }
                }
            } catch (e: Exception) {
                showError(
                    "Falha de conexão: " +
                        (e.message ?: "verifique a internet")
                )
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            status.text = message
            history.add("Lua: $message")
            updateConversation()
        }
    }

    private fun updateConversation() {
        conversation.text = history.joinToString("\n\n")
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
            val languageResult = tts.setLanguage(Locale("pt", "BR"))

            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        status.text = "Instale uma voz em português no Android"
                    }
                }
            }

            if (::voicePicker.isInitialized) {
                selectVoice(voicePicker.selectedItemPosition.coerceAtLeast(0))
            }
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }

        executor.shutdownNow()
        super.onDestroy()
    }
}
