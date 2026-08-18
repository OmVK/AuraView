package com.arora.assistant.ui.miniapps

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.core.ai.ChatSessionManager
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.data.AppPreferences
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.NeonEmerald
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

@Composable
fun FloatingInterviewCopilotView(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val appPreferences = remember { AppPreferences(context) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var isListening by remember { mutableStateOf(false) }
    var detectedQuestion by remember { mutableStateOf("") }
    var currentSpeechStream by remember { mutableStateOf("") }
    var generatedAnswer by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(0.92f) }
    var fontSizeSp by remember { mutableFloatStateOf(13.5f) }
    var isCamouflaged by remember { mutableStateOf(false) }
    var copiedNotice by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }

    // Target Company / Role / Topic Context Info
    var contextInfo by remember { mutableStateOf("") }
    var isContextEditorOpen by remember { mutableStateOf(false) }

    val sessionManager = remember(apiKey) {
        if (apiKey.isNotBlank()) ChatSessionManager(apiKey) else null
    }

    // Load saved API Key
    LaunchedEffect(Unit) {
        apiKey = appPreferences.geminiApiKey.first()
    }

    // Audio Manager to silence continuous speech recognition earcon beeps on Samsung devices (e.g. S24 Ultra)
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager }

    // Speech Recognizer Lifecycle
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
    }

    fun triggerUniversalAnswerGeneration(promptQuestion: String) {
        if (promptQuestion.isBlank()) return
        if (apiKey.isBlank()) {
            generatedAnswer = "⚠️ Please set your Gemini API Key in Settings to generate live answers."
            return
        }

        scope.launch {
            isGenerating = true
            val manager = sessionManager ?: ChatSessionManager(apiKey)
            manager.contextInfo = contextInfo
            val result = manager.sendMessage(
                userMessage = promptQuestion.trim()
            )
            isGenerating = false
            generatedAnswer = result.getOrElse { "Error: ${it.message}" }
        }
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (isListening) {
                    scope.launch {
                        kotlinx.coroutines.delay(400)
                        if (isListening) {
                            try {
                                speechRecognizer.startListening(recognizerIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val fullText = matches[0]
                    currentSpeechStream = fullText
                    detectedQuestion = fullText
                    triggerUniversalAnswerGeneration(fullText)
                }
                if (isListening) {
                    scope.launch {
                        kotlinx.coroutines.delay(300)
                        if (isListening) {
                            try {
                                speechRecognizer.startListening(recognizerIntent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partials.isNullOrEmpty()) {
                    val text = partials[0]
                    currentSpeechStream = text
                    if (text.length > 20 && (text.contains("what", true) || text.contains("how", true) || text.contains("tell me", true) || text.contains("explain", true) || text.contains("why", true) || text.contains("describe", true) || text.contains("design", true))) {
                        detectedQuestion = text
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer.setRecognitionListener(listener)
        onDispose {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                    audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
                }
            } catch (e: Exception) {}
            speechRecognizer.destroy()
        }
    }

    fun startListening() {
        isListening = true
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_MUTE, 0)
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
            }
            speechRecognizer.startListening(recognizerIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopListening() {
        isListening = false
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_UNMUTE, 0)
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_UNMUTE, 0)
            }
            speechRecognizer.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Camouflage Disguise Mode (Emergency 1-tap disguise as Notes)
    if (isCamouflaged) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Meeting Notes & Agendas", color = TextPureWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = { isCamouflaged = false },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Visibility, "Restore AI View", tint = SkyOpal, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftSurface)
                    .padding(12.dp)
            ) {
                Text(
                    text = "• Sprint Goals & Deliverables\n• Technical Debt & Pipeline Improvements\n• Performance Optimization Targets\n• Next Steps for Q4",
                    color = TextOffWhite,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        // Master 1-Tap Start / Stop Live Listening Bar
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isListening) {
                NeonButton(
                    text = "▶ Start Live AI Copilot",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        startListening()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
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
                        Text("■ Stop Listening", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Target Company / Role Context Info Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SoftSurface.copy(alpha = 0.9f))
                .border(0.8.dp, SkyOpal.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isContextEditorOpen = !isContextEditorOpen }
                    ) {
                        Icon(Icons.Default.Business, null, tint = SkyOpal, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (contextInfo.isNotBlank()) contextInfo else "🏢 Add Target Company / Role Context",
                            color = if (contextInfo.isNotBlank()) ElectricCyan else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isContextEditorOpen = !isContextEditorOpen },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = if (isContextEditorOpen) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Edit Context",
                                tint = SkyOpal,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        if (contextInfo.isNotBlank()) {
                            IconButton(
                                onClick = { contextInfo = "" },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Clear, "Clear Context", tint = PastelRose, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isContextEditorOpen) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(SoftSurfaceElevated)
                                .border(0.8.dp, SoftLavender.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (contextInfo.isEmpty()) {
                                Text("e.g. Google - Senior Android Engineer, or Physics Viva Exam", color = TextMuted, fontSize = 10.sp)
                            }
                            BasicTextField(
                                value = contextInfo,
                                onValueChange = { contextInfo = it },
                                textStyle = TextStyle(color = TextPureWhite, fontSize = 11.sp),
                                cursorBrush = SolidColor(SkyOpal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live Controls Strip (Session Reset, Equalizer, Panic Disguise, Opacity)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SoftSurface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isListening) {
                    LiquidAudioVisualizer(modifier = Modifier.width(70.dp).height(18.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Live", color = SageMint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    val turnCount = (sessionManager?.getHistory()?.size ?: 0) / 2
                    Text(
                        text = if (turnCount > 0) "💬 Turn $turnCount (Memory Active)" else "🛡️ Stealth Mode",
                        color = if (turnCount > 0) ElectricCyan else SkyOpal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Session Reset / Clear Button
                IconButton(
                    onClick = {
                        sessionManager?.clearHistory()
                        generatedAnswer = null
                        detectedQuestion = ""
                        currentSpeechStream = ""
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, "Clear Chat Memory", tint = SoftLavender, modifier = Modifier.size(15.dp))
                }

                IconButton(
                    onClick = {
                        if (detectedQuestion.isNotBlank()) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            triggerUniversalAnswerGeneration(detectedQuestion)
                        }
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Re-Generate", tint = SkyOpal, modifier = Modifier.size(15.dp))
                }

                IconButton(
                    onClick = { isCamouflaged = true },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Security, "Panic Disguise", tint = SoftAmber, modifier = Modifier.size(15.dp))
                }

                Text("Opacity", color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(start = 2.dp))
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.35f..1.0f,
                    modifier = Modifier.width(65.dp).padding(horizontal = 2.dp),
                    colors = SliderDefaults.colors(thumbColor = SoftLavender, activeTrackColor = SoftLavender)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Detected Live Question Strip
        if (currentSpeechStream.isNotBlank() || detectedQuestion.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SoftSurfaceElevated.copy(alpha = 0.85f))
                    .border(0.8.dp, SkyOpal.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎙️ ", fontSize = 10.sp)
                    Text(
                        text = if (currentSpeechStream.isNotBlank()) currentSpeechStream else detectedQuestion,
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Main AI Answer Stream Card (Sub-Camera Placement)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(SoftDarkBg.copy(alpha = opacity))
                .border(1.2.dp, Brush.verticalGradient(listOf(SkyOpal.copy(alpha = 0.5f), SoftCardBorder)), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            if (isGenerating) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = SkyOpal, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("⚡ Gemini Flash generating answer...", color = TextMuted, fontSize = 11.sp)
                }
            } else if (generatedAnswer != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = generatedAnswer!!,
                        color = TextPureWhite,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.45f).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = SoftLavender.copy(alpha = 0.5f), modifier = Modifier.size(34.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap 'Start Live AI Copilot' above", color = TextPureWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Listens to any interview, viva, or technical question and answers instantly", color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Refiners & Copy Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SoftSurface.copy(alpha = 0.8f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    RefinerChip("⏱️ 15s Pitch") {
                        if (generatedAnswer != null && apiKey.isNotBlank()) {
                            scope.launch {
                                isGenerating = true
                                val client = GeminiClient(apiKey)
                                val prompt = """Condense this answer into a 15-second spoken pitch (max 40 words).
Keep only the most impressive metric and the core action.
Answer:
$generatedAnswer"""
                                val res = client.generateContent(prompt)
                                isGenerating = false
                                generatedAnswer = res.getOrElse { "Error: ${it.message}" }
                            }
                        }
                    }
                }
                item {
                    RefinerChip("📈 Add Metrics") {
                        if (generatedAnswer != null && apiKey.isNotBlank()) {
                            scope.launch {
                                isGenerating = true
                                val client = GeminiClient(apiKey)
                                val prompt = """Enrich this answer with impressive numbers, percentages, benchmark latencies, or business ROI.
If no real numbers exist, use realistic industry benchmarks and label them as "industry average".
Answer:
$generatedAnswer"""
                                val res = client.generateContent(prompt)
                                isGenerating = false
                                generatedAnswer = res.getOrElse { "Error: ${it.message}" }
                            }
                        }
                    }
                }
                item {
                    RefinerChip("⚖️ Trade-offs") {
                        if (generatedAnswer != null && apiKey.isNotBlank()) {
                            scope.launch {
                                isGenerating = true
                                val client = GeminiClient(apiKey)
                                val prompt = """Add 2 alternative approaches to this answer with clear trade-offs.
Format: "Alternative A: [approach] — best when [condition]. Alternative B: [approach] — best when [condition]."
Answer:
$generatedAnswer"""
                                val res = client.generateContent(prompt)
                                isGenerating = false
                                generatedAnswer = res.getOrElse { "Error: ${it.message}" }
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    if (generatedAnswer != null) {
                        clipboardManager.setText(AnnotatedString(generatedAnswer!!))
                        copiedNotice = true
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (copiedNotice) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy Answer",
                    tint = if (copiedNotice) NeonEmerald else SkyOpal,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun RefinerChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SoftSurfaceElevated)
            .border(0.8.dp, SkyOpal.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextOffWhite, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LiquidAudioVisualizer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidAudio")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2

        val barCount = 12
        val barWidth = width / (barCount * 1.8f)

        for (i in 0 until barCount) {
            val x = i * (width / barCount) + barWidth / 2
            val normX = (i.toFloat() / barCount) * (2 * Math.PI).toFloat()
            val waveHeight = (sin(normX + phase) * (height * 0.4f) + (height * 0.45f)).coerceIn(4f, height)

            val color = if (i % 2 == 0) SkyOpal else SoftLavender

            drawLine(
                color = color,
                start = Offset(x, midY - waveHeight / 2),
                end = Offset(x, midY + waveHeight / 2),
                strokeWidth = barWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}
