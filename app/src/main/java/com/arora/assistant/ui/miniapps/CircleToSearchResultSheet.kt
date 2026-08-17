package com.arora.assistant.ui.miniapps

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arora.assistant.core.ai.GeminiClient
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
import com.arora.assistant.ui.theme.TextSubtle
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CircleToSearchResultSheet(
    bitmap: Bitmap?,
    ocrText: String,
    initialAiSolution: String?,
    isLoading: Boolean,
    geminiClient: GeminiClient?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("🧠 AI Solution", "🌐 Web Search", "📋 Extracted Text", "📖 Translate", "🎭 Tone Analysis", "📚 Study & Anki")

    var aiResponse by remember(initialAiSolution) { mutableStateOf(initialAiSolution) }
    var isQueryingAi by remember(isLoading) { mutableStateOf(isLoading) }

    var toneResponse by remember { mutableStateOf<String?>(null) }
    var isQueryingTone by remember { mutableStateOf(false) }

    var studyNotesResponse by remember { mutableStateOf<String?>(null) }
    var isQueryingStudyNotes by remember { mutableStateOf(false) }

    var followUpInput by remember { mutableStateOf("") }
    var translationText by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }

    // In-Box Embedded WebView State
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val searchQuery = remember(ocrText) {
        if (ocrText.isNotBlank()) ocrText.trim() else "Google Lens visual search"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp, max = 580.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SoftSurfaceElevated.copy(alpha = 0.94f),
                            SoftDarkBg.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle & Header
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Exact Circled Image Thumbnail Preview
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Circled Target",
                            modifier = Modifier
                                .size(width = 50.dp, height = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                                .border(1.dp, SoftCardBorder, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Circle to Search & Solve",
                            color = TextPureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (ocrText.isNotEmpty()) ocrText.take(40) + "..." else "Visual Area Captured",
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(SoftSurface)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = SoftLavender,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = SoftLavender,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    color = if (selectedTabIndex == index) TextPureWhite else TextMuted,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoftSurface.copy(alpha = 0.7f))
                        .border(1.dp, SoftCardBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(if (selectedTabIndex == 1) 0.dp else 12.dp)
                ) {
                    when (selectedTabIndex) {
                        // 1. AI Solution & Step-by-Step Proof
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (isQueryingAi) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SoftLavender, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Gemini 1.5 Flash analyzing visual area...", color = TextMuted, fontSize = 12.sp)
                                    }
                                } else {
                                    Text(
                                        text = aiResponse ?: "No solution generated. Configure Gemini API Key in Settings.",
                                        color = TextPureWhite,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // 2. IN-BOX Embedded Web Search (Direct Inside the Box!)
                        1 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Web Toolbar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SoftSurface)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { webViewInstance?.goBack() },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, "Back", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Search: $searchQuery",
                                        color = SkyOpal,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { webViewInstance?.reload() },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, "Reload", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }

                                // Embedded WebView
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                                            webViewClient = WebViewClient()
                                            webChromeClient = WebChromeClient()

                                            val url = "https://www.google.com/search?q=" + Uri.encode(searchQuery)
                                            loadUrl(url)
                                            webViewInstance = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // 3. Extracted Text Tab
                        2 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Circled OCR Text", color = TextMuted, fontSize = 11.sp)
                                    IconButton(
                                        onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("Circled Text", ocrText))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, "Copy", tint = SoftLavender, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = ocrText.ifEmpty { "No text detected in circled region." },
                                    color = TextOffWhite,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // 4. Google Translate Style Dual-Box Tab
                        3 -> {
                            val availableLanguages = listOf(
                                "Nepali" to "🇳🇵 Nepali",
                                "Arabic" to "🇸🇦 Arabic",
                                "Korean" to "🇰🇷 Korean",
                                "Hindi" to "🇮🇳 Hindi",
                                "English" to "🇬🇧 English",
                                "Spanish" to "🇪🇸 Spanish",
                                "Japanese" to "🇯🇵 Japanese",
                                "French" to "🇫🇷 French",
                                "German" to "🇩🇪 German"
                            )
                            var selectedTargetLang by remember { mutableStateOf("Nepali") }
                            var isAutoTranslating by remember { mutableStateOf(false) }

                            var isOfflineEngine by remember { mutableStateOf(true) }

                            fun performTranslation(target: String) {
                                selectedTargetLang = target
                                if (ocrText.isBlank()) {
                                    translationText = "No text detected to translate."
                                    return
                                }
                                scope.launch {
                                    isAutoTranslating = true
                                    val mlKitCode = com.arora.assistant.core.ai.OfflineTranslationEngine.getMlKitLanguageCode(target)

                                    if (mlKitCode != null) {
                                        // 100% On-Device Neural Translation (Zero API Key needed!)
                                        isOfflineEngine = true
                                        val result = com.arora.assistant.core.ai.OfflineTranslationEngine.translateOnDevice(ocrText, target)
                                        isAutoTranslating = false
                                        translationText = result.getOrElse { "Offline translation error: ${it.message}" }
                                    } else {
                                        // Nepali or cloud fallback
                                        isOfflineEngine = false
                                        val client = geminiClient
                                        if (client != null) {
                                            val prompt = "Translate the following text accurately into $target. Output ONLY the natural translation without commentary or quotes:\n\n$ocrText"
                                            val result = client.generateContent(prompt)
                                            isAutoTranslating = false
                                            translationText = result.getOrElse { "Translation error: ${it.message}" }
                                        } else {
                                            // Fallback to Hindi translation on-device as closest Devanagari model
                                            val hindiResult = com.arora.assistant.core.ai.OfflineTranslationEngine.translateOnDevice(ocrText, "Hindi")
                                            isAutoTranslating = false
                                            translationText = hindiResult.getOrElse { "Add Gemini Key for Nepali, or select Hindi/Arabic/Korean for 100% offline translation." }
                                        }
                                    }
                                }
                            }

                            // Auto-trigger translation whenever Translate tab is open
                            androidx.compose.runtime.LaunchedEffect(selectedTargetLang, selectedTabIndex) {
                                if (selectedTabIndex == 3 && ocrText.isNotBlank()) {
                                    performTranslation(selectedTargetLang)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Quick Language Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Nepali" to "🇳🇵 Nepali", "Arabic" to "🇸🇦 Arabic", "Korean" to "🇰🇷 Korean", "Hindi" to "🇮🇳 Hindi", "English" to "🇬🇧 English").forEach { (langKey, label) ->
                                        val isSelected = selectedTargetLang == langKey
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) SoftLavender else SoftSurface)
                                                .border(1.dp, if (isSelected) SoftLavender else SoftCardBorder, RoundedCornerShape(12.dp))
                                                .clickable { performTranslation(langKey) }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) Color.White else TextOffWhite,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Box 1: Source Box (Original Text)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SoftDarkBg.copy(alpha = 0.85f))
                                        .border(1.dp, SoftCardBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Detected Source Text", color = SkyOpal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            IconButton(
                                                onClick = {
                                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cb.setPrimaryClip(ClipData.newPlainText("Source Text", ocrText))
                                                    Toast.makeText(context, "Copied original text", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, "Copy", tint = TextMuted, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = ocrText.ifEmpty { "No text detected in circled area." },
                                            color = TextOffWhite,
                                            fontSize = 13.sp,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Box 2: Target Box (Translated Result)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SoftSurfaceElevated.copy(alpha = 0.9f))
                                        .border(1.dp, SoftLavender.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Translation ($selectedTargetLang)", color = SoftLavender, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                if (isOfflineEngine) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(SageMint.copy(alpha = 0.2f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text("⚡ Offline On-Device", color = SageMint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                            Row {
                                                if (translationText != null) {
                                                    IconButton(
                                                        onClick = {
                                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cb.setPrimaryClip(ClipData.newPlainText("Translation", translationText))
                                                            Toast.makeText(context, "Copied translation", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.ContentCopy, "Copy", tint = SoftLavender, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (isAutoTranslating) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SoftLavender, strokeWidth = 2.dp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Translating to $selectedTargetLang...", color = TextMuted, fontSize = 12.sp)
                                            }
                                        } else {
                                            Text(
                                                text = translationText ?: "Translating...",
                                                color = TextPureWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 19.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Tone Analysis & Social Subtext Tab
                        4 -> {
                            androidx.compose.runtime.LaunchedEffect(selectedTabIndex) {
                                if (toneResponse == null && geminiClient != null && ocrText.isNotBlank()) {
                                    isQueryingTone = true
                                    val result = com.arora.assistant.core.ai.ToneAnalyzer.analyzeMessageTone(geminiClient, ocrText)
                                    isQueryingTone = false
                                    toneResponse = result.getOrElse { "Error: ${it.message}" }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🎭 Communication Subtext & Replies", color = SoftLavender, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (toneResponse != null) {
                                        IconButton(
                                            onClick = {
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("Tone Analysis", toneResponse))
                                                Toast.makeText(context, "Copied tone breakdown", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, "Copy", tint = TextMuted, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                if (isQueryingTone) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SoftLavender, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Analyzing tone and hidden intent...", color = TextMuted, fontSize = 12.sp)
                                    }
                                } else {
                                    Text(
                                        text = toneResponse ?: if (ocrText.isBlank()) "No text detected to analyze." else "Add Gemini API Key in Settings to analyze message tone.",
                                        color = TextPureWhite,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // 6. Lecture Notes & Anki Flashcards Deck Tab
                        5 -> {
                            androidx.compose.runtime.LaunchedEffect(selectedTabIndex) {
                                if (studyNotesResponse == null && geminiClient != null) {
                                    isQueryingStudyNotes = true
                                    val result = com.arora.assistant.core.ai.LectureNoteProcessor.processLectureFrame(geminiClient, bitmap, ocrText)
                                    isQueryingStudyNotes = false
                                    studyNotesResponse = result.getOrElse { "Error: ${it.message}" }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📚 Study Deck & Flashcards", color = SageMint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (studyNotesResponse != null) {
                                        IconButton(
                                            onClick = {
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("Anki Deck", studyNotesResponse))
                                                Toast.makeText(context, "Copied Study Notes / Anki Deck", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, "Copy", tint = TextMuted, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                if (isQueryingStudyNotes) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SageMint, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Generating study notes & Anki cards...", color = TextMuted, fontSize = 12.sp)
                                    }
                                } else {
                                    Text(
                                        text = studyNotesResponse ?: "Add Gemini API Key in Settings to generate study notes & Anki flashcards.",
                                        color = TextPureWhite,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Ask Follow-up Input Bar (ONLY VISIBLE ON TAB 0: AI Solution)
                if (selectedTabIndex == 0) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = followUpInput,
                            onValueChange = { followUpInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask follow-up about this solution...", fontSize = 12.sp, color = TextSubtle) },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SoftSurface,
                                unfocusedContainerColor = SoftSurface,
                                focusedTextColor = TextPureWhite,
                                unfocusedTextColor = TextPureWhite,
                                focusedIndicatorColor = SoftLavender,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (followUpInput.isNotBlank() && geminiClient != null) {
                                    scope.launch {
                                        val query = followUpInput.trim()
                                        followUpInput = ""
                                        isQueryingAi = true
                                        val prompt = "Context: $ocrText\n\nUser Question about circled image/screen: $query"
                                        val result = geminiClient.generateContent(prompt, bitmap = bitmap)
                                        isQueryingAi = false
                                        aiResponse = result.getOrElse { "Error: ${it.message}" }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SoftLavender)
                        ) {
                            Icon(Icons.Default.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
