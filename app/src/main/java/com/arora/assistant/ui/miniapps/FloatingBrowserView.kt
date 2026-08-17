package com.arora.assistant.ui.miniapps

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arora.assistant.ui.components.GlassCard
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextPureWhite

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingBrowserView(
    initialUrl: String = "https://search.brave.com",
    onClose: () -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrl by remember { mutableStateOf("") }
    var webViewInstance: WebView? by remember { mutableStateOf(null) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp),
        borderGlow = true
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            // Header & Address Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { webViewInstance?.goBack() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    placeholder = { Text("🦁 Search Brave or type URL...", fontSize = 11.5.sp, color = TextMuted) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = SkyOpal, modifier = Modifier.size(16.dp)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val trimmed = inputUrl.trim()
                        val target = when {
                            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
                            else -> "https://search.brave.com/search?q=" + Uri.encode(trimmed)
                        }
                        currentUrl = target
                        webViewInstance?.loadUrl(target)
                    }),
                    shape = RoundedCornerShape(10.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GlassSurfaceHigh,
                        unfocusedContainerColor = GlassSurfaceHigh,
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite,
                        focusedIndicatorColor = SkyOpal,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // WebView
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                }
            )
        }
    }
}
