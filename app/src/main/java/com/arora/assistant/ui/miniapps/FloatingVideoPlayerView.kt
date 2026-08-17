package com.arora.assistant.ui.miniapps

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.TextMuted
import java.io.ByteArrayInputStream

@Composable
fun FloatingVideoPlayerView(
    initialUrl: String = "",
    onClose: () -> Unit = {}
) {
    val startUrl = remember(initialUrl) {
        if (initialUrl.isNotBlank() && initialUrl.startsWith("http")) initialUrl
        else "https://m.youtube.com"
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isBuffering by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fullscreen WebView
        AndroidView(
            factory = { ctx ->
                createBraveShieldedYouTubeWebView(
                    context = ctx,
                    startUrl = startUrl,
                    onNavStateChanged = { back, forward ->
                        canGoBack = back
                        canGoForward = forward
                    },
                    onProgressUpdate = { progress -> loadProgress = progress / 100f },
                    onBufferingState = { buffering -> isBuffering = buffering }
                ).also { webViewRef = it }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Slim Top Loading Bar
        if (loadProgress in 0.01f..0.99f) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter),
                color = SkyOpal,
                trackColor = Color.Transparent
            )
        }

        // Loading Spinner on Initial Start
        if (isBuffering && loadProgress < 0.25f) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SkyOpal, modifier = Modifier.size(32.dp))
            }
        }

        // Sleek Translucent Bottom Nav Capsule
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SoftDarkBg.copy(alpha = 0.90f))
                .border(1.dp, SoftCardBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = canGoBack,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) Color.White else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = canGoForward,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) Color.White else TextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = { webViewRef?.loadUrl("https://m.youtube.com") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Home, "Home", tint = SkyOpal, modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Reload", tint = Color.White, modifier = Modifier.size(14.dp))
                }

                Spacer(modifier = Modifier.width(2.dp))

                // AdBlock Active Badge
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NeonEmerald.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = NeonEmerald, modifier = Modifier.size(9.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("0 Ads", color = NeonEmerald, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createBraveShieldedYouTubeWebView(
    context: Context,
    startUrl: String,
    onNavStateChanged: (Boolean, Boolean) -> Unit,
    onProgressUpdate: (Int) -> Unit,
    onBufferingState: (Boolean) -> Unit
): WebView {
    return WebView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.BLACK)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        // Brave-Shield Engine: Background Audio Lockdown, 0ms Instant Ad Skip, & Cosmetic Filtering
        val braveShieldJs = """
            (function() {
                if (window._auraview_shield_active) return;
                window._auraview_shield_active = true;

                // 1. Permanent Visibility Lock (Prevents YouTube from knowing screen is blurred/minimized)
                try {
                    Object.defineProperty(document, 'hidden', { get: () => false, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: () => 'visible', configurable: true });
                    Object.defineProperty(document, 'webkitHidden', { get: () => false, configurable: true });
                    Object.defineProperty(document, 'webkitVisibilityState', { get: () => 'visible', configurable: true });
                } catch(e) {}

                // 2. Intercept and swallow all blur / visibilitychange events
                const blockEvents = ['visibilitychange', 'webkitvisibilitychange', 'blur', 'pagehide'];
                blockEvents.forEach(evt => {
                    window.addEventListener(evt, e => e.stopImmediatePropagation(), true);
                    document.addEventListener(evt, e => e.stopImmediatePropagation(), true);
                });

                // 3. Spoof IntersectionObserver so video is always 100% visible to YouTube's JS
                if (window.IntersectionObserver) {
                    const OrigIO = window.IntersectionObserver;
                    window.IntersectionObserver = function(callback, options) {
                        return new OrigIO(function(entries, observer) {
                            entries.forEach(entry => {
                                try {
                                    Object.defineProperty(entry, 'isIntersecting', { get: () => true, configurable: true });
                                    Object.defineProperty(entry, 'intersectionRatio', { get: () => 1.0, configurable: true });
                                } catch(e) {}
                            });
                            callback(entries, observer);
                        }, options);
                    };
                }

                // 4. Cosmetic CSS Injection (Hides Banner Ads, Mastheads, Companion Ads, Pivot Bottom Bar)
                function injectShieldStyles() {
                    if (!document.getElementById('brave-shield-css')) {
                        const style = document.createElement('style');
                        style.id = 'brave-shield-css';
                        style.innerHTML = `
                            .ad-showing, .ad-interrupting, .video-ads,
                            .ytp-ad-module, .ytp-ad-overlay-container,
                            .ytp-ad-message-container, .ytp-ad-action-interstitial,
                            .ytp-ad-image-overlay, .ytp-ad-preview-container,
                            .ytm-promoted-sparkles-web-renderer,
                            .ytm-promoted-video-renderer,
                            .ytm-compact-promoted-item-renderer,
                            ytd-ad-slot-renderer, ytd-banner-promo-renderer,
                            ytd-in-feed-ad-layout-renderer, ytd-statement-banner-renderer,
                            ytm-companion-ad-renderer,
                            #player-ads, .player-ad, #masthead-ad,
                            .ytp-ad-feedback-dialog-background,
                            .ytp-ad-skip-button-slot,
                            ytm-pivot-bar-renderer,
                            .ytm-pivot-bar-renderer {
                                display: none !important;
                                visibility: hidden !important;
                                height: 0px !important;
                                width: 0px !important;
                                pointer-events: none !important;
                            }
                            ytm-app {
                                padding-bottom: 0px !important;
                            }
                        `;
                        (document.head || document.documentElement).appendChild(style);
                    }
                }
                injectShieldStyles();

                // 5. Intelligent Background Watchdog & Pause Suppressor
                const origVideoPause = HTMLVideoElement.prototype.pause;
                let isUserDirectAction = false;
                document.addEventListener('pointerdown', () => {
                    isUserDirectAction = true;
                    setTimeout(() => { isUserDirectAction = false; }, 400);
                }, true);

                HTMLVideoElement.prototype.pause = function() {
                    if (!isUserDirectAction && !this.ended && this.currentTime > 0) {
                        // Suppress background suspension pauses from Android WebView resize
                        return;
                    }
                    return origVideoPause.apply(this, arguments);
                };

                function backgroundAudioAndAdWatchdog() {
                    injectShieldStyles();

                    // Fast Skip Ad Button Clicker
                    const skipSelectors = [
                        '.ytp-skip-ad-button',
                        '.ytp-ad-skip-button',
                        '.ytp-ad-skip-button-modern',
                        '.ytp-ad-skip-button-slot button',
                        'button[class*="skip"]',
                        '.ytp-ad-overlay-close-button'
                    ];
                    for (const sel of skipSelectors) {
                        const btn = document.querySelector(sel);
                        if (btn && typeof btn.click === 'function') {
                            btn.click();
                        }
                    }

                    // Detect and skip video ads
                    const isAdPlaying = document.querySelector(
                        '.ad-showing, .ad-interrupting, .video-ads, [class*="ytp-ad-preview"], [class*="ytp-ad-player-overlay"], .ytp-ad-text, [class*="ad-created"]'
                    );

                    const videos = document.querySelectorAll('video');
                    videos.forEach(video => {
                        if (isAdPlaying || video.closest('.ad-showing, .ad-interrupting, .video-ads')) {
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (isFinite(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration;
                            } else {
                                video.currentTime = 9999;
                            }
                        } else {
                            // Ensure background audio stays playing continuously when minimized
                            if (video.paused && !video.ended && video.currentTime > 0) {
                                video.play().catch(() => {});
                            }
                        }
                    });
                }

                setInterval(backgroundAudioAndAdWatchdog, 150);
            })();
        """.trimIndent()

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onBufferingState(true)
                view?.evaluateJavascript(braveShieldJs, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onBufferingState(false)
                view?.evaluateJavascript(braveShieldJs, null)
                onNavStateChanged(canGoBack(), canGoForward())
            }

            // Block ad network requests & return empty 200 JSON to prevent YouTube pause loops
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                val blockedDomains = listOf(
                    "doubleclick.net",
                    "googleads.g.doubleclick.net",
                    "pagead2.googlesyndication.com",
                    "adservice.google.com",
                    "youtube.com/api/stats/ads",
                    "youtube.com/pagead/",
                    "youtube.com/ptracking",
                    "youtube.com/get_midroll_info",
                    "youtube.com/youtubei/v1/player/ad_break",
                    "imasdk.googleapis.com",
                    "static.doubleclick.net",
                    "pubads.g.doubleclick.net",
                    "securepubads.g.doubleclick.net"
                )

                if (blockedDomains.any { url.contains(it) }) {
                    return WebResourceResponse(
                        "application/json",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf(
                            "Access-Control-Allow-Origin" to "*",
                            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                            "Access-Control-Allow-Headers" to "*"
                        ),
                        ByteArrayInputStream("{}".toByteArray())
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressUpdate(newProgress)
                if (newProgress > 30) {
                    view?.evaluateJavascript(braveShieldJs, null)
                }
                onNavStateChanged(canGoBack(), canGoForward())
            }
        }

        loadUrl(startUrl)
    }
}
