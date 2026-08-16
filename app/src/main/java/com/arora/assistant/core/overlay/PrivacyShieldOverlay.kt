package com.arora.assistant.core.overlay

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
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
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite

@Composable
fun PrivacyShieldComposable(
    onClose: () -> Unit
) {
    val view = LocalView.current
    var opacity by remember { mutableFloatStateOf(0.92f) }
    var heightDp by remember { mutableFloatStateOf(180f) }
    var isControlExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = opacity))
            .border(1.5.dp, SoftLavender.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        // Privacy Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SoftSurfaceElevated.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = SoftLavender, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Privacy Shield", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
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
        if (isControlExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SoftSurface.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacity: ${(opacity * 100).toInt()}%", color = TextMuted, fontSize = 11.sp)
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.4f..1.0f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = SoftLavender, activeTrackColor = SoftLavender)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Height: ${heightDp.toInt()}dp", color = TextMuted, fontSize = 11.sp)
                    Slider(
                        value = heightDp,
                        onValueChange = { heightDp = it },
                        valueRange = 100f..450f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = SkyOpal, activeTrackColor = SkyOpal)
                    )
                }
            }
        }

        // Shield Curtain Body (Blacked out / Blur shield)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🛡️ PROTECTED CONTENT",
                color = Color.White.copy(alpha = 0.25f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = 13.sp
            )
        }
    }
}
