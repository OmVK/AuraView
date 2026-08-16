package com.arora.assistant.core.ai

import android.content.Context
import android.content.Intent
import android.net.Uri

data class ClipboardSmartAction(
    val title: String,
    val description: String,
    val actionIntent: Intent
)

object SmartClipboardAnalyzer {

    fun analyzeClip(context: Context, text: String): ClipboardSmartAction? {
        val trimmed = text.trim()

        // 1. Phone number -> WhatsApp / Phone Dialer
        val phonePattern = "^[+]?[0-9]{10,15}$".toRegex()
        if (phonePattern.matches(trimmed.replace("[\\s-]".toRegex(), ""))) {
            val cleanNumber = trimmed.replace("[\\s-]".toRegex(), "")
            return ClipboardSmartAction(
                title = "Open WhatsApp / Dialer",
                description = "Chat with $cleanNumber",
                actionIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        // 2. Physical Address -> Google Maps
        if (trimmed.contains("Street", ignoreCase = true) ||
            trimmed.contains("Ave", ignoreCase = true) ||
            trimmed.contains("Road", ignoreCase = true) ||
            trimmed.contains("Boulevard", ignoreCase = true)
        ) {
            return ClipboardSmartAction(
                title = "Navigate with Maps",
                description = "Search location in Maps",
                actionIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(trimmed)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        // 3. URLs -> Open in Browser
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return ClipboardSmartAction(
                title = "Open Link",
                description = "Open ${Uri.parse(trimmed).host}",
                actionIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        return null
    }
}
