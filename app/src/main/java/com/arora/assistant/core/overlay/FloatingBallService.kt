package com.arora.assistant.core.overlay

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.West
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.arora.assistant.AroraApplication
import com.arora.assistant.R
import com.arora.assistant.core.ai.FloatingGraphPlotterView
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.ai.OfflineOcrEngine
import com.arora.assistant.core.ai.ProblemSolverEngine
import com.arora.assistant.core.bypass.AroraAccessibilityService
import com.arora.assistant.core.bypass.MediaProjectionService
import com.arora.assistant.core.bypass.RootCaptureFallback
import com.arora.assistant.core.bypass.ShizukuBypassService
import com.arora.assistant.core.data.AppPreferences
import com.arora.assistant.core.data.ClipboardRepository
import com.arora.assistant.ui.components.FloatingActionHub
import com.arora.assistant.ui.components.QuickAction
import com.arora.assistant.ui.miniapps.CircleToSearchResultSheet
import com.arora.assistant.ui.miniapps.FloatingBrowserView
import com.arora.assistant.ui.miniapps.FloatingCalculatorView
import com.arora.assistant.ui.miniapps.FloatingClipboardView
import com.arora.assistant.ui.miniapps.FloatingFileManagerView
import com.arora.assistant.ui.miniapps.FloatingLiveTranscriberView
import com.arora.assistant.ui.miniapps.FloatingNotesView
import com.arora.assistant.ui.miniapps.FloatingTeleprompterView
import com.arora.assistant.ui.miniapps.FloatingVideoPlayerView
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftAmber
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingBallService : Service() {

    companion object {
        const val NOTIFICATION_ID = 2001
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var floatingManager: FloatingManager
    private lateinit var appPreferences: AppPreferences
    private lateinit var clipboardRepository: ClipboardRepository

    private var ballView: View? = null
    private var ballLayoutParams: WindowManager.LayoutParams? = null
    private var activeOverlayWindow: View? = null
    private var circleOverlayView: View? = null
    private var actionHubWindow: View? = null
    private var privacyShieldWindow: View? = null
    private var inPlaceTranslateWindow: View? = null
    private var nightDimmerView: View? = null

    private var autoHideJob: Job? = null
    private var isDockedOnRight = true
    private var isPeekingState = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        floatingManager = FloatingManager(this)
        appPreferences = AppPreferences(this)
        clipboardRepository = ClipboardRepository(this)

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        initSleekRectangleCapsule()
    }

    private fun initSleekRectangleCapsule() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val pillWidthPx = (56 * displayMetrics.density).toInt()
        val pillHeightPx = (34 * displayMetrics.density).toInt()
        val peekingVisiblePx = (14 * displayMetrics.density).toInt()

        val params = FloatingManager.createLayoutParams(
            width = pillWidthPx,
            height = pillHeightPx,
            x = screenWidth - pillWidthPx,
            y = screenHeight / 2
        )
        ballLayoutParams = params

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var touchStartTime = 0L

        var isPeeking by mutableStateOf(false)

        val view = floatingManager.createFloatingComposeView(params) {
            DynamicRectangleCapsuleComposable(
                isPeeking = isPeeking,
                isDockedOnRight = isDockedOnRight
            )
        }

        fun scheduleAutoHide() {
            autoHideJob?.cancel()
            autoHideJob = serviceScope.launch {
                delay(3000)
                isPeekingState = true
                isPeeking = true

                if (isDockedOnRight) {
                    params.x = screenWidth - peekingVisiblePx
                } else {
                    params.x = -(pillWidthPx - peekingVisiblePx)
                }
                floatingManager.updateViewLayout(view, params)
            }
        }

        fun wakeFromPeeking() {
            autoHideJob?.cancel()
            if (isPeekingState) {
                isPeekingState = false
                isPeeking = false
                if (isDockedOnRight) {
                    params.x = screenWidth - pillWidthPx
                } else {
                    params.x = 0
                }
                floatingManager.updateViewLayout(view, params)
            }
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    wakeFromPeeking()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    touchStartTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 6 || abs(deltaY) > 6) {
                        isDragging = true
                        params.x = (initialX + deltaX).coerceIn(0, screenWidth - pillWidthPx)
                        params.y = (initialY + deltaY).coerceIn(0, screenHeight - pillHeightPx)
                        floatingManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchStartTime
                    if (isDragging) {
                        isDockedOnRight = event.rawX > screenWidth / 2
                        params.x = if (isDockedOnRight) screenWidth - pillWidthPx else 0
                        floatingManager.updateViewLayout(view, params)
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scheduleAutoHide()
                    } else if (duration < 350) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        openFloatingActionHub()
                        scheduleAutoHide()
                    } else {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        startCircleToSearch()
                    }
                    true
                }
                else -> false
            }
        }

        ballView = view
        scheduleAutoHide()
    }

    private fun openFloatingActionHub() {
        if (actionHubWindow != null) return

        val params = FloatingManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )

        val hubActions = listOf(
            // Category 0: Apps & Tools
            QuickAction("browser", "Browser", "Floating Web", Icons.Default.Language, SkyOpal, "apps") {
                openMiniBrowser()
            },
            QuickAction("transcribe", "Live Transcriber", "Whisper Flow", Icons.Default.GraphicEq, SkyOpal, "apps") {
                openLiveTranscriber()
            },
            QuickAction("video", "Video Player", "Background PiP", Icons.Default.Tv, SoftLavender, "apps") {
                openVideoPlayer()
            },
            QuickAction("prompter", "Teleprompter", "Voice Script", Icons.Default.TextFields, SageMint, "apps") {
                openTeleprompter()
            },
            QuickAction("calc", "Calculator", "Scientific", Icons.Default.Calculate, SageMint, "apps") {
                openCalculator()
            },
            QuickAction("notes", "Study Notes", "Anki Exporter", Icons.Default.Notes, SoftAmber, "apps") {
                openNotes()
            },
            QuickAction("clipboard", "Clipboard", "Auto-Stack", Icons.Default.ContentCopy, SoftLavender, "apps") {
                openClipboardStack()
            },
            QuickAction("files", "Files", "Storage Explorer", Icons.Default.Folder, SoftAmber, "apps") {
                openFileManager()
            },

            // Category 1: Hardware & Power Tools
            QuickAction("volume", "Volume & Dimmer", "200% Loudness", Icons.Default.VolumeUp, SoftAmber, "power") {
                openVolumeAndDimmerDialog()
            },
            QuickAction("privacy", "Privacy Shield", "Peeping Guard", Icons.Default.Security, PastelRose, "power") {
                togglePrivacyShield()
            },
            QuickAction("speed", "Speedometer", "Net Speed & Ping", Icons.Default.Speed, SkyOpal, "power") {
                toggleSpeedometer()
            },
            QuickAction("wifi", "Wi-Fi Dropzone", "Phone to PC", Icons.Default.Wifi, SageMint, "power") {
                startWiFiDropzone()
            },

            // Category 2: AI Vision
            QuickAction("ar_trans", "AR Screen Lens", "In-Place Translate", Icons.Default.Translate, SoftLavender, "vision") {
                startInPlaceARTranslate()
            },
            QuickAction("graph", "2D Graph Plotter", "Formula Graph", Icons.Default.Functions, SkyOpal, "vision") {
                openGraphPlotter()
            },

            // System Actions
            QuickAction("back", "Back", "System Action", Icons.Default.West, Color.White, "system") {
                AroraAccessibilityService.instance?.performBack()
            },
            QuickAction("home", "Home", "System Action", Icons.Default.Home, Color.White, "system") {
                AroraAccessibilityService.instance?.performHome()
            }
        )

        actionHubWindow = floatingManager.createFloatingComposeView(params) {
            FloatingActionHub(
                onDismiss = { dismissActionHub() },
                onCircleSearch = {
                    dismissActionHub()
                    startCircleToSearch()
                },
                actions = hubActions
            )
        }
    }

    private fun dismissActionHub() {
        actionHubWindow?.let {
            floatingManager.removeView(it)
            actionHubWindow = null
        }
    }

    private fun startCircleToSearch() {
        dismissActionHub()
        if (circleOverlayView != null) return

        val params = FloatingManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            gravity = Gravity.TOP or Gravity.START
        )

        circleOverlayView = floatingManager.createFloatingComposeView(params) {
            CircleToSearchOverlay(
                onRegionSelected = { rect ->
                    removeCircleOverlay()
                    processCircleSearch(rect)
                },
                onCancelled = {
                    removeCircleOverlay()
                }
            )
        }
    }

    private fun removeCircleOverlay() {
        circleOverlayView?.let {
            floatingManager.removeView(it)
            circleOverlayView = null
        }
    }

    private fun processCircleSearch(rect: RectF) {
        serviceScope.launch {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(realMetrics)
            val realScreenWidth = realMetrics.widthPixels
            val realScreenHeight = realMetrics.heightPixels

            var fullBitmap: Bitmap? = null

            // 1. Instant Hardware Screenshot via Accessibility API (Android 11+)
            if (AroraAccessibilityService.isRunning()) {
                fullBitmap = AroraAccessibilityService.instance?.takeScreenshotBitmap()
            }

            // 2. Shizuku / Wireless ADB Capture
            if (fullBitmap == null && ShizukuBypassService.hasShizukuPermission()) {
                fullBitmap = ShizukuBypassService.captureSecureScreen()
            }

            // 3. Root Capture Fallback
            if (fullBitmap == null && RootCaptureFallback.isRootAvailable()) {
                fullBitmap = RootCaptureFallback.captureRootScreen()
            }

            // 4. MediaProjection Fallback
            if (fullBitmap == null && MediaProjectionService.instance != null) {
                fullBitmap = MediaProjectionService.instance?.captureScreen(
                    realScreenWidth,
                    realScreenHeight,
                    realMetrics.densityDpi
                )
            }

            var croppedBitmap: Bitmap? = null
            if (fullBitmap != null) {
                val scaleX = fullBitmap.width.toFloat() / realScreenWidth.toFloat()
                val scaleY = fullBitmap.height.toFloat() / realScreenHeight.toFloat()

                val scaledLeft = (rect.left * scaleX).toInt().coerceIn(0, fullBitmap.width - 1)
                val scaledTop = (rect.top * scaleY).toInt().coerceIn(0, fullBitmap.height - 1)
                val scaledWidth = (rect.width() * scaleX).toInt().coerceIn(1, fullBitmap.width - scaledLeft)
                val scaledHeight = (rect.height() * scaleY).toInt().coerceIn(1, fullBitmap.height - scaledTop)

                croppedBitmap = Bitmap.createBitmap(fullBitmap, scaledLeft, scaledTop, scaledWidth, scaledHeight)
            }

            val ocrText = if (croppedBitmap != null) {
                try {
                    val recognized = OfflineOcrEngine.recognizeText(croppedBitmap).fullText
                    if (recognized.isNotBlank()) {
                        recognized
                    } else {
                        AroraAccessibilityService.instance?.extractTextInRegion(rect) ?: ""
                    }
                } catch (e: Exception) {
                    AroraAccessibilityService.instance?.extractTextInRegion(rect) ?: ""
                }
            } else {
                AroraAccessibilityService.instance?.extractTextInRegion(rect) ?: ""
            }

            openCircleResultSheet(croppedBitmap, ocrText)
        }
    }

    private fun openCircleResultSheet(bitmap: Bitmap?, ocrText: String) {
        dismissActiveWindow()

        var solutionText by mutableStateOf<String?>(null)
        var isLoading by mutableStateOf(true)
        var clientInstance by mutableStateOf<GeminiClient?>(null)

        val params = FloatingManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.BOTTOM
        )

        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            CircleToSearchResultSheet(
                bitmap = bitmap,
                ocrText = ocrText,
                initialAiSolution = solutionText,
                isLoading = isLoading,
                geminiClient = clientInstance,
                onClose = { dismissActiveWindow() }
            )
        }

        serviceScope.launch {
            val apiKey = appPreferences.geminiApiKey.first()
            if (apiKey.isNotEmpty()) {
                val client = GeminiClient(apiKey)
                clientInstance = client
                val result = ProblemSolverEngine.solveProblem(client, bitmap, ocrText)
                isLoading = false
                solutionText = result.getOrElse { "Error: ${it.message}" }
            } else {
                isLoading = false
                solutionText = if (ocrText.isNotEmpty()) {
                    "Recognized Text:\n\n$ocrText\n\n(Tip: Configure Gemini Key in settings to enable step-by-step reasoning)"
                } else {
                    "Circled region captured. Add Gemini Key in Settings for AI reasoning."
                }
            }
        }
    }

    private fun startInPlaceARTranslate() {
        dismissActiveWindow()
        serviceScope.launch {
            Toast.makeText(this@FloatingBallService, "Scanning screen for AR translation...", Toast.LENGTH_SHORT).show()
            val bitmap = AroraAccessibilityService.instance?.takeScreenshotBitmap()
            if (bitmap != null) {
                val ocrResult = OfflineOcrEngine.recognizeText(bitmap)
                val blocks = mutableListOf<TranslatedBlock>()

                for (line in ocrResult.lines) {
                    val original = line.text
                    val translated = com.arora.assistant.core.ai.OfflineTranslationEngine.translateOnDevice(original, "Hindi").getOrDefault(original)
                    val box = line.boundingBox ?: Rect(0, 0, 100, 40)
                    blocks.add(TranslatedBlock(box, original, translated))
                }

                val params = FloatingManager.createLayoutParams(
                    width = WindowManager.LayoutParams.MATCH_PARENT,
                    height = WindowManager.LayoutParams.MATCH_PARENT,
                    flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )

                inPlaceTranslateWindow = floatingManager.createFloatingComposeView(params) {
                    InPlaceTranslateOverlay(
                        blocks = blocks,
                        targetLanguage = "Hindi",
                        onClose = {
                            inPlaceTranslateWindow?.let { floatingManager.removeView(it) }
                            inPlaceTranslateWindow = null
                        }
                    )
                }
            } else {
                Toast.makeText(this@FloatingBallService, "Ensure Accessibility Permission is enabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openVolumeAndDimmerDialog() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )

        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            VolumeAndDimmerControlDialog(
                onBoostChange = { level ->
                    VolumeBoosterService.setBoost(level)
                },
                onDimChange = { dimLevel, isBlueLight ->
                    updateNightDimmer(dimLevel, isBlueLight)
                },
                onClose = { dismissActiveWindow() }
            )
        }
    }

    private fun updateNightDimmer(dimPercent: Float, isBlueLight: Boolean) {
        if (dimPercent > 0.05f) {
            if (nightDimmerView == null) {
                val params = FloatingManager.createLayoutParams(
                    width = WindowManager.LayoutParams.MATCH_PARENT,
                    height = WindowManager.LayoutParams.MATCH_PARENT,
                    flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
                nightDimmerView = floatingManager.createFloatingComposeView(params) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isBlueLight) Color(0xFFE08520).copy(alpha = dimPercent * 0.4f)
                                else Color.Black.copy(alpha = dimPercent)
                            )
                    )
                }
            } else {
                nightDimmerView?.alpha = dimPercent
            }
        } else {
            nightDimmerView?.let {
                floatingManager.removeView(it)
                nightDimmerView = null
            }
        }
    }

    private fun togglePrivacyShield() {
        if (privacyShieldWindow != null) {
            floatingManager.removeView(privacyShieldWindow!!)
            privacyShieldWindow = null
        } else {
            val params = FloatingManager.createLayoutParams(
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = WindowManager.LayoutParams.WRAP_CONTENT,
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                gravity = Gravity.CENTER
            )
            privacyShieldWindow = floatingManager.createFloatingComposeView(params) {
                PrivacyShieldComposable(
                    onClose = {
                        privacyShieldWindow?.let { floatingManager.removeView(it) }
                        privacyShieldWindow = null
                    }
                )
            }
        }
    }

    private fun toggleSpeedometer() {
        if (NetworkSpeedMonitorService.isRunning) {
            stopService(Intent(this, NetworkSpeedMonitorService::class.java))
            Toast.makeText(this, "Speedometer Stopped", Toast.LENGTH_SHORT).show()
        } else {
            ContextCompat.startForegroundService(this, Intent(this, NetworkSpeedMonitorService::class.java))
            Toast.makeText(this, "Speedometer Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startWiFiDropzone() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingWiFiDropzoneDialog(
                onClose = { dismissActiveWindow() }
            )
        }
    }

    private fun openLiveTranscriber() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (460 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingLiveTranscriberView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openVideoPlayer() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (460 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingVideoPlayerView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openTeleprompter() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (440 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingTeleprompterView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openGraphPlotter() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (440 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingGraphPlotterView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openMiniBrowser() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (480 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingBrowserView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openClipboardStack() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (440 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingClipboardView(repository = clipboardRepository, onClose = { dismissActiveWindow() })
        }
    }

    private fun openCalculator() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (320 * resources.displayMetrics.density).toInt(),
            height = (460 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingCalculatorView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openNotes() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (460 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingNotesView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openFileManager() {
        dismissActiveWindow()
        val params = FloatingManager.createLayoutParams(
            width = (340 * resources.displayMetrics.density).toInt(),
            height = (460 * resources.displayMetrics.density).toInt(),
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.CENTER
        )
        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            FloatingFileManagerView(onClose = { dismissActiveWindow() })
        }
    }

    private fun dismissActiveWindow() {
        activeOverlayWindow?.let {
            floatingManager.removeView(it)
            activeOverlayWindow = null
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, AroraApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoHideJob?.cancel()
        dismissActionHub()
        dismissActiveWindow()
        removeCircleOverlay()
        privacyShieldWindow?.let { floatingManager.removeView(it) }
        inPlaceTranslateWindow?.let { floatingManager.removeView(it) }
        nightDimmerView?.let { floatingManager.removeView(it) }
        ballView?.let { floatingManager.removeView(it) }
        LocalFileDropzoneServer.stopServer()
        clipboardRepository.destroy()
        isRunning = false
    }
}

@Composable
fun DynamicRectangleCapsuleComposable(
    isPeeking: Boolean,
    isDockedOnRight: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CapsuleMorph")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CapsuleScale"
    )

    val morphColor by infiniteTransition.animateColor(
        initialValue = SoftLavender,
        targetValue = SkyOpal,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CapsuleMorphColor"
    )

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 34.dp)
            .scale(if (isPeeking) 0.88f else pulseScale)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(17.dp),
                ambientColor = Color.Black,
                spotColor = morphColor.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        SoftSurfaceElevated.copy(alpha = 0.95f),
                        SoftDarkBg.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(morphColor, SoftCardBorder)),
                RoundedCornerShape(17.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(morphColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SkyOpal.copy(alpha = 0.75f))
            )
        }
    }
}
