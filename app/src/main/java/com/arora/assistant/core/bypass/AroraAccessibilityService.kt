package com.arora.assistant.core.bypass

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AroraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AroraAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("AroraAccessibility", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.w("AroraAccessibility", "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // System Navigation Actions
    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun performQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun performLockScreen(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    } else false
    fun performSplitScreen(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
    } else false

    /**
     * Captures full screen directly using Accessibility API (Android 11+ / API 30+).
     * No prompts, no background service restrictions.
     */
    suspend fun takeScreenshotBitmap(): Bitmap? {
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

    /**
     * Extracts text ONLY from UI elements that intersect with the circled bounding box.
     */
    fun extractTextInRegion(region: RectF): String {
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

    fun extractScreenTextHierarchy(): String {
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
