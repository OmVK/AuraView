package com.arora.assistant.ui.miniapps

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.core.data.ClipboardRepository
import com.arora.assistant.ui.components.GlassCard
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.NeonAmber
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.QuantumViolet
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
fun FloatingClipboardView(
    repository: ClipboardRepository? = null,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repo = remember { repository ?: ClipboardRepository(context) }
    val entries by repo.entries.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Auto-capture clipboard on open
    LaunchedEffect(Unit) {
        repo.captureCurrentClip()
    }

    val categories = listOf("All", "Pinned", "Code", "URL", "Math")

    val filteredEntries = entries.filter { entry ->
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Pinned" -> entry.isPinned
            else -> entry.type.equals(selectedCategory, ignoreCase = true)
        }
        val matchesSearch = searchQuery.isEmpty() || entry.content.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Toolbar Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋 Clipboard History", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SkyOpal.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text("${entries.size} clips", color = SkyOpal, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sync current clip button
                IconButton(
                    onClick = {
                        val added = repo.captureCurrentClip()
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        Toast.makeText(context, if (added) "Current clipboard captured" else "Clipboard synced", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Sync Clipboard", tint = SkyOpal, modifier = Modifier.size(16.dp))
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Clear unpinned
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        repo.clearAllUnpinned()
                        Toast.makeText(context, "Unpinned clips cleared", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ClearAll, "Clear Unpinned", tint = PastelRose, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            placeholder = { Text("Search clipboard history...", fontSize = 11.5.sp, color = TextMuted) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = SkyOpal, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SoftSurfaceElevated,
                unfocusedContainerColor = SoftSurfaceElevated,
                focusedTextColor = TextPureWhite,
                unfocusedTextColor = TextPureWhite,
                focusedIndicatorColor = SkyOpal,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) SkyOpal else SoftSurfaceElevated)
                        .border(1.dp, if (isSelected) SkyOpal else SoftCardBorder, RoundedCornerShape(10.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.Black else TextOffWhite,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List
        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ContentPaste, null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("No clipboard entries found", color = TextMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Copy any text in any app to see it here", color = TextMuted.copy(alpha = 0.7f), fontSize = 10.5.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredEntries, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SoftSurfaceElevated)
                            .border(0.8.dp, SoftCardBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                repo.copyToClipboard(item.content)
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Type badge
                                Text(
                                    text = item.type,
                                    color = when (item.type) {
                                        "Code" -> SoftLavender
                                        "URL" -> SkyOpal
                                        "Math" -> NeonEmerald
                                        else -> TextMuted
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = item.formattedTime, color = TextMuted, fontSize = 10.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.content,
                                color = TextPureWhite,
                                fontSize = 12.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Smart Action Chip
                            val smartAction = remember(item.content) {
                                com.arora.assistant.core.ai.SmartClipboardAnalyzer.analyzeClip(context, item.content)
                            }
                            if (smartAction != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SkyOpal.copy(alpha = 0.2f))
                                        .clickable {
                                            try {
                                                context.startActivity(smartAction.actionIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Cannot handle action", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⚡ ${smartAction.title}",
                                        color = SkyOpal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Pin Button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                repo.togglePin(item.id)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pin",
                                tint = if (item.isPinned) NeonAmber else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Delete Button
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                repo.deleteEntry(item.id)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
