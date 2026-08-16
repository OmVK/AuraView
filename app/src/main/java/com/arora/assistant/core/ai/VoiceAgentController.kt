package com.arora.assistant.core.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

class VoiceAgentController(
    private val context: Context,
    private val geminiClientProvider: () -> GeminiClient?
) : RecognitionListener {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _lastTranscript = MutableStateFlow("")
    val lastTranscript: StateFlow<String> = _lastTranscript.asStateFlow()

    private val _lastAiResponse = MutableStateFlow("")
    val lastAiResponse: StateFlow<String> = _lastAiResponse.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    fun startVoiceSession() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceState.ERROR
            return
        }

        stopVoiceSession()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@VoiceAgentController)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        _voiceState.value = VoiceState.LISTENING
        speechRecognizer?.startListening(intent)
    }

    fun stopVoiceSession() {
        tts?.stop()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _voiceState.value = VoiceState.IDLE
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        _lastTranscript.value = text

        if (text.isNotEmpty()) {
            _voiceState.value = VoiceState.THINKING
            processVoiceQuery(text)
        } else {
            _voiceState.value = VoiceState.IDLE
        }
    }

    private fun processVoiceQuery(query: String) {
        scope.launch {
            val client = geminiClientProvider()
            if (client == null) {
                speak("Please configure your Gemini API Key in AuraView settings.")
                return@launch
            }

            val prompt = "You are a voice assistant copilot. Answer concisely in 1-2 spoken sentences:\n\nUser: $query"
            val result = client.generateContent(prompt)

            val answer = result.getOrElse { "Sorry, I had trouble processing that." }
            _lastAiResponse.value = answer
            speak(answer)
        }
    }

    private fun speak(text: String) {
        _voiceState.value = VoiceState.SPEAKING
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_agent_tts")
    }

    override fun onError(error: Int) {
        _voiceState.value = VoiceState.IDLE
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            _lastTranscript.value = text
        }
    }
    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        stopVoiceSession()
        tts?.shutdown()
    }
}
