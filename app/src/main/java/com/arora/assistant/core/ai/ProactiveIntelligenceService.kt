package com.arora.assistant.core.ai

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.regex.Pattern

enum class ProactiveType {
    DEADLINE,
    BILL_PAYMENT,
    TRACKING_NUMBER,
    ERROR_CODE,
    MEETING_LINK
}

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

    private val DEADLINE_PATTERN = Pattern.compile(
        "(?i)(due\\s+(?:on|by|at|date:)?\\s*([A-Za-z]+ \\d{1,2}|tomorrow|today|\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{1,2}:\\d{2}\\s*(?:AM|PM)?))"
    )
    private val BILL_PATTERN = Pattern.compile(
        "(?i)(total\\s+due|amount\\s+due|balance\\s+due|total)\\s*[:$]?\\s*([$€£¥]?\\s*\\d+(?:\\.\\d{2})?)"
    )
    private val TRACKING_PATTERN = Pattern.compile(
        "(?i)(tracking\\s*(?:number|#)?\\s*[:]?\\s*([A-Z0-9]{10,25}))"
    )

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
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$trackCode"))
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
