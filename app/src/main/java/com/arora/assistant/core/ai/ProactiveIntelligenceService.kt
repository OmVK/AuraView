package com.arora.assistant.core.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

enum class ProactiveType {
    DEADLINE,
    BILL_PAYMENT,
    TRACKING_NUMBER,
    ERROR_CODE,
    MEETING_LINK,
    OTP,
    PRICE_DROP
}

data class ProactiveEnrichmentResponse(
    @SerializedName("type") val type: String,
    @SerializedName("extracted_value") val extractedValue: String,
    @SerializedName("suggested_action") val suggestedAction: String,
    @SerializedName("action_label") val actionLabel: String,
    @SerializedName("action_intent") val actionIntent: String // COPY, OPEN_MAPS, OPEN_BROWSER, OPEN_DIALER, OPEN_CALENDAR, DISMISS
)

data class ProactiveAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ProactiveType,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val onAction: (Context) -> Unit
)

object ProactiveIntelligenceService {

    private val _alerts = MutableSharedFlow<ProactiveAlert>(extraBufferCapacity = 5)
    val alerts: SharedFlow<ProactiveAlert> = _alerts.asSharedFlow()
    private val gson = Gson()

    const val PROACTIVE_ENRICHMENT_PROMPT = """You are a Proactive Android Screen Intelligence Engine.
The user's screen shows an actionable entity (OTP, TRACKING_NUMBER, DEADLINE, PRICE_DROP, ERROR_CODE).

Output JSON only:
{
  "type": "OTP | TRACKING_NUMBER | DEADLINE | PRICE_DROP | ERROR_CODE",
  "extracted_value": "[the key value]",
  "suggested_action": "[what the user probably wants to do]",
  "action_label": "[short button label, max 3 words]",
  "action_intent": "COPY | OPEN_MAPS | OPEN_BROWSER | OPEN_DIALER | OPEN_CALENDAR | DISMISS"
}"""

    private val DEADLINE_PATTERN = Pattern.compile(
        "(?i)(due\\s+(?:on|by|at|date:)?\\s*([A-Za-z]+ \\d{1,2}|tomorrow|today|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}:\\d{2}\\s*(?:AM|PM)?))"
    )
    private val BILL_PATTERN = Pattern.compile(
        "(?i)(total\\s+due|amount\\s+due|balance\\s+due|total)\\s*[:$]?\\s*([$€£¥]?\\s*\\d+(?:\\.\\d{2})?)"
    )
    private val TRACKING_PATTERN = Pattern.compile(
        "(?i)(tracking\\s*(?:number|#)?\\s*[:]?\\s*([A-Z0-9]{10,25}))"
    )

    suspend fun enrichDetectedEntity(
        geminiClient: GeminiClient? = null,
        groqClient: GroqClient? = null,
        detectedType: String,
        screenText: String
    ): Result<ProactiveEnrichmentResponse> = withContext(Dispatchers.IO) {
        val prompt = """$PROACTIVE_ENRICHMENT_PROMPT

The user's screen shows a $detectedType.
Screen text: "$screenText""""

        var cleanJson: String? = null

        if (groqClient != null) {
            val groqRes = groqClient.generateContent(prompt)
            if (groqRes.isSuccess && !groqRes.getOrNull().isNullOrBlank()) {
                cleanJson = groqRes.getOrNull()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim()
            }
        }

        if (cleanJson == null && geminiClient != null) {
            val result = geminiClient.generateContent(prompt)
            if (result.isFailure) return@withContext Result.failure(result.exceptionOrNull()!!)
            cleanJson = result.getOrNull()?.removePrefix("```json")?.removePrefix("```")?.removeSuffix("```")?.trim()
        }

        if (cleanJson.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No AI client configured or empty response"))
        }

        try {
            val response = gson.fromJson(cleanJson, ProactiveEnrichmentResponse::class.java)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun inspectScreenContent(text: String, packageName: String) {
        if (text.length < 5) return

        // 1. Deadline Detection
        val deadlineMatcher = DEADLINE_PATTERN.matcher(text)
        if (deadlineMatcher.find()) {
            val detectedTime = deadlineMatcher.group(2) ?: "Upcoming Deadline"
            val alert = ProactiveAlert(
                type = ProactiveType.DEADLINE,
                title = "Deadline Detected",
                detail = "Found '$detectedTime' on screen.",
                actionLabel = "Add to Calendar",
                onAction = { context ->
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, "Assignment / Deadline")
                        .putExtra(CalendarContract.Events.DESCRIPTION, "Auto-detected by AuraView: $text")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            )
            _alerts.tryEmit(alert)
            return
        }

        // 2. Tracking Number
        val trackMatcher = TRACKING_PATTERN.matcher(text)
        if (trackMatcher.find()) {
            val trackCode = trackMatcher.group(2) ?: ""
            val alert = ProactiveAlert(
                type = ProactiveType.TRACKING_NUMBER,
                title = "Package Tracking Code",
                detail = "Tracking: $trackCode",
                actionLabel = "Track Package",
                onAction = { context ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$trackCode"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            )
            _alerts.tryEmit(alert)
            return
        }

        // 3. Bill Amount
        val billMatcher = BILL_PATTERN.matcher(text)
        if (billMatcher.find()) {
            val amount = billMatcher.group(2) ?: ""
            val alert = ProactiveAlert(
                type = ProactiveType.BILL_PAYMENT,
                title = "Payment Due Detected",
                detail = "Amount: $amount",
                actionLabel = "Save Reminder",
                onAction = { context ->
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, "Bill Due: $amount")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            )
            _alerts.tryEmit(alert)
        }
    }
}
