package com.arora.assistant.core.agent

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.ai.GroqClient
import com.arora.assistant.core.service.ServiceStateManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class UiElementDescriptor(
    val id: String,
    val text: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean
)

data class AgentActionResponse(
    @SerializedName("action") val action: String = "DONE",
    @SerializedName("target_text") val targetText: String? = null,
    @SerializedName("target_hint") val targetHint: String? = null,
    @SerializedName("input_value") val inputValue: String? = null,
    @SerializedName("reasoning") val reasoning: String? = null,
    @SerializedName("confidence") val confidence: Float = 1.0f
)

object UiActionExecutor {

    private val gson = Gson()

    /**
     * Executes instant system intents and app launches without cloud AI delay (<20ms turnaround).
     * Returns true if handled instantly.
     */
    suspend fun tryExecuteInstantCommand(context: Context, rawGoal: String): String? {
        val g = rawGoal.trim().lowercase()

        // 1. Instant System Settings
        when {
            g == "open wifi" || g == "wifi" || g == "wi-fi" || g.contains("wifi") || g.contains("wi-fi") -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    return "Opened Wi-Fi Settings"
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    return "Opened Settings"
                }
            }
            g == "open bluetooth" || g == "bluetooth" || g.contains("bluetooth") -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    return "Opened Bluetooth Settings"
                } catch (e: Exception) {
                    return null
                }
            }
            g == "open settings" || g == "settings" -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return "Opened Settings"
            }
            g.contains("display") || g.contains("brightness") -> {
                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return "Opened Display Settings"
            }
            g.contains("sound") || g.contains("volume") || g.contains("audio") -> {
                context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return "Opened Sound Settings"
            }
            g.contains("battery") -> {
                context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return "Opened Battery Info"
            }
            g.contains("app settings") || g == "apps" -> {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return "Opened Installed Apps"
            }
            g.contains("screenshot") -> {
                val ok = ServiceStateManager.takeScreenshot() != null
                return if (ok) "Captured Screenshot" else "Screenshot action triggered"
            }
            g == "lock" || g.contains("lock screen") -> {
                ServiceStateManager.performLockScreen()
                return "Screen Locked"
            }
            g.contains("split screen") -> {
                ServiceStateManager.performSplitScreen()
                return "Toggled Split Screen"
            }
            g.contains("notification") -> {
                ServiceStateManager.performNotifications()
                return "Pulled Notification Shade"
            }
            g.contains("quick settings") -> {
                ServiceStateManager.performQuickSettings()
                return "Pulled Quick Settings"
            }
            g == "back" -> {
                ServiceStateManager.performBack()
                return "Navigated Back"
            }
            g == "home" -> {
                ServiceStateManager.performHome()
                return "Navigated Home"
            }
            g == "recents" -> {
                ServiceStateManager.performRecents()
                return "Opened Recent Apps"
            }
        }

        // 2. Direct App Launch: "open [app name]"
        if (g.startsWith("open ") || g.startsWith("launch ")) {
            val appQuery = g.removePrefix("open ").removePrefix("launch ").trim()
            val launched = launchAppByName(context, appQuery)
            if (launched != null) {
                return "Launched $launched"
            }
        }

        // 3. Direct Screen Click: "click [button text]" or "tap [button text]"
        if (g.startsWith("click ") || g.startsWith("tap ")) {
            val targetText = g.removePrefix("click ").removePrefix("tap ").trim()
            val root = ServiceStateManager.getActiveRootNode()
            if (root != null) {
                val node = findNodeByText(root, targetText)
                if (node != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        ServiceStateManager.clickAtCoordinates(rect.exactCenterX(), rect.exactCenterY())
                    } else {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    return "Clicked '$targetText' on screen"
                }
            }
        }

        return null
    }

    private fun launchAppByName(context: Context, appName: String): String? {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedApps = pm.queryIntentActivities(mainIntent, 0)

        // Common package mappings
        val commonPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "camera" to "com.google.android.GoogleCamera",
            "maps" to "com.google.android.apps.maps",
            "play store" to "com.android.vending",
            "photos" to "com.google.android.apps.photos",
            "whatsapp" to "com.whatsapp",
            "telegram" to "org.telegram.messenger",
            "spotify" to "com.spotify.music",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar"
        )

        // Try direct mapped package
        commonPackages[appName.lowercase()]?.let { pkg ->
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                return appName.replaceFirstChar { it.uppercase() }
            }
        }

        // Search through all installed apps
        val match = resolvedApps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase()
            label.contains(appName.lowercase()) || appName.lowercase().contains(label)
        }

        if (match != null) {
            val pkg = match.activityInfo.packageName
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                return match.loadLabel(pm).toString()
            }
        }

        return null
    }

    suspend fun executeGoalOnScreen(
        context: Context,
        geminiClient: GeminiClient? = null,
        groqClient: GroqClient? = null,
        goal: String,
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.Main) {
        onProgress("Checking instant system actions...")

        // Step 1: Check instant command
        val instantResult = tryExecuteInstantCommand(context, goal)
        if (instantResult != null) {
            return@withContext Result.success("⚡ $instantResult")
        }

        // Step 2: If accessibility root node is available, perform direct screen OCR / node search
        val rootNode = ServiceStateManager.getActiveRootNode()
        if (rootNode == null) {
            return@withContext Result.failure(Exception("Accessibility Service is not enabled or screen is inaccessible."))
        }

        // Step 3: Check AI client availability
        if (groqClient == null && geminiClient == null) {
            return@withContext Result.failure(Exception("Command not recognized as instant action. Configure Groq or Gemini API key for complex UI planning."))
        }

        onProgress("Analyzing UI layout and nodes with AI...")
        val elements = mutableListOf<UiElementDescriptor>()
        extractInteractiveElements(rootNode, elements)

        val elementsJson = gson.toJson(elements.take(60))
        val prompt = """You are an Android UI automation agent. Read the current screen node tree and output a single JSON action for goal: "$goal".
Schema: {"action": "CLICK"|"INPUT"|"SCROLL_DOWN"|"BACK"|"DONE", "target_text": "text", "input_value": "val", "reasoning": "why"}
Screen nodes: $elementsJson"""

        var responseText: String? = null

        // 1. Try Groq LPU first
        if (groqClient != null) {
            val groqRes = groqClient.generateContent(prompt)
            if (groqRes.isSuccess && !groqRes.getOrNull().isNullOrBlank()) {
                responseText = groqRes.getOrNull()?.trim()
            }
        }

        // 2. Fallback to Gemini
        if (responseText == null && geminiClient != null) {
            val result = geminiClient.generateContent(prompt)
            if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            responseText = result.getOrNull()?.trim()
        }

        if (responseText.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Empty response from AI Agent planner."))
        }

        val jsonCleaned = responseText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        try {
            val action = gson.fromJson(jsonCleaned, AgentActionResponse::class.java)
            executeSingleAction(rootNode, action)
            delay(300)
            Result.success("✅ Executed ${action.action} on '${action.targetText ?: "screen"}' (${action.reasoning ?: ""})")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to execute action: ${e.message}"))
        }
    }

    private fun executeSingleAction(rootNode: AccessibilityNodeInfo, action: AgentActionResponse): Boolean {
        return when (action.action.uppercase()) {
            "CLICK" -> {
                val target = findNodeByText(rootNode, action.targetText ?: action.targetHint ?: "")
                if (target != null) {
                    val rect = Rect()
                    target.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        ServiceStateManager.clickAtCoordinates(rect.exactCenterX(), rect.exactCenterY())
                    } else {
                        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                } else false
            }
            "INPUT" -> {
                val target = findNodeByText(rootNode, action.targetText ?: action.targetHint ?: "")
                if (target != null && action.inputValue != null) {
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.inputValue)
                    }
                    target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                } else false
            }
            "SCROLL_DOWN" -> rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "SCROLL_UP" -> rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "BACK" -> ServiceStateManager.performBack()
            else -> false
        }
    }

    private fun extractInteractiveElements(node: AccessibilityNodeInfo?, elements: MutableList<UiElementDescriptor>) {
        if (node == null) return
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        if (node.isClickable || node.isEditable || text.isNotBlank()) {
            elements.add(
                UiElementDescriptor(
                    id = id,
                    text = text.take(60),
                    className = className.substringAfterLast('.'),
                    isClickable = node.isClickable,
                    isEditable = node.isEditable
                )
            )
        }

        for (i in 0 until node.childCount) {
            extractInteractiveElements(node.getChild(i), elements)
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (nodeText.contains(lower) || nodeDesc.contains(lower)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }
}
