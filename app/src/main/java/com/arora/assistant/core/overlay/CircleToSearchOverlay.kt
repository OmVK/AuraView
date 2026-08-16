package com.arora.assistant.core.overlay

import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CircleToSearchOverlay(
    onRegionSelected: (RectF) -> Unit,
    onCancelled: () -> Unit
) {
    val view = LocalView.current
    val strokePoints = remember { mutableStateListOf<Offset>() }
    val rawPoints = remember { mutableStateListOf<Offset>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftDarkBg.copy(alpha = 0.32f))
            .pointerInteropFilter { motionEvent ->
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        strokePoints.clear()
                        rawPoints.clear()
                        strokePoints.add(Offset(motionEvent.x, motionEvent.y))
                        rawPoints.add(Offset(motionEvent.rawX, motionEvent.rawY))
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        strokePoints.add(Offset(motionEvent.x, motionEvent.y))
                        rawPoints.add(Offset(motionEvent.rawX, motionEvent.rawY))
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (rawPoints.size > 5) {
                            var minX = Float.MAX_VALUE
                            var minY = Float.MAX_VALUE
                            var maxX = Float.MIN_VALUE
                            var maxY = Float.MIN_VALUE

                            // Calculate using raw screen coordinates (100% immune to window offsets & status bar insets)
                            for (pt in rawPoints) {
                                if (pt.x < minX) minX = pt.x
                                if (pt.y < minY) minY = pt.y
                                if (pt.x > maxX) maxX = pt.x
                                if (pt.y > maxY) maxY = pt.y
                            }

                            val width = maxX - minX
                            val height = maxY - minY

                            if (width > 15 && height > 15) {
                                val rect = RectF(minX, minY, maxX, maxY)
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onRegionSelected(rect)
                            } else {
                                onCancelled()
                            }
                        } else {
                            onCancelled()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        onCancelled()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Glowing Canvas for visual feedback
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (strokePoints.size > 1) {
                val path = Path().apply {
                    moveTo(strokePoints[0].x, strokePoints[0].y)
                    for (i in 1 until strokePoints.size) {
                        lineTo(strokePoints[i].x, strokePoints[i].y)
                    }
                }

                // Ambient Glow Layer
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(listOf(SoftLavender.copy(alpha = 0.5f), SkyOpal.copy(alpha = 0.5f))),
                    style = Stroke(
                        width = 14f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Core Crisp Line
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(listOf(Color.White, SoftLavender)),
                    style = Stroke(
                        width = 4f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Top Instruction Header
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(SoftDarkBg.copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TouchApp, null, tint = SoftLavender, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Circle or draw around anything on screen",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(
                    onClick = onCancelled,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Close, "Cancel", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
