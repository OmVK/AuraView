package com.arora.assistant.core.service

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

interface AccessibilityDelegate {
    fun performBack(): Boolean
    fun performHome(): Boolean
    fun performRecents(): Boolean
    fun performNotifications(): Boolean
    fun performQuickSettings(): Boolean
    fun performLockScreen(): Boolean
    fun performSplitScreen(): Boolean
    suspend fun takeScreenshot(): Bitmap?
    fun extractTextInRegion(region: RectF): String
    fun extractScreenTextHierarchy(): String
    fun getActiveRootNode(): AccessibilityNodeInfo?
    fun clickAtCoordinates(x: Float, y: Float): Boolean
}

interface MediaProjectionDelegate {
    suspend fun captureScreen(cropRect: RectF? = null): Bitmap?
}

/**
 * Thread-safe Service State Coordinator and Decoupler.
 * Services and UI communicate exclusively through StateFlow and weak delegates,
 * eliminating direct inter-service coupling and memory leaks.
 */
object ServiceStateManager {

    // --- StateFlow Service Lifecycle Status ---
    private val _isFloatingBallActive = MutableStateFlow(false)
    val isFloatingBallActive: StateFlow<Boolean> = _isFloatingBallActive.asStateFlow()

    private val _isAccessibilityActive = MutableStateFlow(false)
    val isAccessibilityActive: StateFlow<Boolean> = _isAccessibilityActive.asStateFlow()

    private val _isMediaProjectionActive = MutableStateFlow(false)
    val isMediaProjectionActive: StateFlow<Boolean> = _isMediaProjectionActive.asStateFlow()

    private val _isSpeedMonitorActive = MutableStateFlow(false)
    val isSpeedMonitorActive: StateFlow<Boolean> = _isSpeedMonitorActive.asStateFlow()

    private val _isVolumeBoosterActive = MutableStateFlow(false)
    val isVolumeBoosterActive: StateFlow<Boolean> = _isVolumeBoosterActive.asStateFlow()

    // --- Weak Reference Delegates to Prevent Memory Leaks ---
    private var accessibilityDelegateRef: WeakReference<AccessibilityDelegate>? = null
    private var mediaProjectionDelegateRef: WeakReference<MediaProjectionDelegate>? = null

    // Register / Unregister Accessibility
    fun registerAccessibilityDelegate(delegate: AccessibilityDelegate) {
        accessibilityDelegateRef = WeakReference(delegate)
        _isAccessibilityActive.value = true
    }

    fun unregisterAccessibilityDelegate() {
        accessibilityDelegateRef = null
        _isAccessibilityActive.value = false
    }

    // Register / Unregister MediaProjection
    fun registerMediaProjectionDelegate(delegate: MediaProjectionDelegate) {
        mediaProjectionDelegateRef = WeakReference(delegate)
        _isMediaProjectionActive.value = true
    }

    fun unregisterMediaProjectionDelegate() {
        mediaProjectionDelegateRef = null
        _isMediaProjectionActive.value = false
    }

    // Service Lifecycle Setters
    fun setFloatingBallActive(active: Boolean) {
        _isFloatingBallActive.value = active
    }

    fun setSpeedMonitorActive(active: Boolean) {
        _isSpeedMonitorActive.value = active
    }

    fun setVolumeBoosterActive(active: Boolean) {
        _isVolumeBoosterActive.value = active
    }

    // Decoupled Helper Actions
    fun performBack(): Boolean = accessibilityDelegateRef?.get()?.performBack() ?: false
    fun performHome(): Boolean = accessibilityDelegateRef?.get()?.performHome() ?: false
    fun performRecents(): Boolean = accessibilityDelegateRef?.get()?.performRecents() ?: false
    fun performNotifications(): Boolean = accessibilityDelegateRef?.get()?.performNotifications() ?: false
    fun performQuickSettings(): Boolean = accessibilityDelegateRef?.get()?.performQuickSettings() ?: false
    fun performLockScreen(): Boolean = accessibilityDelegateRef?.get()?.performLockScreen() ?: false
    fun performSplitScreen(): Boolean = accessibilityDelegateRef?.get()?.performSplitScreen() ?: false

    suspend fun takeScreenshot(): Bitmap? {
        // Priority 1: Accessibility API (Android 11+)
        val a11Screenshot = accessibilityDelegateRef?.get()?.takeScreenshot()
        if (a11Screenshot != null) return a11Screenshot

        // Priority 2: MediaProjection Virtual Display
        return mediaProjectionDelegateRef?.get()?.captureScreen()
    }

    fun extractTextInRegion(region: RectF): String =
        accessibilityDelegateRef?.get()?.extractTextInRegion(region) ?: ""

    fun extractScreenTextHierarchy(): String =
        accessibilityDelegateRef?.get()?.extractScreenTextHierarchy() ?: ""

    fun getActiveRootNode(): AccessibilityNodeInfo? =
        accessibilityDelegateRef?.get()?.getActiveRootNode()

    fun clickAtCoordinates(x: Float, y: Float): Boolean =
        accessibilityDelegateRef?.get()?.clickAtCoordinates(x, y) ?: false
}
