package com.arora.assistant.core.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arora.assistant.ui.theme.AroraTheme
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated

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

    /**
     * Creates an independently draggable floating sub-window that can be placed anywhere on screen.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun createDraggableSubWindow(
        title: String,
        icon: ImageVector,
        widthDp: Int = 340,
        heightDp: Int = 460,
        isSecure: Boolean = false,
        initialYDp: Int? = null,
        onBackToMenu: (() -> Unit)? = null,
        onClose: () -> Unit,
        content: @Composable () -> Unit
    ): ComposeView {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val windowWidthPx = (widthDp * displayMetrics.density).toInt()
        val windowHeightPx = (heightDp * displayMetrics.density).toInt()

        val initialX = ((screenWidth - windowWidthPx) / 2).coerceAtLeast(0)
        val initialY = initialYDp?.let { (it * displayMetrics.density).toInt() } ?: ((screenHeight - windowHeightPx) / 2).coerceAtLeast(0)

        var windowFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        // Screen-Share & Screenshot Invisibility Shield
        if (isSecure) {
            windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_SECURE
        }

        val params = createLayoutParams(
            width = windowWidthPx,
            height = windowHeightPx,
            x = initialX,
            y = initialY,
            flags = windowFlags,
            gravity = Gravity.TOP or Gravity.START
        )

        lateinit var composeView: ComposeView
        var isMinimized by mutableStateOf(false)

        composeView = createFloatingComposeView(params) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ALWAYS keep window card & WebView mounted with persistent dimensions so audio decoding never stops
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (isMinimized) 0.005f else 1f)
                ) {
                    Box(
                        modifier = if (isMinimized) {
                            Modifier.requiredSize(widthDp.dp, heightDp.dp)
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        MovableSubWindowCard(
                            title = title,
                            icon = icon,
                            onDrag = { dx, dy ->
                                params.x = (params.x + dx.toInt()).coerceIn(0, screenWidth - windowWidthPx)
                                params.y = (params.y + dy.toInt()).coerceIn(0, screenHeight - 120)
                                updateViewLayout(composeView, params)
                            },
                            onMinimize = {
                                isMinimized = true
                                params.width = (64 * displayMetrics.density).toInt()
                                params.height = (64 * displayMetrics.density).toInt()
                                updateViewLayout(composeView, params)
                            },
                            onBackToMenu = onBackToMenu,
                            onClose = onClose,
                            content = content
                        )
                    }
                }

                // Render clean, non-clipped circular floating pill on top when minimized
                if (isMinimized) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MinimizedPill(
                            icon = icon,
                            onExpand = {
                                isMinimized = false
                                params.width = windowWidthPx
                                params.height = windowHeightPx
                                updateViewLayout(composeView, params)
                            },
                            onDrag = { dx, dy ->
                                params.x = (params.x + dx.toInt()).coerceIn(0, screenWidth - (64 * displayMetrics.density).toInt())
                                params.y = (params.y + dy.toInt()).coerceIn(0, screenHeight - (64 * displayMetrics.density).toInt())
                                updateViewLayout(composeView, params)
                            }
                        )
                    }
                }
            }
        }

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

@Composable
fun MovableSubWindowCard(
    title: String,
    icon: ImageVector,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onMinimize: () -> Unit,
    onBackToMenu: (() -> Unit)? = null,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = SkyOpal.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SoftSurfaceElevated.copy(alpha = 0.96f),
                        SoftDarkBg.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(SkyOpal.copy(alpha = 0.5f), SoftCardBorder)),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Draggable Window Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                SoftSurface.copy(alpha = 0.85f),
                                SoftSurfaceElevated.copy(alpha = 0.65f)
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (onBackToMenu != null) {
                        IconButton(
                            onClick = onBackToMenu,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Menu",
                                tint = SkyOpal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to Move",
                        tint = SkyOpal.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SoftLavender,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimize",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Window Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                content()
            }
        }
    }
}

@Composable
fun MinimizedPill(
    icon: ImageVector,
    onExpand: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(SoftSurfaceElevated, SoftDarkBg)
                )
            )
            .border(1.5.dp, SkyOpal, CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    var totalMovement = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            // Touch released / up
                            if (!isDrag) {
                                onExpand()
                            }
                            break
                        }
                        val dragDelta = change.position - change.previousPosition
                        totalMovement += kotlin.math.abs(dragDelta.x) + kotlin.math.abs(dragDelta.y)
                        if (totalMovement > 8f) {
                            isDrag = true
                            change.consume()
                            onDrag(dragDelta.x, dragDelta.y)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Expand Window",
            tint = SkyOpal,
            modifier = Modifier.size(26.dp)
        )
    }
}
