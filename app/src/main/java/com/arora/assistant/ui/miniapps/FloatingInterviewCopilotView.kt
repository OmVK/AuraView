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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.arora.assistant.core.ai.GroqClient
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
    var rmsLevel by remember { mutableFloatStateOf(0f) }

    val answerScrollState = rememberScrollState()
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var autoScrollSpeed by remember { mutableFloatStateOf(1.0f) }

    // Auto-scroll follow while streaming live chunks
    LaunchedEffect(generatedAnswer) {
        if (isAutoScrollEnabled && !generatedAnswer.isNullOrBlank() && isGenerating) {
            answerScrollState.animateScrollTo(answerScrollState.maxValue)
        }
    }

    // Hands-free teleprompter scroll after generation finishes
    LaunchedEffect(isGenerating, generatedAnswer, isAutoScrollEnabled, autoScrollSpeed) {
        if (!isGenerating && !generatedAnswer.isNullOrBlank() && isAutoScrollEnabled) {
            kotlinx.coroutines.delay(1200)
            while (isAutoScrollEnabled && answerScrollState.value < answerScrollState.maxValue) {
                kotlinx.coroutines.delay((110 / autoScrollSpeed).toLong().coerceAtLeast(25L))
                val nextPos = (answerScrollState.value + 3).coerceAtMost(answerScrollState.maxValue)
                answerScrollState.scrollTo(nextPos)
            }
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val apiKey by appPreferences.geminiApiKey.collectAsState(initial = "")
    val groqApiKey by appPreferences.groqApiKey.collectAsState(initial = "")
    val preferredEngine by appPreferences.preferredAiEngine.collectAsState(initial = "groq")

    // Target Company / Role / Topic Context Info
    var contextInfo by remember { mutableStateOf("") }
    var isContextEditorOpen by remember { mutableStateOf(false) }

    val sessionManager = remember(apiKey, groqApiKey, preferredEngine) {
        ChatSessionManager(apiKey = apiKey, groqApiKey = groqApiKey, activeEngine = preferredEngine)
    }

    // Audio Manager to silence continuous speech recognition earcon beeps on Samsung devices (e.g. S24 Ultra)
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListening() {
        isListening = false
        rmsLevel = 0f
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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
    }

    fun triggerUniversalAnswerGeneration(promptQuestion: String) {
        if (promptQuestion.isBlank()) return
        if (preferredEngine == "groq" && groqApiKey.isBlank()) {
            generatedAnswer = "⚠️ Groq is active. Please enter your Groq API Key in Settings."
            return
        }
        if (preferredEngine == "gemini" && apiKey.isBlank()) {
            generatedAnswer = "⚠️ Gemini is active. Please enter your Gemini API Key in Settings."
            return
        }

        // Auto-pause mic immediately so the candidate's own voice reading the answer is not picked up
        stopListening()

        scope.launch {
            isGenerating = true
            generatedAnswer = ""
            sessionManager.contextInfo = contextInfo
            val result = sessionManager.sendMessageStream(
                userMessage = promptQuestion.trim(),
                onChunk = { partial ->
                    generatedAnswer = partial
                }
            )
            isGenerating = false
            if (result.isFailure) {
                generatedAnswer = "Error: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun startListening() {
        hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            currentSpeechStream = "⚠️ Microphone permission not granted. Please enable in Settings."
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            currentSpeechStream = "⚠️ Speech recognizer service not available on this device."
            return
        }

        stopListening()
        isListening = true

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.ADJUST_MUTE, 0)
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.ADJUST_MUTE, 0)
            } catch (e: Exception) {}
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    rmsLevel = (rmsdB / 10f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (isListening && !isGenerating) {
                        scope.launch {
                            kotlinx.coroutines.delay(250)
                            if (isListening && !isGenerating) {
                                startListening()
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (isGenerating) return

                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val fullText = matches[0]
                        if (fullText.isNotBlank()) {
                            currentSpeechStream = fullText
                            detectedQuestion = fullText
                            triggerUniversalAnswerGeneration(fullText)
                            return
                        }
                    }
                    if (isListening && !isGenerating) {
                        scope.launch {
                            kotlinx.coroutines.delay(250)
                            if (isListening && !isGenerating) {
                                startListening()
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (isGenerating || !isListening) return

                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        if (text.isNotBlank()) {
                            currentSpeechStream = text
                            if (text.length > 15 && (text.contains("what", true) || text.contains("how", true) || text.contains("tell me", true) || text.contains("explain", true) || text.contains("why", true) || text.contains("describe", true) || text.contains("design", true))) {
                                detectedQuestion = text
                            }
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            isListening = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopListening()
        }
    }

    // Camouflaged Panic Mode
    if (isCamouflaged) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E))
                .padding(14.dp)
                .clickable { isCamouflaged = false }
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = SkyOpal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sprint Meeting Notes", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
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
        // Master Smart Action Bar
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftSurfaceElevated)
                        .border(1.dp, SkyOpal.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = SkyOpal, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("⚡ Generating Answer...", color = ElectricCyan, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                    }
                }
            } else if (isListening) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("■ Listening to Interviewer (Tap to Pause)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            repeat(4) { index ->
                                val barHeight = (8 + (rmsLevel * 14 * (index + 1) / 4)).dp
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(Color.White.copy(alpha = 0.9f))
                                )
                            }
                        }
                    }
                }
            } else if (generatedAnswer != null) {
                NeonButton(
                    text = "🎙️ Listen for Next Question",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        startListening()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                )
            } else {
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
                        val focusRequester = com.arora.assistant.core.overlay.LocalWindowFocusRequester.current

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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        focusRequester?.invoke(focusState.isFocused)
                                    }
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
                    Text(if (preferredEngine == "groq") "⚡ Groq LPU generating answer..." else "⚡ Gemini Flash generating answer...", color = TextMuted, fontSize = 11.sp)
                }
            } else if (generatedAnswer != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(answerScrollState)
                            .padding(bottom = 26.dp)
                    ) {
                        Text(
                            text = generatedAnswer!!,
                            color = TextPureWhite,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45f).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Floating Auto-Scroll HUD Pill (Bottom End)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SoftSurfaceElevated.copy(alpha = 0.95f))
                            .border(0.8.dp, SkyOpal.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                isAutoScrollEnabled = !isAutoScrollEnabled
                            }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAutoScrollEnabled) "📜 Auto-Scroll: ON" else "📜 Auto-Scroll: OFF",
                            color = if (isAutoScrollEnabled) NeonEmerald else TextMuted,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
            fun applyRefiner(promptBuilder: (String) -> String) {
                val current = generatedAnswer
                if (current.isNullOrBlank()) return
                scope.launch {
                    isGenerating = true
                    val prompt = promptBuilder(current)
                    val candidateSys = buildString {
                        append("You are the human candidate sitting in a job interview. Always answer directly in the first person ('I') as the job applicant speaking out loud. Never refer to yourself as an AI assistant or language model.")
                        if (contextInfo.isNotBlank()) {
                            append("\nTarget Role & Company: ${contextInfo.trim()}")
                        }
                    }
                    if (preferredEngine == "groq") {
                        if (groqApiKey.isBlank()) {
                            isGenerating = false
                            generatedAnswer = "⚠️ Groq is active. Please enter your Groq API Key in Settings."
                            return@launch
                        }
                        val groq = GroqClient(groqApiKey)
                        val res = groq.generateContent(prompt, systemInstruction = candidateSys)
                        isGenerating = false
                        generatedAnswer = res.getOrElse { "Error: ${it.message}" }
                    } else {
                        if (apiKey.isBlank()) {
                            isGenerating = false
                            generatedAnswer = "⚠️ Gemini is active. Please enter your Gemini API Key in Settings."
                            return@launch
                        }
                        val client = GeminiClient(apiKey)
                        val res = client.generateContent(prompt, systemInstruction = candidateSys)
                        isGenerating = false
                        generatedAnswer = res.getOrElse { "Error: ${it.message}" }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    RefinerChip("⏱️ 15s Pitch") {
                        applyRefiner { ans ->
                            """Condense this answer into a 15-second spoken pitch (max 40 words).
Keep only the most impressive metric and the core action.
Answer:
$ans"""
                        }
                    }
                }
                item {
                    RefinerChip("📈 Add Metrics") {
                        applyRefiner { ans ->
                            """Enrich this answer with impressive numbers, percentages, benchmark latencies, or business ROI.
If no real numbers exist, use realistic industry benchmarks and label them as "industry average".
Answer:
$ans"""
                        }
                    }
                }
                item {
                    RefinerChip("⚖️ Trade-offs") {
                        applyRefiner { ans ->
                            """Add 2 alternative approaches to this answer with clear trade-offs.
Format: "Alternative A: [approach] — best when [condition]. Alternative B: [approach] — best when [condition]."
Answer:
$ans"""
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
