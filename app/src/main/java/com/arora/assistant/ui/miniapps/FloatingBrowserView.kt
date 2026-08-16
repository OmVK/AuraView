package com.arora.assistant.ui.miniapps

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
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
import com.arora.assistant.ui.theme.ObsidianBg

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingBrowserView(
    initialUrl: String = "https://www.google.com",
    onClose: () -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var webViewInstance: WebView? by remember { mutableStateOf(null) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp),
        borderGlow = true
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header & Address Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    placeholder = { Text("Search or type URL...", fontSize = 12.sp, color = Color.Gray) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = ElectricCyan, modifier = Modifier.size(18.dp)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val target = if (inputUrl.startsWith("http://") || inputUrl.startsWith("https://")) {
                            inputUrl
                        } else {
                            "https://www.google.com/search?q=$inputUrl"
                        }
                        currentUrl = target
                        webViewInstance?.loadUrl(target)
                    }),
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = GlassSurfaceHigh,
                        unfocusedContainerColor = GlassSurfaceHigh,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = ElectricCyan,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = { webViewInstance?.reload() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                }

                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // WebView
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl(currentUrl)
                        webViewInstance = this
                    }
                }
            )
        }
    }
}
