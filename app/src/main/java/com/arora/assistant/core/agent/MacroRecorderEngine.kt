package com.arora.assistant.core.agent

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.arora.assistant.core.service.ServiceStateManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class MacroStep(
    val id: String = UUID.randomUUID().toString(),
    val eventType: String, // CLICK, INPUT, SCROLL, DELAY
    val targetText: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val delayMs: Long = 400
)

data class SavedMacro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep>,
    val createdAt: Long = System.currentTimeMillis()
)

object MacroRecorderEngine {

    private const val PREFS_NAME = "arora_saved_macros_db"
    private const val KEY_MACROS = "saved_macros_json"
    private val gson = Gson()

    private val _recordedSteps = mutableListOf<MacroStep>()
    var isRecording = false
        private set

    private var lastEventTime = 0L

    private val _savedMacrosFlow = MutableStateFlow<List<SavedMacro>>(emptyList())
    val savedMacrosFlow: StateFlow<List<SavedMacro>> = _savedMacrosFlow.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadSavedMacros()
        }
    }

    fun startRecording() {
        _recordedSteps.clear()
        lastEventTime = System.currentTimeMillis()
        isRecording = true
        Log.d("MacroRecorder", "🔴 Macro recording started")
    }

    fun logStep(eventType: String, targetText: String, x: Float = 0f, y: Float = 0f) {
        if (!isRecording) return
        val now = System.currentTimeMillis()
        val delta = if (lastEventTime > 0) (now - lastEventTime).coerceIn(200, 2000) else 400
        lastEventTime = now

        // Prevent duplicate spam within 100ms
        if (_recordedSteps.isNotEmpty()) {
            val last = _recordedSteps.last()
            if (last.eventType == eventType && last.targetText == targetText && (now - lastEventTime < 100)) {
                return
            }
        }

        val step = MacroStep(
            eventType = eventType,
            targetText = targetText,
            x = x,
            y = y,
            delayMs = delta
        )
        _recordedSteps.add(step)
        Log.d("MacroRecorder", "Logged Step: $eventType on '$targetText' ($x, $y)")
    }

    fun stopRecording(): List<MacroStep> {
        isRecording = false
        Log.d("MacroRecorder", "⏹️ Macro recording stopped. Total steps: ${_recordedSteps.size}")
        return _recordedSteps.toList()
    }

    fun getCurrentRecordedSteps(): List<MacroStep> = _recordedSteps.toList()

    fun saveMacro(name: String, steps: List<MacroStep>): SavedMacro {
        val macro = SavedMacro(
            name = name.ifBlank { "Custom Macro #${_savedMacrosFlow.value.size + 1}" },
            steps = steps
        )
        val updated = _savedMacrosFlow.value.toMutableList().apply { add(0, macro) }
        _savedMacrosFlow.value = updated
        persistMacros()
        return macro
    }

    fun deleteMacro(id: String) {
        val updated = _savedMacrosFlow.value.filter { it.id != id }
        _savedMacrosFlow.value = updated
        persistMacros()
    }

    fun replayMacro(
        macro: SavedMacro,
        scope: CoroutineScope,
        onProgress: (String) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        scope.launch(Dispatchers.Main) {
            onProgress("Starting macro: ${macro.name}")
            delay(500)

            for ((index, step) in macro.steps.withIndex()) {
                onProgress("Step ${index + 1}/${macro.steps.size}: ${step.eventType} ${step.targetText}")
                
                when (step.eventType.uppercase()) {
                    "CLICK" -> {
                        if (step.x > 0f && step.y > 0f) {
                            ServiceStateManager.clickAtCoordinates(step.x, step.y)
                        } else {
                            // Try finding node
                            val root = ServiceStateManager.getActiveRootNode()
                            if (root != null && step.targetText.isNotBlank()) {
                                val rect = Rect()
                                val node = findNodeByText(root, step.targetText)
                                if (node != null) {
                                    node.getBoundsInScreen(rect)
                                    if (!rect.isEmpty) {
                                        ServiceStateManager.clickAtCoordinates(rect.exactCenterX(), rect.exactCenterY())
                                    } else {
                                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                                    }
                                }
                            }
                        }
                    }
                    "SCROLL" -> {
                        val root = ServiceStateManager.getActiveRootNode()
                        root?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    }
                    "INPUT" -> {
                        val root = ServiceStateManager.getActiveRootNode()
                        val node = root?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                        if (node != null && step.targetText.isNotBlank()) {
                            val args = android.os.Bundle().apply {
                                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.targetText)
                            }
                            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                        }
                    }
                }
                delay(step.delayMs)
            }

            onProgress("✅ Completed macro: ${macro.name}")
            delay(300)
            onComplete(true)
        }
    }

    private fun findNodeByText(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val nText = node.text?.toString()?.lowercase() ?: ""
        val nDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (nText.contains(lower) || nDesc.contains(lower)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun loadSavedMacros() {
        val json = prefs?.getString(KEY_MACROS, null) ?: return
        try {
            val type = object : TypeToken<List<SavedMacro>>() {}.type
            val list: List<SavedMacro> = gson.fromJson(json, type)
            _savedMacrosFlow.value = list ?: emptyList()
        } catch (e: Exception) {
            _savedMacrosFlow.value = emptyList()
        }
    }

    private fun persistMacros() {
        val json = gson.toJson(_savedMacrosFlow.value)
        prefs?.edit()?.putString(KEY_MACROS, json)?.apply()
    }
}
