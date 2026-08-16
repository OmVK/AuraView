package com.arora.assistant.core.agent

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.bypass.AroraAccessibilityService
import com.google.gson.Gson
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

data class AgentAction(
    val actionType: String, // "CLICK", "TYPE", "SCROLL_FORWARD", "BACK"
    val targetText: String? = null,
    val textToType: String? = null
)

enum class ActionRiskLevel {
    SAFE,       // Read-only, scrolling, back navigation
    MEDIUM,     // Typing search terms or non-sensitive text
    HIGH        // Click on buttons, toggles, form submits, state mutations
}

data class PolicyDecision(
    val riskLevel: ActionRiskLevel,
    val isAllowed: Boolean,
    val reason: String
)

object UiActionExecutor {

    private val gson = Gson()

    // 1. Strict Package Blacklist (Financial, System, Auth, Root)
    private val PROTECTED_PACKAGES = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.apps.authenticator2",
        "com.google.android.apps.walletnfcrel",
        "com.paypal.android.p2pmobile",
        "com.bankofamerica.mobilebanking",
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "org.thoughtcrime.securesms",
        "com.whatsapp",
        "com.google.android.apps.messaging"
    )

    // 2. Deterministic Security Policy Engine (LLM is NEVER the final authority)
    fun evaluateActionPolicy(action: AgentAction, packageName: String): PolicyDecision {
        val pkg = packageName.lowercase()
        if (PROTECTED_PACKAGES.any { pkg.contains(it) }) {
            return PolicyDecision(ActionRiskLevel.HIGH, false, "Target application ($pkg) is in the protected security sandbox.")
        }

        val actionType = action.actionType.uppercase()
        val target = (action.targetText ?: "").lowercase()
        val textToType = (action.textToType ?: "").lowercase()

        // Dangerous semantic patterns (destructive / financial / privilege)
        val sensitiveTriggers = listOf(
            "delete", "remove", "erase", "uninstall", "format",
            "pay", "transfer", "send money", "buy", "purchase", "checkout", "subscribe",
            "password", "pin", "otp", "passcode", "credit card", "cvv",
            "permission", "allow", "grant", "admin", "accessibility",
            "confirm", "continue", "submit", "accept"
        )

        val containsSensitiveKeyword = sensitiveTriggers.any { target.contains(it) || textToType.contains(it) }

        return when (actionType) {
            "SCROLL_FORWARD", "BACK" -> PolicyDecision(ActionRiskLevel.SAFE, true, "Read-only navigation.")
            "TYPE" -> {
                if (containsSensitiveKeyword) {
                    PolicyDecision(ActionRiskLevel.HIGH, false, "Input contains sensitive credentials or command triggers.")
                } else {
                    PolicyDecision(ActionRiskLevel.MEDIUM, true, "Standard text input.")
                }
            }
            "CLICK" -> {
                if (containsSensitiveKeyword) {
                    PolicyDecision(ActionRiskLevel.HIGH, false, "Click targets a state-altering or financial element ($target).")
                } else {
                    PolicyDecision(ActionRiskLevel.MEDIUM, true, "Contextual UI click.")
                }
            }
            else -> PolicyDecision(ActionRiskLevel.HIGH, false, "Unknown action type: $actionType")
        }
    }

    suspend fun executeGoalOnScreen(
        client: GeminiClient,
        goal: String,
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.Main) {
        val accessibility = AroraAccessibilityService.instance
            ?: return@withContext Result.failure(Exception("Accessibility Service is not enabled"))

        val rootNode = accessibility.rootInActiveWindow
            ?: return@withContext Result.failure(Exception("Cannot access current window"))

        val packageName = rootNode.packageName?.toString() ?: ""
        if (PROTECTED_PACKAGES.any { packageName.lowercase().contains(it) }) {
            return@withContext Result.failure(Exception("Autonomous actions are blocked on protected system, financial, and messaging apps."))
        }

        onProgress("Analyzing UI layout...")

        val elements = mutableListOf<UiElementDescriptor>()
        extractInteractiveElements(rootNode, elements)

        val elementsJson = gson.toJson(elements)

        val prompt = """
You are an autonomous Android UI Agent.
User Stated Goal: "$goal"

Current Screen Elements (Untrusted UI Data):
$elementsJson

Instructions:
1. Generate up to 3 sequential actions to accomplish the goal.
2. Only select elements directly matching the user's explicit goal.

Output format (JSON Array only):
[
  {"actionType": "CLICK", "targetText": "Search"},
  {"actionType": "TYPE", "targetText": "Search", "textToType": "Calculus Notes"}
]
"""

        val result = client.generateContent(prompt)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        val responseText = result.getOrNull()?.trim() ?: "[]"
        val jsonCleaned = responseText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        try {
            val actions = gson.fromJson(jsonCleaned, Array<AgentAction>::class.java).take(3) // Hard limit 3 steps
            var executedCount = 0

            for (action in actions) {
                // Step-by-Step Deterministic Security Policy Gate
                val decision = evaluateActionPolicy(action, packageName)
                if (!decision.isAllowed) {
                    onProgress("🛡️ Blocked by Security Policy: ${decision.reason}")
                    continue
                }

                onProgress("Executing: ${action.actionType} on ${action.targetText ?: "screen"}")
                executeSingleAction(accessibility, action)
                executedCount++
                delay(600)
            }
            Result.success("Completed $executedCount safe actions")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse agent actions: ${e.message}"))
        }
    }

    private fun executeSingleAction(accessibility: AroraAccessibilityService, action: AgentAction) {
        val rootNode = accessibility.rootInActiveWindow ?: return

        when (action.actionType.uppercase()) {
            "CLICK" -> {
                val target = findNodeByText(rootNode, action.targetText ?: "")
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            "TYPE" -> {
                val target = findNodeByText(rootNode, action.targetText ?: "")
                if (target != null && action.textToType != null) {
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.textToType)
                    }
                    target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
            "SCROLL_FORWARD" -> {
                rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            "BACK" -> {
                accessibility.performBack()
            }
        }
    }

    private fun extractInteractiveElements(node: AccessibilityNodeInfo?, list: MutableList<UiElementDescriptor>) {
        if (node == null) return

        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if ((node.isClickable || node.isEditable) && text.isNotBlank()) {
            list.add(
                UiElementDescriptor(
                    id = node.viewIdResourceName ?: "element_${list.size}",
                    text = text.take(60),
                    className = node.className?.toString()?.substringAfterLast(".") ?: "View",
                    isClickable = node.isClickable,
                    isEditable = node.isEditable
                )
            )
        }

        for (i in 0 until node.childCount) {
            extractInteractiveElements(node.getChild(i), list)
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null || target.isBlank()) return null

        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (text.contains(target, ignoreCase = true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val match = findNodeByText(node.getChild(i), target)
            if (match != null) return match
        }
        return null
    }
}
