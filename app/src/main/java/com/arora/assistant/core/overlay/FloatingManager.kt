package com.arora.assistant.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arora.assistant.ui.theme.AroraTheme

class FloatingManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    }

    fun createFloatingComposeView(
        layoutParams: WindowManager.LayoutParams,
        content: @Composable () -> Unit
    ): ComposeView {
        val lifecycleOwner = OverlayLifecycleOwner()
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                AroraTheme {
                    content()
                }
            }
        }
        windowManager.addView(composeView, layoutParams)
        return composeView
    }

    fun updateViewLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            // View may already be detached
        }
    }

    fun removeView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // View may already be detached
        }
    }

    companion object {
        fun createLayoutParams(
            width: Int = WindowManager.LayoutParams.WRAP_CONTENT,
            height: Int = WindowManager.LayoutParams.WRAP_CONTENT,
            x: Int = 0,
            y: Int = 0,
            flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            gravity: Int = Gravity.TOP or Gravity.START
        ): WindowManager.LayoutParams {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            return WindowManager.LayoutParams(
                width,
                height,
                type,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
                this.x = x
                this.y = y
            }
        }
    }
}
