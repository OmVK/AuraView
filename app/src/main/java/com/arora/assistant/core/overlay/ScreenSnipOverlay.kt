package com.arora.assistant.core.overlay

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.QuantumViolet

@Composable
fun ScreenSnipOverlay(
    onSnipCompleted: (RectF) -> Unit,
    onCancelled: () -> Unit
) {
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        startOffset = offset
                        currentOffset = offset
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentOffset = change.position
                    },
                    onDragEnd = {
                        val start = startOffset
                        val end = currentOffset
                        if (start != null && end != null) {
                            val left = minOf(start.x, end.x)
                            val top = minOf(start.y, end.y)
                            val right = maxOf(start.x, end.x)
                            val bottom = maxOf(start.y, end.y)

                            if (right - left > 20 && bottom - top > 20) {
                                onSnipCompleted(RectF(left, top, right, bottom))
                            } else {
                                onCancelled()
                            }
                        }
                    },
                    onDragCancel = {
                        onCancelled()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val start = startOffset
            val end = currentOffset

            // Dim background
            drawRect(color = Color.Black.copy(alpha = 0.35f))

            if (start != null && end != null) {
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                val width = kotlin.math.abs(end.x - start.x)
                val height = kotlin.math.abs(end.y - start.y)

                // Highlight snipped area border with dashed cyan line
                drawRect(
                    color = ElectricCyan,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(
                        width = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                )

                // Corner accents
                drawCircle(
                    color = QuantumViolet,
                    radius = 8f,
                    center = Offset(left, top)
                )
                drawCircle(
                    color = QuantumViolet,
                    radius = 8f,
                    center = Offset(left + width, top + height)
                )
            }
        }
    }
}
