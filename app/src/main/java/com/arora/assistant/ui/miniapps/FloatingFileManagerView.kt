package com.arora.assistant.ui.miniapps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.util.Locale
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.arora.assistant.ui.components.GlassCard
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.NeonAmber
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.QuantumViolet
import java.io.File

@Composable
fun FloatingFileManagerView(onClose: () -> Unit) {
    val context = LocalContext.current
    val rootDir = remember { Environment.getExternalStorageDirectory() }
    var currentDir by remember { mutableStateOf(rootDir) }
    var searchQuery by remember { mutableStateOf("") }

    val fileList = remember(currentDir, searchQuery) {
        try {
            val files = currentDir.listFiles()?.toList() ?: emptyList()
            val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            if (searchQuery.isNotEmpty()) {
                sorted.filter { it.name.contains(searchQuery, ignoreCase = true) }
            } else {
                sorted
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp),
        borderGlow = true
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentDir != rootDir && currentDir.parentFile != null) {
                    IconButton(
                        onClick = { currentDir = currentDir.parentFile!! },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = ElectricCyan)
                    }
                } else {
                    Icon(Icons.Default.Folder, null, tint = NeonAmber, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = currentDir.name.ifEmpty { "Storage" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                placeholder = { Text("Filter files...", fontSize = 12.sp, color = Color.Gray) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ElectricCyan, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = GlassSurfaceHigh,
                    unfocusedContainerColor = GlassSurfaceHigh,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = ElectricCyan,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // File List
            if (fileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Empty folder", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(fileList, key = { it.absolutePath }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassSurfaceHigh)
                                .clickable {
                                    if (file.isDirectory) {
                                        currentDir = file
                                        searchQuery = ""
                                    } else {
                                        openFile(context, file)
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (icon, tint) = getFileIconAndTint(file)
                            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (file.isDirectory) "${file.list()?.size ?: 0} items" else formatFileSize(file.length()),
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getFileIconAndTint(file: File): Pair<ImageVector, Color> {
    if (file.isDirectory) return Pair(Icons.Default.Folder, NeonAmber)
    val ext = file.extension.lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp" -> Pair(Icons.Default.Image, ElectricCyan)
        "mp4", "mkv", "avi", "webm" -> Pair(Icons.Default.Movie, QuantumViolet)
        "mp3", "flac", "wav", "m4a" -> Pair(Icons.Default.MusicNote, NeonEmerald)
        else -> Pair(Icons.Default.Description, Color.LightGray)
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun openFile(context: Context, file: File) {
    try {
        val extension = file.extension
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to open ${file.name}", Toast.LENGTH_SHORT).show()
    }
}
