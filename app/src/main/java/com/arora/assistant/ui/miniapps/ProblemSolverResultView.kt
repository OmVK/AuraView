package com.arora.assistant.ui.miniapps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.components.GlassCard
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.QuantumViolet

@Composable
fun ProblemSolverResultView(
    solutionText: String?,
    isLoading: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp),
        borderGlow = true
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "AuraView AI Solver",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    CircularProgressIndicator(color = ElectricCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analyzing math & diagram context...", color = Color.White, fontSize = 14.sp)
                }
            } else {
                // Content Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassSurfaceHigh)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = solutionText ?: "No solution generated.",
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(modifier = Modifier.fillMaxWidth()) {
                    NeonButton(
                        text = "Copy LaTeX",
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Solution", solutionText ?: ""))
                            Toast.makeText(context, "Solution copied", Toast.LENGTH_SHORT).show()
                        },
                        icon = Icons.Default.ContentCopy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
