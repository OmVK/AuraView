package com.arora.assistant.core.overlay

import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender

data class TranslatedBlock(
    val boundingBox: Rect,
    val originalText: String,
    val translatedText: String
)

@Composable
fun InPlaceTranslateOverlay(
    blocks: List<TranslatedBlock>,
    targetLanguage: String,
    onClose: () -> Unit
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        // Render each translated block directly over its original screen bounding box
        blocks.forEach { block ->
            val leftDp = with(density) { block.boundingBox.left.toDp() }
            val topDp = with(density) { block.boundingBox.top.toDp() }
            val widthDp = with(density) { block.boundingBox.width().toDp() }
            val heightDp = with(density) { block.boundingBox.height().toDp() }

            Box(
                modifier = Modifier
                    .offset(x = leftDp, y = topDp)
                    .size(width = widthDp.coerceAtLeast(40.dp), height = heightDp.coerceAtLeast(20.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(SoftDarkBg.copy(alpha = 0.94f))
                    .border(1.dp, SoftLavender.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = block.translatedText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 13.sp
                )
            }
        }

        // Top Control Strip
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(SoftDarkBg.copy(alpha = 0.92f))
                    .border(1.dp, SoftLavender, RoundedCornerShape(30.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Translate, null, tint = SoftLavender, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AR In-Place Translation ($targetLanguage)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
