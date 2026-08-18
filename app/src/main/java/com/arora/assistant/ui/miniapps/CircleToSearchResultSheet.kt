package com.arora.assistant.ui.miniapps

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.arora.assistant.core.ai.ChatMessage
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.ai.ProblemSolverEngine
import com.arora.assistant.ui.theme.NeonEmerald
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
    initialIsVisualMode: Boolean,
    geminiClient: GeminiClient?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isVisualMode by remember { mutableStateOf(initialIsVisualMode) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("✨ AI Chat & Search", "🦁 Brave Search", "📋 Extracted Text", "📖 Translate", "📚 Study & Anki")

    // Interactive Chat History
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var isQueryingAi by remember { mutableStateOf(false) }
    var followUpInput by remember { mutableStateOf("") }

    val quickPrompts = if (!isVisualMode) {
        listOf(
            "📝 Summarize text",
            "📐 Solve / Calculate",
            "💻 Explain code",
            "📖 Translate to English",
            "🔍 Search this text"
        )
    } else {
        listOf(
            "🛍️ What product is this?",
            "📐 Solve / Calculate",
            "💻 Explain code / error",
            "🔍 Where can I buy this?",
            "📸 Describe object"
        )
    }

    fun sendChatMessage(userQuery: String) {
        if (userQuery.isBlank() || geminiClient == null) return
        val q = userQuery.trim()
        chatMessages.add(ChatMessage("user", q))
        chatMessages.add(ChatMessage("model", "⏳ Processing..."))
        val modelMsgIndex = chatMessages.size - 1
        isQueryingAi = true

        scope.launch {
            val prompt = buildString {
                when {
                    q.contains("Solve", ignoreCase = true) || q.contains("Calculate", ignoreCase = true) -> {
                        append("Solve the math equation, formula, or calculation. State the **Final Answer** directly and clearly at the top, followed by concise step-by-step calculations. Use plain, readable math symbols (+, -, *, /, ^, =, √). Do NOT use LaTeX dollar signs ($ or $$).")
                    }
                    q.contains("What product", ignoreCase = true) || q.contains("Describe object", ignoreCase = true) -> {
                        append("Identify the exact physical object, brand, or product, with details and primary purpose.")
                    }
                    q.contains("Summarize", ignoreCase = true) -> {
                        append("Summarize the key information concisely in structured bullet points.")
                    }
                    else -> {
                        append("User request: ").append(q)
                        append("\nNote: Use clean readable text/math formatting. Do not use raw LaTeX dollar signs ($ or $$).")
                    }
                }

                if (!isVisualMode && ocrText.isNotBlank()) {
                    append("\n\nExtracted Screen Text:\n\"").append(ocrText).append("\"")
                } else if (ocrText.isNotBlank()) {
                    append("\nDetected text: \"").append(ocrText).append("\"")
                }
            }

            val targetBitmap = if (isVisualMode) bitmap else null
            val result = geminiClient.streamGenerate(
                prompt = prompt,
                bitmap = targetBitmap,
                maxTokens = 1200,
                onChunk = { partial ->
                    val clean = ProblemSolverEngine.cleanMathFormatting(partial)
                    if (modelMsgIndex < chatMessages.size) {
                        chatMessages[modelMsgIndex] = ChatMessage("model", clean)
                    }
                }
            )
            isQueryingAi = false
            if (result.isFailure) {
                if (modelMsgIndex < chatMessages.size) {
                    chatMessages[modelMsgIndex] = ChatMessage("model", "Error: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    // Automatically trigger initial analysis for the selected mode
    LaunchedEffect(geminiClient) {
        if (geminiClient != null && chatMessages.isEmpty()) {
            isQueryingAi = true
            chatMessages.add(ChatMessage("model", "⚡ Analyzing with Gemini 2.0 Flash..."))
            val modelMsgIndex = 0

            val initialPrompt = if (isVisualMode) {
                if (ocrText.isNotBlank()) {
                    "Analyze this circled image and context. If it contains a math equation or formula, solve it and state the **Final Answer** directly at the top with clean math symbols and no LaTeX dollar signs ($ or $$). If it contains a product, object, or code, identify and explain it concisely."
                } else {
                    "Identify the physical object, product, brand, or visual content in this image and explain what it is."
                }
            } else {
                if (ocrText.isNotBlank()) {
                    "Here is the text extracted from the screen: \"$ocrText\"\n\nIf this contains a math equation, formula, or calculation, solve it and state the **Final Answer** directly at the top without LaTeX dollar signs ($ or $$). If it is informational text or code, provide a clear, concise breakdown."
                } else {
                    "No text was detected in the crop. Please describe what you want to search or switch to Visual Mode."
                }
            }

            val targetBitmap = if (isVisualMode) bitmap else null
            val res = geminiClient.streamGenerate(
                prompt = initialPrompt,
                bitmap = targetBitmap,
                maxTokens = 1200,
                onChunk = { partial ->
                    val clean = ProblemSolverEngine.cleanMathFormatting(partial)
                    if (modelMsgIndex < chatMessages.size) {
                        chatMessages[modelMsgIndex] = ChatMessage("model", clean)
                    }
                }
            )
            isQueryingAi = false
            if (res.isFailure) {
                if (modelMsgIndex < chatMessages.size) {
                    chatMessages[modelMsgIndex] = ChatMessage("model", "Error: ${res.exceptionOrNull()?.message}")
                }
            }
        }
    }

    var translationText by remember { mutableStateOf<String?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var studyNotesResponse by remember { mutableStateOf<String?>(null) }
    var isQueryingStudyNotes by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val searchQuery = remember(ocrText) {
        if (ocrText.isNotBlank()) ocrText.trim() else "Brave visual web search"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 470.dp, max = 640.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SoftSurfaceElevated.copy(alpha = 0.96f),
                            SoftDarkBg.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(1.dp, SoftCardBorder, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag Handle
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextMuted.copy(alpha = 0.4f))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Header with Image Reference Card & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Referenced Circled Area",
                                modifier = Modifier
                                    .size(width = 54.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.2.dp, if (isVisualMode) SoftLavender else NeonEmerald, RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isVisualMode) "📸 Visual Search" else "⚡ Text Search",
                                    color = TextPureWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isVisualMode) SoftLavender.copy(alpha = 0.2f) else NeonEmerald.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = if (isVisualMode) "Image Ref" else "Low Tokens",
                                        color = if (isVisualMode) SoftLavender else NeonEmerald,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = if (ocrText.isNotEmpty()) ocrText.take(35) + "..." else "Visual Area Referenced",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SoftSurface)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(15.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mode Switcher Pills (Allows switching anytime)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftSurface)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 1. Text Mode Pill
                    val isTextSelected = !isVisualMode
                    val textBg by animateColorAsState(if (isTextSelected) NeonEmerald.copy(alpha = 0.25f) else Color.Transparent)
                    val textBorder by animateColorAsState(if (isTextSelected) NeonEmerald else Color.Transparent)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(textBg)
                            .border(1.dp, textBorder, RoundedCornerShape(10.dp))
                            .clickable { isVisualMode = false }
                            .padding(vertical = 5.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Bolt, "Text Mode", tint = if (isTextSelected) NeonEmerald else TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "⚡ Text Mode (Low Tokens)",
                            color = if (isTextSelected) TextPureWhite else TextMuted,
                            fontWeight = if (isTextSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.5.sp
                        )
                    }

                    // 2. Visual Image Mode Pill
                    val isImgSelected = isVisualMode
                    val imgBg by animateColorAsState(if (isImgSelected) SoftLavender.copy(alpha = 0.25f) else Color.Transparent)
                    val imgBorder by animateColorAsState(if (isImgSelected) SoftLavender else Color.Transparent)

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(imgBg)
                            .border(1.dp, imgBorder, RoundedCornerShape(10.dp))
                            .clickable { isVisualMode = true }
                            .padding(vertical = 5.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Image, "Visual Mode", tint = if (isImgSelected) SoftLavender else TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "📸 Visual Mode",
                            color = if (isImgSelected) TextPureWhite else TextMuted,
                            fontWeight = if (isImgSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.5.sp
                        )
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
                            color = if (isVisualMode) SoftLavender else NeonEmerald,
                            height = 2.5.dp
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
                                    fontSize = 11.5.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Content Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftSurface.copy(alpha = 0.6f))
                        .border(1.dp, SoftCardBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(if (selectedTabIndex == 1) 0.dp else 10.dp)
                ) {
                    when (selectedTabIndex) {
                        // 0. AI Chat Stream
                        0 -> {
                            val scrollState = rememberScrollState()
                            LaunchedEffect(chatMessages.size, isQueryingAi) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            ) {
                                if (bitmap == null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SoftAmber.copy(alpha = 0.15f))
                                            .border(0.8.dp, SoftAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "⚠️ Screenshot Permission Sync: Android requires toggling Accessibility OFF and ON once in Settings to register screenshot capture capability.",
                                            color = SoftAmber,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(SoftAmber)
                                                .clickable {
                                                    try {
                                                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Open Settings -> Accessibility", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "⚙️ Open Accessibility Settings",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (chatMessages.isEmpty() && isQueryingAi) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = if (isVisualMode) SoftLavender else NeonEmerald,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (isVisualMode) "Gemini is analyzing visual area..." else "Gemini is processing extracted text...",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    chatMessages.forEach { msg ->
                                        if (msg.role == "user") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isVisualMode) SoftLavender.copy(alpha = 0.85f) else NeonEmerald.copy(alpha = 0.85f))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text(msg.text, color = Color.White, fontSize = 12.5.sp)
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.Start
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(SoftSurfaceElevated)
                                                        .border(0.8.dp, SoftCardBorder, RoundedCornerShape(12.dp))
                                                        .padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = msg.text,
                                                        color = TextPureWhite,
                                                        fontSize = 13.sp,
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isQueryingAi) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = if (isVisualMode) SoftLavender else NeonEmerald,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Gemini thinking...", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Brave Web Search Tab
                        1 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
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
                                        text = "🦁 Brave: $searchQuery",
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
                                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                            webViewClient = WebViewClient()
                                            webChromeClient = WebChromeClient()
                                            loadUrl("https://search.brave.com/search?q=" + Uri.encode(searchQuery))
                                            webViewInstance = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // 2. Extracted Text Tab
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

                        // 3. Translate Tab
                        3 -> {
                            var selectedLang by remember { mutableStateOf("Nepali") }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Nepali" to "🇳🇵 Nepali", "Hindi" to "🇮🇳 Hindi", "Spanish" to "🇪🇸 Spanish", "French" to "🇫🇷 French").forEach { (k, label) ->
                                        val isSel = selectedLang == k
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) SoftLavender else SoftSurface)
                                                .clickable {
                                                    selectedLang = k
                                                    if (ocrText.isNotBlank() && geminiClient != null) {
                                                        scope.launch {
                                                            isTranslating = true
                                                            val res = geminiClient.generateContent("Translate this text accurately to $k:\n\n$ocrText")
                                                            isTranslating = false
                                                            translationText = res.getOrNull()
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(label, color = if (isSel) Color.White else TextOffWhite, fontSize = 11.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = translationText ?: "Select a language above to translate.",
                                    color = TextPureWhite,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // 4. Study & Anki Flashcards
                        4 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (studyNotesResponse == null && !isQueryingStudyNotes && geminiClient != null) {
                                    LaunchedEffect(Unit) {
                                        isQueryingStudyNotes = true
                                        val prompt = "Create structured study notes and 3 Q&A flashcards for this content:\n\n$ocrText"
                                        val res = geminiClient.generateContent(prompt, bitmap = bitmap)
                                        isQueryingStudyNotes = false
                                        studyNotesResponse = res.getOrNull()
                                    }
                                }
                                if (isQueryingStudyNotes) {
                                    CircularProgressIndicator(color = SoftLavender, modifier = Modifier.size(20.dp))
                                } else {
                                    Text(
                                        text = studyNotesResponse ?: "Add Gemini Key in Settings to generate flashcards.",
                                        color = TextPureWhite,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Interactive Bottom Bar (Only on Tab 0: AI Chat)
                if (selectedTabIndex == 0) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Question Suggestion Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickPrompts) { promptText ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SoftSurface)
                                    .border(0.8.dp, if (isVisualMode) SoftLavender.copy(alpha = 0.4f) else NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .clickable { sendChatMessage(promptText) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(promptText, color = TextOffWhite, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // User Question Input Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = followUpInput,
                            onValueChange = { followUpInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    text = if (isVisualMode) "Ask about circled visual area..." else "Ask about extracted text...",
                                    fontSize = 11.5.sp,
                                    color = TextSubtle
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SoftSurface,
                                unfocusedContainerColor = SoftSurface,
                                focusedTextColor = TextPureWhite,
                                unfocusedTextColor = TextPureWhite,
                                focusedIndicatorColor = if (isVisualMode) SoftLavender else NeonEmerald,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                if (followUpInput.isNotBlank()) {
                                    val query = followUpInput
                                    followUpInput = ""
                                    sendChatMessage(query)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isVisualMode) SoftLavender else NeonEmerald)
                        ) {
                            Icon(Icons.Default.Send, "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
