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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.West
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
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
import com.arora.assistant.core.ai.OfflineTranslationEngine
import com.arora.assistant.core.ai.ProblemSolverEngine
import com.arora.assistant.core.bypass.AroraAccessibilityService
import com.arora.assistant.core.bypass.MediaProjectionService
import com.arora.assistant.core.bypass.RootCaptureFallback
import com.arora.assistant.core.bypass.ShizukuBypassService
import com.arora.assistant.core.data.AppPreferences
import com.arora.assistant.core.data.ClipboardRepository
import com.arora.assistant.core.service.ServiceStateManager
import com.arora.assistant.ui.components.FloatingActionHub
import com.arora.assistant.ui.components.QuickAction
import com.arora.assistant.ui.miniapps.CircleActionChoiceSheet
import com.arora.assistant.ui.miniapps.CircleToSearchResultSheet
import com.arora.assistant.ui.miniapps.FloatingAiAgentView
import com.arora.assistant.ui.miniapps.FloatingBrowserView
import com.arora.assistant.ui.miniapps.FloatingCalculatorView
import com.arora.assistant.ui.miniapps.FloatingClipboardView
import com.arora.assistant.ui.miniapps.FloatingFileManagerView
import com.arora.assistant.ui.miniapps.FloatingInterviewCopilotView
import com.arora.assistant.ui.miniapps.FloatingLiveTranscriberView
import com.arora.assistant.ui.miniapps.FloatingNotesView
import com.arora.assistant.ui.miniapps.FloatingTeleprompterView
import com.arora.assistant.ui.miniapps.FloatingVideoPlayerView
import com.arora.assistant.ui.theme.ElectricCyan
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
        var instance: FloatingBallService? = null
            private set
        var isRunning: Boolean = false
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
    private var isRecordingMacro by mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        ServiceStateManager.setFloatingBallActive(true)
        floatingManager = FloatingManager(this)
        appPreferences = AppPreferences(this)
        clipboardRepository = ClipboardRepository(this)
        com.arora.assistant.core.agent.MacroRecorderEngine.init(this)

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_OPEN_VIDEO") {
            openVideoPlayer()
        }
        return START_STICKY
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
                isDockedOnRight = isDockedOnRight,
                isRecording = isRecordingMacro
            )
        }

        fun scheduleAutoHide() {
            if (isRecordingMacro) return // Keep visible while recording
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

                    if (abs(deltaX) > 8 || abs(deltaY) > 8) {
                        isDragging = true
                        params.x = (initialX + deltaX).coerceIn(0, screenWidth - pillWidthPx)
                        params.y = (initialY + deltaY).coerceIn(0, screenHeight - pillHeightPx)
                        floatingManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchStartTime
                    val totalDeltaX = event.rawX - initialTouchX
                    val totalDeltaY = event.rawY - initialTouchY

                    // Detect 4-Way Flick Shortcuts on quick release
                    val isQuickFlick = !isRecordingMacro && duration < 300 && (abs(totalDeltaX) > 60 || abs(totalDeltaY) > 60)

                    if (isQuickFlick) {
                        if (totalDeltaY < -60 && abs(totalDeltaY) > abs(totalDeltaX)) {
                            // Flick UP -> Circle to Search / Lasso
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            startCircleToSearch()
                        } else if (totalDeltaY > 60 && abs(totalDeltaY) > abs(totalDeltaX)) {
                            // Flick DOWN -> Notification Shade
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            ServiceStateManager.performNotifications()
                        } else if ((isDockedOnRight && totalDeltaX < -60) || (!isDockedOnRight && totalDeltaX > 60)) {
                            // Flick INWARD -> Back Navigation
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            ServiceStateManager.performBack()
                        } else {
                            // Settle to screen edge
                            isDockedOnRight = event.rawX > screenWidth / 2
                            params.x = if (isDockedOnRight) screenWidth - pillWidthPx else 0
                            floatingManager.updateViewLayout(view, params)
                            scheduleAutoHide()
                        }
                    } else if (isDragging) {
                        isDockedOnRight = event.rawX > screenWidth / 2
                        params.x = if (isDockedOnRight) screenWidth - pillWidthPx else 0
                        floatingManager.updateViewLayout(view, params)
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        scheduleAutoHide()
                    } else if (duration < 350) {
                        // Clean TAP -> If recording, STOP; otherwise Open Hub
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (isRecordingMacro) {
                            stopMacroRecording()
                        } else {
                            openFloatingActionHub()
                        }
                        scheduleAutoHide()
                    } else {
                        // LONG PRESS -> WhisperFlow Transcriber
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        if (isRecordingMacro) {
                            stopMacroRecording()
                        } else {
                            openLiveTranscriber()
                        }
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
            QuickAction("interview", "Interview & Exam AI", "Live STAR & Answers", Icons.Default.AutoAwesome, SkyOpal, "apps") {
                openInterviewCopilot()
            },
            QuickAction("agent", "Autonomous AI Agent", "UI Actions & Macros", Icons.Default.Bolt, ElectricCyan, "apps") {
                openAiAgent()
            },
            QuickAction("browser", "Browser", "Floating Web", Icons.Default.Language, SkyOpal, "apps") {
                openMiniBrowser()
            },
            QuickAction("transcribe", "Live Transcriber", "Whisper Flow", Icons.Default.GraphicEq, SkyOpal, "apps") {
                openLiveTranscriber()
            },
            QuickAction("video", "YouTube", "Ad-Free Player", Icons.Default.Tv, SoftLavender, "apps") {
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
                ServiceStateManager.performBack()
            },
            QuickAction("home", "Home", "System Action", Icons.Default.Home, Color.White, "system") {
                ServiceStateManager.performHome()
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
            kotlinx.coroutines.delay(80)
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(realMetrics)
            val realScreenWidth = realMetrics.widthPixels
            val realScreenHeight = realMetrics.heightPixels

            var fullBitmap: Bitmap? = null

            // 1. Decoupled Multi-Tier Capture (Accessibility API / MediaProjection)
            fullBitmap = ServiceStateManager.takeScreenshot()

            // 2. Shizuku / Wireless ADB Capture Fallback
            if (fullBitmap == null && ShizukuBypassService.hasShizukuPermission()) {
                fullBitmap = ShizukuBypassService.captureSecureScreen()
            }

            // 3. Root Capture Fallback
            if (fullBitmap == null && RootCaptureFallback.isRootAvailable()) {
                fullBitmap = RootCaptureFallback.captureRootScreen()
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
                    if (recognized.isNotBlank()) recognized.trim() else ""
                } catch (e: Exception) {
                    ""
                }
            } else ""

            openCircleChoiceSheet(croppedBitmap, ocrText)
        }
    }

    private fun openCircleChoiceSheet(bitmap: Bitmap?, ocrText: String) {
        dismissActiveWindow()

        val params = FloatingManager.createLayoutParams(
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            gravity = Gravity.BOTTOM
        )

        activeOverlayWindow = floatingManager.createFloatingComposeView(params) {
            CircleActionChoiceSheet(
                bitmap = bitmap,
                ocrText = ocrText,
                onSelectTextMode = {
                    openCircleResultSheet(bitmap, ocrText, isVisualMode = false)
                },
                onSelectVisualMode = {
                    openCircleResultSheet(bitmap, ocrText, isVisualMode = true)
                },
                onClose = { dismissActiveWindow() }
            )
        }
    }

    private fun openCircleResultSheet(bitmap: Bitmap?, ocrText: String, isVisualMode: Boolean) {
        dismissActiveWindow()

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
                initialIsVisualMode = isVisualMode,
                geminiClient = clientInstance,
                onClose = { dismissActiveWindow() }
            )
        }

        serviceScope.launch {
            val apiKey = appPreferences.geminiApiKey.first()
            if (apiKey.isNotEmpty()) {
                clientInstance = GeminiClient(apiKey)
            }
        }
    }

    private fun startInPlaceARTranslate() {
        dismissActiveWindow()
        serviceScope.launch {
            Toast.makeText(this@FloatingBallService, "Scanning screen for AR translation...", Toast.LENGTH_SHORT).show()
            val bitmap = ServiceStateManager.takeScreenshot()
            if (bitmap != null) {
                val ocrResult = OfflineOcrEngine.recognizeText(bitmap)
                val blocks = mutableListOf<TranslatedBlock>()

                val apiKey = appPreferences.geminiApiKey.first()
                val geminiClient = if (apiKey.isNotBlank()) GeminiClient(apiKey) else null

                for (line in ocrResult.lines) {
                    val original = line.text
                    var translated = com.arora.assistant.core.ai.OfflineTranslationEngine.translateOnDevice(original, "Hindi").getOrNull()
                    if (translated == null && geminiClient != null) {
                        translated = com.arora.assistant.core.ai.InPlaceTranslator.translateContent(geminiClient, original, "Hindi").getOrNull()
                    }
                    val finalTranslation = translated ?: original
                    val box = line.boundingBox ?: Rect(0, 0, 100, 40)
                    blocks.add(TranslatedBlock(box, original, finalTranslation))
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
                Toast.makeText(this@FloatingBallService, "Could not capture screen for translation", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openVolumeAndDimmerDialog() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Volume & Dimmer",
            icon = Icons.Default.VolumeUp,
            widthDp = 340,
            heightDp = 440,
            onClose = { dismissActiveWindow() }
        ) {
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
                gravity = Gravity.TOP or Gravity.START
            ).apply {
                x = 0
                y = 400
            }
            privacyShieldWindow = floatingManager.createFloatingComposeView(params) {
                PrivacyShieldComposable(
                    onDragDelta = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        privacyShieldWindow?.let { win ->
                            floatingManager.updateViewLayout(win, params)
                        }
                    },
                    onClose = {
                        privacyShieldWindow?.let { floatingManager.removeView(it) }
                        privacyShieldWindow = null
                    }
                )
            }
        }
    }

    private fun toggleSpeedometer() {
        if (ServiceStateManager.isSpeedMonitorActive.value) {
            stopService(Intent(this, NetworkSpeedMonitorService::class.java))
            Toast.makeText(this, "Speedometer Stopped", Toast.LENGTH_SHORT).show()
        } else {
            ContextCompat.startForegroundService(this, Intent(this, NetworkSpeedMonitorService::class.java))
            Toast.makeText(this, "Speedometer Active", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInterviewCopilot() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "AI Interview & Exam Copilot",
            icon = Icons.Default.AutoAwesome,
            widthDp = 340,
            heightDp = 460,
            isSecure = true,
            initialYDp = 48,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingInterviewCopilotView(onClose = { dismissActiveWindow() })
        }
    }

    fun openAiAgent() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Touch Macros & Actions",
            icon = Icons.Default.Bolt,
            widthDp = 340,
            heightDp = 480,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingAiAgentView(
                onStartRecording = { startMacroRecording() },
                onClose = { dismissActiveWindow() }
            )
        }
    }

    fun startMacroRecording() {
        dismissActionHub()
        dismissActiveWindow()
        com.arora.assistant.core.agent.MacroRecorderEngine.startRecording()
        isRecordingMacro = true
        Toast.makeText(this, "🔴 Recording touch gestures! Tap anywhere, then tap Stop on the floating rectangle.", Toast.LENGTH_LONG).show()
    }

    fun stopMacroRecording() {
        isRecordingMacro = false
        val steps = com.arora.assistant.core.agent.MacroRecorderEngine.stopRecording()
        Toast.makeText(this, "⏹️ Recorded ${steps.size} actions!", Toast.LENGTH_SHORT).show()
        openAiAgent()
    }

    private fun startWiFiDropzone() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Wi-Fi Dropzone",
            icon = Icons.Default.Wifi,
            widthDp = 340,
            heightDp = 460,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingWiFiDropzoneDialog(
                onClose = { dismissActiveWindow() }
            )
        }
    }

    private fun openLiveTranscriber() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Live Transcriber",
            icon = Icons.Default.GraphicEq,
            widthDp = 340,
            heightDp = 480,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingLiveTranscriberView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openVideoPlayer() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "YouTube Mini",
            icon = Icons.Default.Tv,
            widthDp = 350,
            heightDp = 490,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingVideoPlayerView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openTeleprompter() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Teleprompter",
            icon = Icons.Default.TextFields,
            widthDp = 340,
            heightDp = 460,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingTeleprompterView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openGraphPlotter() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "2D Graph Plotter",
            icon = Icons.Default.Functions,
            widthDp = 340,
            heightDp = 480,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingGraphPlotterView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openMiniBrowser() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Mini Browser",
            icon = Icons.Default.Language,
            widthDp = 350,
            heightDp = 500,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingBrowserView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openClipboardStack() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Smart Clipboard",
            icon = Icons.Default.ContentCopy,
            widthDp = 340,
            heightDp = 480,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingClipboardView(repository = clipboardRepository, onClose = { dismissActiveWindow() })
        }
    }

    private fun openCalculator() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Scientific Calculator",
            icon = Icons.Default.Calculate,
            widthDp = 330,
            heightDp = 480,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingCalculatorView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openNotes() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Study Notes & Anki",
            icon = Icons.Default.Notes,
            widthDp = 340,
            heightDp = 480,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
            FloatingNotesView(onClose = { dismissActiveWindow() })
        }
    }

    private fun openFileManager() {
        dismissActiveWindow()
        activeOverlayWindow = floatingManager.createDraggableSubWindow(
            title = "Storage Explorer",
            icon = Icons.Default.Folder,
            widthDp = 340,
            heightDp = 480,
            onBackToMenu = { dismissActiveWindow(); openFloatingActionHub() },
            onClose = { dismissActiveWindow() }
        ) {
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
        ServiceStateManager.setFloatingBallActive(false)
        isRunning = false
    }
}

@Composable
fun DynamicRectangleCapsuleComposable(
    isPeeking: Boolean,
    isDockedOnRight: Boolean,
    isRecording: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CapsuleMorph")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isRecording) 0.95f else 0.97f,
        targetValue = if (isRecording) 1.05f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 900 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CapsuleScale"
    )

    val morphColor by infiniteTransition.animateColor(
        initialValue = if (isRecording) PastelRose else SoftLavender,
        targetValue = if (isRecording) Color(0xFFFF1744) else SkyOpal,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isRecording) 1000 else 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CapsuleMorphColor"
    )

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 34.dp)
            .scale(if (isPeeking) 0.88f else pulseScale)
            .shadow(
                elevation = if (isRecording) 16.dp else 12.dp,
                shape = RoundedCornerShape(17.dp),
                ambientColor = Color.Black,
                spotColor = if (isRecording) PastelRose.copy(alpha = 0.8f) else morphColor.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.horizontalGradient(
                    if (isRecording) {
                        listOf(
                            Color(0xFF8A001A).copy(alpha = 0.95f),
                            Color(0xFF2B0008).copy(alpha = 0.95f)
                        )
                    } else {
                        listOf(
                            SoftSurfaceElevated.copy(alpha = 0.95f),
                            SoftDarkBg.copy(alpha = 0.95f)
                        )
                    }
                )
            )
            .border(
                if (isRecording) 1.5.dp else 1.2.dp,
                Brush.linearGradient(
                    if (isRecording) listOf(Color(0xFFFF5252), PastelRose)
                    else listOf(morphColor, SoftCardBorder)
                ),
                RoundedCornerShape(17.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF1744))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Recording",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
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
}
