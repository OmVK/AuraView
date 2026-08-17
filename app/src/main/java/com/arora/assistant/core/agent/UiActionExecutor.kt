package com.arora.assistant.core.agent

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.bypass.AroraAccessibilityService
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
    @SerializedName("action") val action: String = "DONE", // CLICK, INPUT, SCROLL_DOWN, SCROLL_UP, SWIPE, BACK, WAIT, DONE, ABORT
    @SerializedName("target_text") val targetText: String? = null,
    @SerializedName("target_hint") val targetHint: String? = null,
    @SerializedName("input_value") val inputValue: String? = null,
    @SerializedName("reasoning") val reasoning: String? = null,
    @SerializedName("confidence") val confidence: Float = 1.0f
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
    val PROTECTED_PACKAGES = setOf(
        "wallet", "pay", "bank", "gpay", "nfc", "finance", "money",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.google.android.apps.authenticator2",
        "com.google.android.apps.walletnfcrel",
        "com.paypal.android.p2pmobile",
        "com.bankofamerica.mobilebanking",
        "com.chase.sig.android",
        "com.wf.wellsfargomobile",
        "org.thoughtcrime.securesms"
    )

    const val AGENT_ACTION_PROMPT = """You are an Android UI automation agent. You control an Android phone by reading 
the accessibility node tree and executing one action at a time.

You will receive:
1. The current screen's accessibility node tree as JSON
2. The user's goal
3. The action history so far

Output ONLY a single valid JSON object. No explanation, no markdown, no prose.
Any non-JSON output will crash the system.

JSON schema:
{
  "action": "CLICK" | "INPUT" | "SCROLL_DOWN" | "SCROLL_UP" | "SWIPE" | "BACK" | "WAIT" | "DONE" | "ABORT",
  "target_text": "exact visible text of the UI node to interact with",
  "target_hint": "content-description or resource-id if text is empty",
  "input_value": "text to type (INPUT action only)",
  "reasoning": "one sentence why this action",
  "confidence": 0.0-1.0
}

SAFETY RULES — violating these triggers ABORT:
- ABORT if current app package contains: wallet, pay, bank, gpay, nfc, finance, money
- ABORT if any visible node has inputType containing password or has isPassword=true
- ABORT if screen contains: "Enter PIN", "Enter password", "Biometric", "Confirm payment"
- ABORT if goal requires accessing contacts, call logs, or SMS content
- Return DONE when the user's goal is fully achieved
- Return BACK if you are stuck or the screen is unexpected
- Return WAIT if an animation or loading indicator is visible
- Confidence below 0.4 -> return BACK instead of guessing"""

    // 2. Deterministic Security Policy Engine (LLM is NEVER the final authority)
    fun evaluateActionPolicy(action: AgentActionResponse, packageName: String): PolicyDecision {
        val pkg = packageName.lowercase()
        if (PROTECTED_PACKAGES.any { pkg.contains(it) }) {
            return PolicyDecision(ActionRiskLevel.HIGH, false, "Target application ($pkg) is in the protected security sandbox.")
        }

        val actionType = action.action.uppercase()
        val target = (action.targetText ?: "").lowercase()
        val inputValue = (action.inputValue ?: "").lowercase()

        // Dangerous semantic patterns (destructive / financial / privilege)
        val sensitiveTriggers = listOf(
            "delete", "remove", "erase", "uninstall", "format",
            "pay", "transfer", "send money", "buy", "purchase", "checkout", "subscribe",
            "password", "pin", "otp", "passcode", "credit card", "cvv",
            "permission", "allow", "grant", "admin", "accessibility",
            "confirm", "continue", "submit", "accept"
        )

        val containsSensitiveKeyword = sensitiveTriggers.any { target.contains(it) || inputValue.contains(it) }

        return when (actionType) {
            "SCROLL_DOWN", "SCROLL_UP", "BACK", "WAIT", "DONE" -> PolicyDecision(ActionRiskLevel.SAFE, true, "Safe navigation action.")
            "INPUT" -> {
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
            "ABORT" -> PolicyDecision(ActionRiskLevel.SAFE, true, "User or safety abort initiated.")
            else -> PolicyDecision(ActionRiskLevel.HIGH, false, "Unknown action type: $actionType")
        }
    }

    suspend fun executeGoalOnScreen(
        client: GeminiClient,
        goal: String,
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.Main) {
        val rootNode = ServiceStateManager.getActiveRootNode()
            ?: return@withContext Result.failure(Exception("Accessibility Service is not enabled or active window is inaccessible"))

        val packageName = rootNode.packageName?.toString() ?: ""
        if (PROTECTED_PACKAGES.any { packageName.lowercase().contains(it) }) {
            return@withContext Result.failure(Exception("Autonomous actions are blocked on protected system, financial, and messaging apps."))
        }

        onProgress("Analyzing UI layout and nodes...")

        val elements = mutableListOf<UiElementDescriptor>()
        extractInteractiveElements(rootNode, elements)

        val elementsJson = gson.toJson(elements)

        val prompt = """$AGENT_ACTION_PROMPT

USER GOAL: $goal

ACTIONS TAKEN SO FAR:
[]

CURRENT SCREEN NODE TREE:
$elementsJson"""

        val result = client.generateContent(prompt)
        if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)

        val responseText = result.getOrNull()?.trim() ?: "{}"
        val jsonCleaned = responseText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        try {
            val action = gson.fromJson(jsonCleaned, AgentActionResponse::class.java)

            if (action.action.uppercase() == "ABORT") {
                return@withContext Result.success("🛑 Agent aborted: ${action.reasoning ?: "Safety rule triggered"}")
            }

            if (action.action.uppercase() == "DONE") {
                return@withContext Result.success("✅ Goal accomplished: ${action.reasoning ?: "Completed"}")
            }

            if (action.confidence < 0.4f) {
                onProgress("Low confidence (${action.confidence}). Performing safe BACK navigation.")
                ServiceStateManager.performBack()
                return@withContext Result.success("Navigated BACK due to low confidence")
            }

            // Step-by-Step Deterministic Security Policy Gate
            val decision = evaluateActionPolicy(action, packageName)
            if (!decision.isAllowed) {
                return@withContext Result.failure(Exception("🛡️ Blocked by Security Policy: ${decision.reason}"))
            }

            onProgress("Executing: ${action.action} on ${action.targetText ?: action.targetHint ?: "screen"} (${action.reasoning ?: ""})")
            executeSingleAction(rootNode, action)
            delay(500)
            Result.success("✅ Executed ${action.action} (${action.reasoning ?: ""})")
        } catch (e: Exception) {
            Result.failure(Exception("Failed to parse agent action JSON: ${e.message}"))
        }
    }

    private fun executeSingleAction(rootNode: AccessibilityNodeInfo, action: AgentActionResponse) {
        when (action.action.uppercase()) {
            "CLICK" -> {
                val target = findNodeByText(rootNode, action.targetText ?: action.targetHint ?: "")
                target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            "INPUT" -> {
                val target = findNodeByText(rootNode, action.targetText ?: action.targetHint ?: "")
                if (target != null && action.inputValue != null) {
                    val arguments = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.inputValue)
                    }
                    target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
            "SCROLL_DOWN" -> rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "SCROLL_UP" -> rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "BACK" -> ServiceStateManager.performBack()
            "WAIT" -> Thread.sleep(800)
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
                    text = text,
                    className = className,
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
        val nodeId = node.viewIdResourceName?.lowercase() ?: ""

        if (nodeText.contains(lower) || nodeDesc.contains(lower) || nodeId.contains(lower)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }
}
