package com.arora.assistant.ui.miniapps

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FloatingVideoPlayerView(
    initialUrl: String = "https://m.youtube.com",
    onClose: () -> Unit
) {
    var urlInput by remember { mutableStateOf(initialUrl) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val speedOptions = listOf(1.0f, 1.5f, 2.0f, 3.0f, 4.0f)

    Box(
        modifier = Modifier
            .size(width = 360.dp, height = 480.dp)
            .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = SoftLavender.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(20.dp))
            .background(SoftDarkBg.copy(alpha = 0.96f))
            .border(1.dp, SoftCardBorder, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Tv, null, tint = SoftLavender, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Floating Video & Background PiP", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))

                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // URL Search Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Paste video URL or search...", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SoftSurface,
                        unfocusedContainerColor = SoftSurface,
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite,
                        focusedIndicatorColor = SoftLavender,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                NeonButton(
                    text = "Go",
                    onClick = {
                        val target = if (urlInput.startsWith("http")) urlInput else "https://www.google.com/search?q=${Uri.encode(urlInput)}"
                        webViewInstance?.loadUrl(target)
                    },
                    modifier = Modifier.height(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Speed Control Toolbar (Up to 4x speed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SoftSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, null, tint = SkyOpal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Speed:", color = TextMuted, fontSize = 11.sp)
                }

                speedOptions.forEach { spd ->
                    val isSelected = playbackSpeed == spd
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) SoftLavender else Color.Transparent)
                            .clickable {
                                playbackSpeed = spd
                                webViewInstance?.evaluateJavascript(
                                    "document.querySelectorAll('video').forEach(v => v.playbackRate = $spd);",
                                    null
                                )
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${spd}x",
                            color = if (isSelected) Color.White else TextOffWhite,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Embedded Video Web View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            loadUrl(urlInput)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
