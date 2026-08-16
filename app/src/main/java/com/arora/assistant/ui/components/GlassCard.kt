package com.arora.assistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arora.assistant.ui.theme.CarbonCardBorder
import com.arora.assistant.ui.theme.CarbonElevated
import com.arora.assistant.ui.theme.CarbonSurface
import com.arora.assistant.ui.theme.HyperViolet

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    borderGlow: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed && onClick != null) 0.98f else 1.0f, label = "cardScale")

    val borderBrush = if (borderGlow) {
        Brush.linearGradient(listOf(HyperViolet.copy(alpha = 0.6f), CarbonCardBorder, HyperViolet.copy(alpha = 0.2f)))
    } else {
        Brush.linearGradient(listOf(CarbonCardBorder.copy(alpha = 0.8f), CarbonCardBorder.copy(alpha = 0.3f)))
    }

    val bgBrush = Brush.verticalGradient(
        listOf(
            CarbonElevated.copy(alpha = 0.85f),
            CarbonSurface.copy(alpha = 0.95f)
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (borderGlow) 12.dp else 4.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = if (borderGlow) HyperViolet.copy(alpha = 0.3f) else Color.Transparent
            )
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(16.dp),
        content = content
    )
}
