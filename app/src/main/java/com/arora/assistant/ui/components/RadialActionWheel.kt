package com.arora.assistant.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassBorderActive
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.ObsidianBg
import com.arora.assistant.ui.theme.QuantumViolet
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class RadialActionItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val tint: Color = ElectricCyan,
    val onClick: () -> Unit
)

@Composable
fun RadialActionWheel(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    radius: Dp = 115.dp,
    items: List<RadialActionItem>,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var hoveredIndex by remember { mutableIntStateOf(-1) }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(tween(200)),
        exit = scaleOut(tween(150)) + fadeOut(tween(150))
    ) {
        Box(
            modifier = modifier
                .size(radius * 2 + 90.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val touch = change.position
                            val dx = touch.x - center.x
                            val dy = touch.y - center.y
                            val dist = hypot(dx, dy)

                            if (dist > 30f) {
                                var angle = atan2(dy.toDouble(), dx.toDouble()) + Math.PI / 2
                                if (angle < 0) angle += 2 * Math.PI
                                val sector = (2 * Math.PI) / items.size
                                val index = ((angle + sector / 2) / sector).toInt() % items.size
                                if (index != hoveredIndex) {
                                    hoveredIndex = index
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            } else {
                                hoveredIndex = -1
                            }
                        },
                        onDragEnd = {
                            if (hoveredIndex in items.indices) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                items[hoveredIndex].onClick()
                            }
                            onDismiss()
                        },
                        onDragCancel = {
                            onDismiss()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Ambient Neon Halo Ring
            Box(
                modifier = Modifier
                    .size(radius * 2 + 10.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                QuantumViolet.copy(alpha = 0.15f),
                                ElectricCyan.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )

            val angleStep = (2 * Math.PI) / items.size

            items.forEachIndexed { index, item ->
                val angle = index * angleStep - Math.PI / 2
                val x = (radius.value * cos(angle)).dp
                val y = (radius.value * sin(angle)).dp

                val isHovered = hoveredIndex == index
                val scaleAnim by animateFloatAsState(
                    targetValue = if (isHovered) 1.3f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "RadialItemScale"
                )

                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .scale(scaleAnim)
                        .size(52.dp)
                        .shadow(
                            elevation = if (isHovered) 16.dp else 6.dp,
                            shape = CircleShape,
                            ambientColor = if (isHovered) item.tint else Color.Black,
                            spotColor = item.tint
                        )
                        .clip(CircleShape)
                        .background(
                            if (isHovered) {
                                Brush.radialGradient(listOf(item.tint.copy(alpha = 0.9f), ObsidianBg))
                            } else {
                                Brush.radialGradient(listOf(GlassSurfaceHigh, ObsidianBg.copy(alpha = 0.9f)))
                            }
                        )
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            item.onClick()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (isHovered) Color.White else item.tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Central Tooltip HUD for active selection
            if (hoveredIndex in items.indices) {
                val activeItem = items[hoveredIndex]
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GlassSurfaceHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = activeItem.label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
