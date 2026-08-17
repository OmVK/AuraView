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
        if (!textList.isNullOrEmpty()) {
            val combined = textList.joinToString(" ")
            com.arora.assistant.core.ai.ProactiveIntelligenceService.inspectScreenContent(combined, pkg)
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
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                            val copy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            buffer.close()
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
