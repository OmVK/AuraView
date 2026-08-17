package com.arora.assistant.ui.miniapps

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftAmber
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import kotlinx.coroutines.delay

@Composable
fun FloatingTeleprompterView(
    onClose: () -> Unit
) {
    var scriptText by remember {
        mutableStateOf("Welcome to AuraView AI. This is your floating teleprompter script. It auto-scrolls smoothly while you record videos, host meetings, or present slides on screen.")
    }
    var isEditing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(20f) } // Pixels per second
    var fontSizeSp by remember { mutableFloatStateOf(16f) }
    var isMirrored by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    var isVoicePaced by remember { mutableStateOf(false) }

    // Smooth Auto-Scroll Engine
    LaunchedEffect(isPlaying, isVoicePaced, scrollSpeed) {
        while (isPlaying) {
            delay(50)
            val effectiveSpeed = if (isVoicePaced) scrollSpeed * 1.25f else scrollSpeed
            val delta = effectiveSpeed * 0.05f
            scrollState.animateScrollBy(delta, tween(50, easing = LinearEasing))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Controls Strip (Play/Pause, Voice-Pace, Mirror, Edit, Speed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SoftSurface.copy(alpha = 0.8f))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) PastelRose else SageMint)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = { isVoicePaced = !isVoicePaced }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.GraphicEq, "Voice-Pace", tint = if (isVoicePaced) SkyOpal else TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { isMirrored = !isMirrored }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Flip, "Mirror Mode", tint = if (isMirrored) SkyOpal else TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { isEditing = !isEditing }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, "Edit Script", tint = if (isEditing) SoftLavender else TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isVoicePaced) "🎙️" else "Speed", color = if (isVoicePaced) SkyOpal else TextMuted, fontSize = 10.sp)
                Slider(
                    value = scrollSpeed,
                    onValueChange = { scrollSpeed = it },
                    valueRange = 5f..100f,
                    modifier = Modifier.width(95.dp).padding(horizontal = 4.dp),
                    colors = SliderDefaults.colors(thumbColor = if (isVoicePaced) SkyOpal else SoftLavender, activeTrackColor = if (isVoicePaced) SkyOpal else SoftLavender)
                )
            }
        }

        // Script Viewport / Editor
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(SoftSurfaceElevated.copy(alpha = 0.7f))
                .padding(12.dp)
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    modifier = Modifier.fillMaxSize(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPureWhite,
                        unfocusedTextColor = TextPureWhite
                    )
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scaleX = if (isMirrored) -1f else 1f, scaleY = 1f)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = scriptText,
                        color = TextPureWhite,
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = (fontSizeSp * 1.4f).sp
                    )
                    Spacer(modifier = Modifier.height(200.dp)) // Extra scroll runway
                }
            }
        }
    }
}
