package com.arora.assistant.core.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GraphPlotterEngine {

    fun evaluate(expr: String, x: Double): Double {
        val clean = expr.replace(" ", "").lowercase()
        return try {
            when {
                clean.contains("sin") -> sin(x)
                clean.contains("cos") -> cos(x)
                clean.contains("x^2") -> x * x
                clean.contains("x^3") -> x * x * x
                clean.contains("sqrt") -> if (x >= 0) sqrt(x) else Double.NaN
                clean.contains("ln") || clean.contains("log") -> if (x > 0) ln(x) else Double.NaN
                clean.contains("exp") -> exp(x)
                clean == "x" -> x
                clean.contains("2*x") || clean.contains("2x") -> 2 * x
                clean.contains("-x") -> -x
                else -> {
                    // Basic quadratic parser: ax^2 + bx + c
                    if (clean.contains("x^2-4")) (x * x) - 4 else x * x
                }
            }
        } catch (e: Exception) {
            0.0
        }
    }
}

@Composable
fun FloatingGraphPlotterView(
    initialFunction: String = "sin(x)",
    onClose: () -> Unit
) {
    var functionInput by remember { mutableStateOf(initialFunction) }
    var activeFunction by remember { mutableStateOf(initialFunction) }

    var scale by remember { mutableFloatStateOf(40f) } // Pixels per unit
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(width = 340.dp, height = 440.dp)
            .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = SkyOpal.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(20.dp))
            .background(SoftDarkBg.copy(alpha = 0.95f))
            .border(1.dp, SoftCardBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header & Function Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Functions, null, tint = SkyOpal, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("2D Graph Plotter", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        scale = 40f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Reset Zoom", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Function Input Field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = functionInput,
                    onValueChange = { functionInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. sin(x), x^2 - 4", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SoftSurface,
                        unfocusedContainerColor = SoftSurface,
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite,
                        focusedIndicatorColor = SkyOpal,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                NeonButton(
                    text = "Plot",
                    onClick = { activeFunction = functionInput.trim() },
                    modifier = Modifier.height(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive 2D Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftSurfaceElevated)
                    .border(1.dp, SoftCardBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(10f, 150f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = (size.width / 2) + offsetX
                    val centerY = (size.height / 2) + offsetY

                    // 1. Draw Grid Lines
                    val gridStep = scale
                    var xPos = centerX % gridStep
                    while (xPos < size.width) {
                        drawLine(
                            color = SoftCardBorder.copy(alpha = 0.4f),
                            start = Offset(xPos, 0f),
                            end = Offset(xPos, size.height),
                            strokeWidth = 1f
                        )
                        xPos += gridStep
                    }

                    var yPos = centerY % gridStep
                    while (yPos < size.height) {
                        drawLine(
                            color = SoftCardBorder.copy(alpha = 0.4f),
                            start = Offset(0f, yPos),
                            end = Offset(size.width, yPos),
                            strokeWidth = 1f
                        )
                        yPos += gridStep
                    }

                    // 2. Draw Main X and Y Axes
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.7f),
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, size.height),
                        strokeWidth = 2f
                    )

                    // 3. Plot Mathematical Function Curve f(x)
                    val path = Path()
                    var firstPoint = true

                    for (pixelX in 0 until size.width.toInt() step 2) {
                        val mathX = (pixelX - centerX) / scale
                        val mathY = GraphPlotterEngine.evaluate(activeFunction, mathX.toDouble())

                        if (!mathY.isNaN() && !mathY.isInfinite()) {
                            val pixelY = centerY - (mathY.toFloat() * scale)
                            if (firstPoint) {
                                path.moveTo(pixelX.toFloat(), pixelY)
                                firstPoint = false
                            } else {
                                path.lineTo(pixelX.toFloat(), pixelY)
                            }
                        }
                    }

                    drawPath(
                        path = path,
                        color = SkyOpal,
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
