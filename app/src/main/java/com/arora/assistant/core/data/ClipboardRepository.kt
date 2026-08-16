package com.arora.assistant.core.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ClipboardEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val type: String = detectType(content)
) {
    val formattedTime: String
        get() = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))

    companion object {
        fun detectType(text: String): String {
            return when {
                text.startsWith("http://") || text.startsWith("https://") -> "URL"
                text.contains("{") && text.contains("}") || text.contains("class ") || text.contains("fun ") || text.contains("def ") -> "Code"
                text.contains("=") || text.contains("\\frac") || text.contains("\\sqrt") -> "Math"
                else -> "Text"
            }
        }
    }
}

class ClipboardRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("arora_clipboard_db", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _entries = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val entries: StateFlow<List<ClipboardEntry>> = _entries.asStateFlow()

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrentClip()
    }

    init {
        loadEntries()
        cleanupExpiredClips()
        try {
            clipboardManager.addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        captureCurrentClip()
    }

    private fun isSensitiveClip(clip: ClipData): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val description = clip.description
            if (description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true) {
                return true
            }
        }
        return false
    }

    private fun captureCurrentClip() {
        try {
            val clip = clipboardManager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                if (isSensitiveClip(clip)) return

                val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
                if (!text.isNullOrEmpty()) {
                    addEntry(text)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupExpiredClips() {
        val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
        val filtered = _entries.value.filter { it.isPinned || it.timestamp > thirtyMinutesAgo }
        if (filtered.size != _entries.value.size) {
            _entries.value = filtered
            saveEntries()
        }
    }

    @Synchronized
    fun addEntry(text: String) {
        val currentList = _entries.value.toMutableList()
        if (currentList.isNotEmpty() && currentList.first().content == text) {
            return
        }
        currentList.removeAll { it.content == text && !it.isPinned }
        currentList.add(0, ClipboardEntry(content = text))
        
        val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
        val validItems = currentList.filter { it.isPinned || it.timestamp > thirtyMinutesAgo }.take(50)

        _entries.value = validItems
        saveEntries()
    }

    fun togglePin(id: String) {
        val updated = _entries.value.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
        _entries.value = updated
        saveEntries()
    }

    fun deleteEntry(id: String) {
        val updated = _entries.value.filter { it.id != id }
        _entries.value = updated
        saveEntries()
    }

    fun clearAllUnpinned() {
        val pinnedOnly = _entries.value.filter { it.isPinned }
        _entries.value = pinnedOnly
        saveEntries()
    }

    fun copyToClipboard(text: String) {
        try {
            val clip = ClipData.newPlainText("Arora", text)
            clipboardManager.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        try {
            clipboardManager.removePrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadEntries() {
        val json = prefs.getString("clipboard_history", null) ?: return
        try {
            val type = object : TypeToken<List<ClipboardEntry>>() {}.type
            val list: List<ClipboardEntry> = gson.fromJson(json, type)
            _entries.value = list
        } catch (e: Exception) {
            _entries.value = emptyList()
        }
    }

    private fun saveEntries() {
        val json = gson.toJson(_entries.value)
        prefs.edit().putString("clipboard_history", json).apply()
    }
}
