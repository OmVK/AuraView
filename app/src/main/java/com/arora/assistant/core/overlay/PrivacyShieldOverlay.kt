package com.arora.assistant.core.overlay

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextPureWhite

@Composable
fun PrivacyShieldComposable(
    onDragDelta: (Float, Float) -> Unit = { _, _ -> },
    onClose: () -> Unit
) {
    val view = LocalView.current
    var opacity by remember { mutableFloatStateOf(0.95f) }
    var heightDp by remember { mutableFloatStateOf(200f) }
    var isControlExpanded by remember { mutableStateOf(false) }
    var isCamouflaged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .shadow(16.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = if (isCamouflaged) 0.98f else opacity))
            .border(1.5.dp, if (isCamouflaged) SageMint.copy(alpha = 0.6f) else SoftLavender.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
    ) {
        // Privacy Bar Header (Draggable Handle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SoftSurfaceElevated.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag Handle",
                    tint = SkyOpal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = if (isCamouflaged) "Calculator (Camouflage)" else "🛡️ Privacy Shield",
                        color = TextPureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                    Text(
                        text = "Drag to move • Blocks shoulder-surfing",
                        color = TextMuted,
                        fontSize = 9.5.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Camouflage toggle
                IconButton(
                    onClick = { isCamouflaged = !isCamouflaged },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        "Toggle Camouflage",
                        tint = if (isCamouflaged) SageMint else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Controls toggle
                IconButton(
                    onClick = { isControlExpanded = !isControlExpanded },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        if (isControlExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Toggle controls",
                        tint = SkyOpal,
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Close button
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClose()
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Expandable Opacity & Height Controls
        if (isControlExpanded && !isCamouflaged) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SoftSurface.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity: ${(opacity * 100).toInt()}%", color = TextMuted, fontSize = 11.sp)
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.3f..1.0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = SoftLavender, activeTrackColor = SoftLavender)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Height: ${heightDp.toInt()}dp", color = TextMuted, fontSize = 11.sp)
                    Slider(
                        value = heightDp,
                        onValueChange = { heightDp = it },
                        valueRange = 100f..500f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = SkyOpal, activeTrackColor = SkyOpal)
                    )
                }
            }
        }

        // Shield Curtain Body (Blackout vs Camouflage mode)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCamouflaged) 190.dp else heightDp.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isCamouflaged) {
                // Realistic faux calculator display to deter onlookers
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SoftSurface)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text("3,492.50", color = TextPureWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("7", "8", "9", "÷").forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SoftSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(digit, color = TextPureWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("4", "5", "6", "×").forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SoftSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(digit, color = TextPureWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "🛡️ SHIELDED REGION\n(Drag anywhere to move)",
                    color = Color.White.copy(alpha = 0.35f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
