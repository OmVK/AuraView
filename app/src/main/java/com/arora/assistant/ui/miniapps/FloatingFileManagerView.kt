package com.arora.assistant.ui.miniapps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.arora.assistant.core.ai.PersonalDocumentRag
import com.arora.assistant.core.ai.RagSearchResult
import com.arora.assistant.ui.theme.ElectricCyan
import com.arora.assistant.ui.theme.GlassSurfaceHigh
import com.arora.assistant.ui.theme.NeonAmber
import com.arora.assistant.ui.theme.NeonEmerald
import com.arora.assistant.ui.theme.QuantumViolet
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun FloatingFileManagerView(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = remember { Environment.getExternalStorageDirectory() }
    var currentDir by remember { mutableStateOf(rootDir) }
    var searchQuery by remember { mutableStateOf("") }

    var isRagSearchMode by remember { mutableStateOf(false) }
    var ragResults by remember { mutableStateOf<List<RagSearchResult>>(emptyList()) }
    var isSearchingRag by remember { mutableStateOf(false) }

    val fileList = remember(currentDir, searchQuery, isRagSearchMode) {
        if (isRagSearchMode) emptyList()
        else {
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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Path Navigation & RAG Toggle Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (!isRagSearchMode && currentDir != rootDir && currentDir.parentFile != null) {
                    IconButton(
                        onClick = { currentDir = currentDir.parentFile!! },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Parent Directory",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Icon(
                    imageVector = if (isRagSearchMode) Icons.Default.Description else Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isRagSearchMode) ElectricCyan else NeonAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = if (isRagSearchMode) "Local Docs RAG Search" else currentDir.name.ifEmpty { "Storage" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isRagSearchMode) ElectricCyan else GlassSurfaceHigh)
                    .clickable {
                        isRagSearchMode = !isRagSearchMode
                        if (!isRagSearchMode) ragResults = emptyList()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isRagSearchMode) "📂 Files" else "🧠 RAG Search",
                    color = if (isRagSearchMode) Color.Black else ElectricCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (isRagSearchMode && it.length > 2) {
                    scope.launch {
                        isSearchingRag = true
                        ragResults = PersonalDocumentRag.searchLocalKnowledgeBase(it)
                        isSearchingRag = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            placeholder = { Text(if (isRagSearchMode) "Search inside docs & notes..." else "Filter files...", fontSize = 12.sp, color = Color.Gray) },
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

        if (isRagSearchMode) {
            // RAG Results List
            if (isSearchingRag) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Searching local documents...", color = Color.Gray, fontSize = 12.sp)
                }
            } else if (ragResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (searchQuery.isBlank()) "Type a keyword to search inside local notes & docs" else "No matching snippets found", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ragResults) { res ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(GlassSurfaceHigh)
                                .padding(10.dp)
                        ) {
                            Text(res.fileName, color = ElectricCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(res.matchedSnippet, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        } else {
            // Standard File List
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
