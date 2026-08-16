package com.arora.assistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.theme.CarbonCardBorder
import com.arora.assistant.ui.theme.CarbonElevated
import com.arora.assistant.ui.theme.CyberCyan
import com.arora.assistant.ui.theme.ElectricIndigo
import com.arora.assistant.ui.theme.HyperViolet
import com.arora.assistant.ui.theme.TextMuted

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1.0f, label = "buttonScale")

    val bgBrush = if (isPrimary) {
        Brush.horizontalGradient(listOf(HyperViolet, ElectricIndigo))
    } else {
        Brush.verticalGradient(listOf(CarbonElevated, CarbonElevated.copy(alpha = 0.9f)))
    }

    val borderBrush = if (!isPrimary) {
        Brush.linearGradient(listOf(CarbonCardBorder, CarbonCardBorder.copy(alpha = 0.4f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent))
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .shadow(
                elevation = if (isPrimary) 8.dp else 0.dp,
                shape = shape,
                ambientColor = if (isPrimary) HyperViolet.copy(alpha = 0.4f) else Color.Transparent,
                spotColor = if (isPrimary) HyperViolet else Color.Transparent
            )
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, borderBrush, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary) Color.White else HyperViolet,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (isPrimary) Color.White else TextMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}
