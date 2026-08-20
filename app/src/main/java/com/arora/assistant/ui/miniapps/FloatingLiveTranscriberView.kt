package com.arora.assistant.ui.miniapps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.arora.assistant.core.ai.GroqWhisperClient
import com.arora.assistant.core.ai.OfflineTranslationEngine
import com.arora.assistant.core.data.AppPreferences
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftAmber
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FloatingLiveTranscriberView(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val appPreferences = remember { AppPreferences(context) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Dual Mode: 0 = "📴 Offline Neural", 1 = "⚡ Groq Whisper"
    var selectedModeIndex by remember { mutableIntStateOf(0) }
    var groqApiKeyInput by remember { mutableStateOf("") }
    var isTestingKey by remember { mutableStateOf(false) }
    var keyValidationStatus by remember { mutableStateOf<String?>(null) }
    var isKeySavedNotice by remember { mutableStateOf(false) }
    var showGroqSettings by remember { mutableStateOf(false) }

    var isListening by remember { mutableStateOf(false) }
    var currentPartialText by remember { mutableStateOf("") }
    val transcriptList = remember { mutableStateListOf<String>() }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var isSummarizing by remember { mutableStateOf(false) }
    var meetingSummary by remember { mutableStateOf<String?>(null) }
    var opacity by remember { mutableFloatStateOf(0.85f) }
    var copiedNotice by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var groqRecorderJob by remember { mutableStateOf<Job?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    // Load saved preferences
    LaunchedEffect(Unit) {
        val savedMode = appPreferences.transcriberMode.first()
        selectedModeIndex = if (savedMode == "groq") 1 else 0
        groqApiKeyInput = appPreferences.groqApiKey.first()
    }

    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager }

    fun stopListening() {
        isListening = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
            }
        } catch (e: Exception) {}

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null

        groqRecorderJob?.cancel()
        groqRecorderJob = null
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
    }

    DisposableEffect(Unit) {
        onDispose {
            stopListening()
        }
    }

    // Start Mode A: 100% Offline Neural Recognizer
    fun startOfflineListening() {
        stopListening()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_MUTE, 0)
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
            } catch (e: Exception) {}
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (isListening) {
                    scope.launch {
                        delay(400)
                        if (isListening) {
                            try { recognizer.startListening(intent) } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val fullText = matches[0]
                    if (fullText.isNotBlank()) {
                        transcriptList.add(fullText)
                        currentPartialText = ""
                    }
                }
                if (isListening) {
                    scope.launch {
                        delay(300)
                        if (isListening) {
                            try { recognizer.startListening(intent) } catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    currentPartialText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
        isListening = true
    }

    // Start Mode B: Groq Whisper Cloud Tumbling Window Streamer
    fun startGroqListening() {
        stopListening()
        if (groqApiKeyInput.isBlank()) {
            showGroqSettings = true
            keyValidationStatus = "Please enter and save your Groq API Key first"
            return
        }

        isListening = true
        groqRecorderJob = scope.launch(Dispatchers.IO) {
            val audioDir = context.cacheDir
            var chunkIndex = 0

            while (isActive && isListening) {
                val chunkFile = File(audioDir, "groq_chunk_${chunkIndex % 3}.m4a")
                try {
                    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    mediaRecorder = recorder
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioSamplingRate(16000)
                    recorder.setAudioEncodingBitRate(32000)
                    recorder.setOutputFile(chunkFile.absolutePath)
                    recorder.prepare()
                    recorder.start()

                    delay(4000)

                    try {
                        recorder.stop()
                        recorder.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    mediaRecorder = null

                    if (chunkFile.exists() && chunkFile.length() > 500) {
                        val result = GroqWhisperClient.transcribeAudio(groqApiKeyInput, chunkFile)
                        result.onSuccess { transcribed ->
                            val clean = transcribed.trim()
                            if (clean.isNotBlank() && clean != "." && clean != "you" && clean != "Thank you." && clean != "Subtitles by...") {
                                launch(Dispatchers.Main) {
                                    transcriptList.add(clean)
                                }
                            }
                        }
                    }
                    chunkIndex++
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(1000)
                }
            }
        }
    }

    fun startListening() {
        hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPermission) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return
        }
        if (selectedModeIndex == 0) {
            startOfflineListening()
        } else {
            startGroqListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopListening()
        }
    }

    Box(
        modifier = Modifier
            .size(width = 345.dp, height = 500.dp)
            .shadow(24.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = SkyOpal.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(22.dp))
            .background(SoftDarkBg.copy(alpha = opacity))
            .border(1.2.dp, Brush.linearGradient(listOf(SkyOpal, SoftLavender)), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row (Compact Mic Permission Icon on the side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isListening) SageMint.copy(alpha = 0.2f) else PastelRose.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isListening) Icons.Default.GraphicEq else Icons.Default.MicOff,
                        null,
                        tint = if (isListening) SageMint else PastelRose,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("WhisperFlow Live Transcriber", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        if (isListening) "🟢 Streaming speech..." else "🔴 Stream Stopped",
                        color = if (isListening) SageMint else PastelRose,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Compact Mic Access Icon on Top Right
                IconButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (hasMicPermission) Icons.Default.Mic else Icons.Default.MicOff,
                        "Mic Permission",
                        tint = if (hasMicPermission) SageMint else PastelRose,
                        modifier = Modifier.size(17.dp)
                    )
                }

                IconButton(
                    onClick = { showGroqSettings = !showGroqSettings },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Tune, "Settings", tint = if (showGroqSettings) SoftLavender else TextMuted, modifier = Modifier.size(16.dp))
                }

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        stopListening()
                        onClose()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dual Mode Switcher Tab Bar
            TabRow(
                selectedTabIndex = selectedModeIndex,
                containerColor = SoftSurface.copy(alpha = 0.8f),
                contentColor = SoftLavender,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedModeIndex]),
                        color = if (selectedModeIndex == 0) SageMint else SkyOpal,
                        height = 2.5.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedModeIndex == 0,
                    onClick = {
                        selectedModeIndex = 0
                        scope.launch { appPreferences.setTranscriberMode("offline") }
                        if (isListening) startOfflineListening()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📴 Offline Neural", color = if (selectedModeIndex == 0) TextPureWhite else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedModeIndex == 1,
                    onClick = {
                        selectedModeIndex = 1
                        scope.launch { appPreferences.setTranscriberMode("groq") }
                        if (isListening) startGroqListening()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, null, tint = SkyOpal, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("⚡ Groq Whisper", color = if (selectedModeIndex == 1) SkyOpal else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Groq API Settings Panel (Expandable)
            AnimatedVisibility(visible = showGroqSettings || (selectedModeIndex == 1 && groqApiKeyInput.isBlank())) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftSurfaceElevated)
                        .border(1.dp, SoftCardBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, null, tint = SkyOpal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Groq Whisper API Key", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Info, "Get Free Groq Key", tint = SkyOpal, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Free key available at console.groq.com/keys (click ℹ️)", color = TextMuted, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = groqApiKeyInput,
                        onValueChange = {
                            groqApiKeyInput = it
                            keyValidationStatus = null
                            isKeySavedNotice = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("gsk_...", color = TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SoftSurface,
                            unfocusedContainerColor = SoftSurface,
                            focusedTextColor = TextPureWhite,
                            unfocusedTextColor = TextPureWhite,
                            focusedIndicatorColor = SkyOpal,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Half-and-Half Row: Left = Test Key (50%), Right = Save Key (50%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 50% Test Key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SoftSurface)
                                .border(1.dp, SkyOpal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        isTestingKey = true
                                        keyValidationStatus = null
                                        val res = GroqWhisperClient.testApiKey(groqApiKeyInput)
                                        isTestingKey = false
                                        if (res.isSuccess) {
                                            keyValidationStatus = "valid"
                                        } else {
                                            keyValidationStatus = res.exceptionOrNull()?.message ?: "Invalid Key"
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isTestingKey) {
                                CircularProgressIndicator(color = SkyOpal, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("🧪 Test Key", color = SkyOpal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 50% Save Key (ONLY here inside Groq settings)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SkyOpal)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    scope.launch {
                                        appPreferences.setGroqApiKey(groqApiKeyInput.trim())
                                        isKeySavedNotice = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("💾 Save Key", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isKeySavedNotice) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("✓ API Key saved permanently!", color = SageMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    keyValidationStatus?.let { status ->
                        Spacer(modifier = Modifier.height(4.dp))
                        if (status == "valid") {
                            Text("✓ Key is valid! Whisper Large-v3 ready.", color = SageMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("✗ $status", color = PastelRose, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Master Start / Stop Controls
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isListening) {
                    NeonButton(
                        text = if (selectedModeIndex == 0) "▶ Start Offline Stream" else "▶ Start Groq Whisper",
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            startListening()
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PastelRose)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                stopListening()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("■ Stop Stream", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Strip (Copy, Clear, Translate, Opacity)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SoftSurface.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val combined = (transcriptList + listOf(currentPartialText)).filter { it.isNotBlank() }.joinToString(" ")
                            if (combined.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(combined))
                                copiedNotice = true
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = if (copiedNotice) SageMint else SoftLavender, modifier = Modifier.size(15.dp))
                    }

                    IconButton(
                        onClick = {
                            transcriptList.clear()
                            currentPartialText = ""
                            translatedText = null
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Clear", tint = TextMuted, modifier = Modifier.size(15.dp))
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                val combined = (transcriptList + listOf(currentPartialText)).filter { it.isNotBlank() }.joinToString(" ")
                                if (combined.isNotBlank()) {
                                    val res = OfflineTranslationEngine.translateOnDevice(combined, "Hindi")
                                    translatedText = res.getOrNull()
                                }
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Translate, "Translate Live", tint = SkyOpal, modifier = Modifier.size(15.dp))
                    }

                    // 1-Tap AI Meeting Summary Button (Prompt 3)
                    IconButton(
                        onClick = {
                            val combined = (transcriptList + listOf(currentPartialText)).filter { it.isNotBlank() }.joinToString(" ")
                            if (combined.isNotBlank()) {
                                scope.launch {
                                    isSummarizing = true
                                    val groqApiKey = appPreferences.groqApiKey.first()
                                    val geminiApiKey = appPreferences.geminiApiKey.first()

                                    val prompt = """You are a professional meeting intelligence engine.

Analyze this transcript and extract ONLY what was explicitly said.
NEVER invent names, dates, deadlines, or facts not present in the transcript.
If something is unclear, mark it as [unclear] rather than guessing.

TRANSCRIPT:
$combined

Output STRICTLY in this format — do not add any extra sections:

## Summary
[3 sentences maximum — who talked about what and what was decided]

## Key Decisions
[List only concrete decisions reached. If none, write: "No explicit decisions recorded."]
- [decision]

## Action Items
[List only tasks explicitly assigned or volunteered. If none, write: "No action items recorded."]
- [ ] [task] — Owner: [name if mentioned, else "Unassigned"] — Due: [date if mentioned, else "Not specified"]

## Open Questions
[Unresolved items that need follow-up]
- [question]"""

                                    // 1. Try Groq LPU first
                                    if (groqApiKey.isNotBlank()) {
                                        val groq = com.arora.assistant.core.ai.GroqClient(groqApiKey)
                                        val groqRes = groq.generateContent(prompt)
                                        if (groqRes.isSuccess && !groqRes.getOrNull().isNullOrBlank()) {
                                            isSummarizing = false
                                            meetingSummary = groqRes.getOrNull()
                                            return@launch
                                        }
                                    }

                                    // 2. Fallback to Gemini
                                    if (geminiApiKey.isNotBlank()) {
                                        val client = com.arora.assistant.core.ai.GeminiClient(geminiApiKey)
                                        val res = client.generateContent(prompt)
                                        isSummarizing = false
                                        meetingSummary = res.getOrElse { "Error: ${it.message}" }
                                    } else {
                                        isSummarizing = false
                                        meetingSummary = "Add Groq or Gemini Key in Settings to generate instant AI meeting summaries & action items."
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.Bolt, "AI Meeting Summary", tint = SoftAmber, modifier = Modifier.size(15.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity", color = TextMuted, fontSize = 9.sp)
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.4f..1.0f,
                        modifier = Modifier.width(85.dp).padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(thumbColor = SoftLavender, activeTrackColor = SoftLavender)
                    )
                }
            }

            if (copiedNotice) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("✓ Transcript copied to clipboard!", color = SageMint, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Live Transcript Stream Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftSurfaceElevated.copy(alpha = 0.6f))
                    .border(1.dp, SoftCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    if (transcriptList.isEmpty() && currentPartialText.isBlank()) {
                        Text(
                            text = if (isListening) "Listening: play any video, reel, or speak near phone..." else "Tap '▶ Start' to stream live speech...",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        transcriptList.forEach { chunk ->
                            Text(
                                text = chunk,
                                color = TextPureWhite,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (currentPartialText.isNotBlank()) {
                            Text(
                                text = currentPartialText,
                                color = SkyOpal,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (isSummarizing) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SoftAmber, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating meeting summary & action items...", color = SoftAmber, fontSize = 11.sp)
                        }
                    }

                    meetingSummary?.let { summary ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftAmber.copy(alpha = 0.15f))
                                .border(1.dp, SoftAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📝 AI Meeting Summary & Actions:", color = SoftAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(summary))
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, "Copy Summary", tint = SoftAmber, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(summary, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    translatedText?.let { trans ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SoftLavender.copy(alpha = 0.2f))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("🌐 Translation (Hindi):", color = SoftLavender, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(trans, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
