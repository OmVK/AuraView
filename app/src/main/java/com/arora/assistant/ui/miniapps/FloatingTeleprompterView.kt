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

    // Smooth Auto-Scroll Engine
    LaunchedEffect(isPlaying, scrollSpeed) {
        while (isPlaying) {
            delay(50)
            val delta = scrollSpeed * 0.05f
            scrollState.animateScrollBy(delta, tween(50, easing = LinearEasing))
        }
    }

    Box(
        modifier = Modifier
            .size(width = 340.dp, height = 440.dp)
            .shadow(24.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = SoftLavender.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(20.dp))
            .background(SoftDarkBg.copy(alpha = 0.85f)) // Translucent for transparent viewing
            .border(1.dp, SoftLavender.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TextFields, null, tint = SoftLavender, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Floating Teleprompter", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))

                IconButton(onClick = { isMirrored = !isMirrored }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Flip, "Mirror Mode", tint = if (isMirrored) SkyOpal else TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { isEditing = !isEditing }, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Edit, "Edit Script", tint = if (isEditing) SoftLavender else TextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Play / Speed Controls Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SoftSurface.copy(alpha = 0.8f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) PastelRose else SageMint)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed", color = TextMuted, fontSize = 11.sp)
                    Slider(
                        value = scrollSpeed,
                        onValueChange = { scrollSpeed = it },
                        valueRange = 5f..100f,
                        modifier = Modifier.width(140.dp).padding(horizontal = 6.dp),
                        colors = SliderDefaults.colors(thumbColor = SoftLavender, activeTrackColor = SoftLavender)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
}
