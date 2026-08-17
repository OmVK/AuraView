package com.arora.assistant.core.bypass

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.arora.assistant.core.service.AccessibilityDelegate
import com.arora.assistant.core.service.ServiceStateManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AroraAccessibilityService : AccessibilityService(), AccessibilityDelegate {

    companion object {
        var instance: AroraAccessibilityService? = null
            private set

        fun isRunning(): Boolean = ServiceStateManager.isAccessibilityActive.value
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ServiceStateManager.registerAccessibilityDelegate(this)
        Log.d("AroraAccessibility", "Accessibility Service Connected & Registered with StateFlow")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""
        val textList = event.text
        val combined = if (!textList.isNullOrEmpty()) textList.joinToString(" ") else (event.contentDescription?.toString() ?: "")

        if (combined.isNotBlank()) {
            com.arora.assistant.core.ai.ProactiveIntelligenceService.inspectScreenContent(combined, pkg)
        }

        // 1. Log events to Macro Recorder if active
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val label = if (combined.isNotBlank()) combined else (event.className?.toString() ?: "Button")
                var cx = 0f
                var cy = 0f
                try {
                    val source = event.source
                    if (source != null) {
                        val rect = Rect()
                        source.getBoundsInScreen(rect)
                        if (!rect.isEmpty) {
                            cx = rect.exactCenterX()
                            cy = rect.exactCenterY()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                com.arora.assistant.core.agent.MacroRecorderEngine.logStep("CLICK", label, cx, cy)
                com.arora.assistant.core.agent.AiMacroRecorder.logEvent("CLICK", label)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (combined.isNotBlank()) {
                    com.arora.assistant.core.agent.MacroRecorderEngine.logStep("INPUT", combined)
                    com.arora.assistant.core.agent.AiMacroRecorder.logEvent("INPUT", combined)
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                com.arora.assistant.core.agent.MacroRecorderEngine.logStep("SCROLL", "")
                com.arora.assistant.core.agent.AiMacroRecorder.logEvent("SCROLL", "")
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                try {
                    com.arora.assistant.core.data.ClipboardRepository(this).captureCurrentClip()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w("AroraAccessibility", "Accessibility Service Interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceStateManager.unregisterAccessibilityDelegate()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceStateManager.unregisterAccessibilityDelegate()
        instance = null
    }

    // System Navigation Actions (AccessibilityDelegate implementation)
    override fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    override fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    override fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    override fun performNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    override fun performQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    override fun performLockScreen(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    } else false
    override fun performSplitScreen(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    } else false

    override fun getActiveRootNode(): AccessibilityNodeInfo? = rootInActiveWindow

    override fun clickAtCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = android.graphics.Path().apply {
            moveTo(x, y)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Captures full screen directly using Accessibility API (Android 11+ / API 30+).
     * No prompts, no background service restrictions.
     */
    override suspend fun takeScreenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return suspendCancellableCoroutine { continuation ->
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val buffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            var copy: Bitmap? = null
                            try {
                                val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                                copy = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (e: Exception) {
                                Log.e("AroraAccessibility", "Hardware buffer copy failed", e)
                            } finally {
                                buffer.close()
                            }
                            if (continuation.isActive) continuation.resume(copy)
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w("AroraAccessibility", "takeScreenshot failed: $errorCode")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("AroraAccessibility", "takeScreenshot exception", e)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    // Backwards-compatible alias for existing calls
    suspend fun takeScreenshotBitmap(): Bitmap? = takeScreenshot()

    /**
     * Extracts text ONLY from UI elements that intersect with the circled bounding box.
     */
    override fun extractTextInRegion(region: RectF): String {
        val rootNode = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        val screenRect = Rect()
        traverseNodeInRegion(rootNode, region, screenRect, builder)
        return builder.toString().trim()
    }

    private fun traverseNodeInRegion(
        node: AccessibilityNodeInfo?,
        region: RectF,
        outRect: Rect,
        builder: StringBuilder
    ) {
        if (node == null) return

        node.getBoundsInScreen(outRect)
        val nodeRectF = RectF(outRect)

        // Check if node is inside or intersects with the circled region
        if (RectF.intersects(nodeRectF, region) || region.contains(nodeRectF)) {
            val text = node.text
            if (!text.isNullOrEmpty()) {
                builder.append(text).append("\n")
            }
            val contentDesc = node.contentDescription
            if (!contentDesc.isNullOrEmpty() && contentDesc != text) {
                builder.append(contentDesc).append("\n")
            }
        }

        for (i in 0 until node.childCount) {
            traverseNodeInRegion(node.getChild(i), region, outRect, builder)
        }
    }

    override fun extractScreenTextHierarchy(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val builder = StringBuilder()
        traverseNode(rootNode, builder)
        return builder.toString().trim()
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, builder: StringBuilder) {
        if (node == null) return

        val text = node.text
        if (!text.isNullOrEmpty()) {
            builder.append(text).append("\n")
        }

        val contentDesc = node.contentDescription
        if (!contentDesc.isNullOrEmpty() && contentDesc != text) {
            builder.append(contentDesc).append("\n")
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), builder)
        }
    }
}
