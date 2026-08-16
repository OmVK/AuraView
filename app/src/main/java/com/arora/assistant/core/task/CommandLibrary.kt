package com.arora.assistant.core.task

sealed class AroraCommand(val name: String, val category: String) {
    // Navigation & System
    object Back : AroraCommand("Back", "System")
    object Home : AroraCommand("Home", "System")
    object Recents : AroraCommand("Recents", "System")
    object PullNotification : AroraCommand("Pull Notification", "System")
    object LockScreen : AroraCommand("Lock Screen", "System")
    object ToggleSplitScreen : AroraCommand("Split Screen", "System")
    
    // Snip, OCR & AI
    object SnipScreen : AroraCommand("Snip Screen", "Screen & OCR")
    object ExtractOcrText : AroraCommand("Extract OCR Text", "Screen & OCR")
    data class AskGeminiAi(val prompt: String) : AroraCommand("Ask AI", "AI Intelligence")
    object SolveScreenProblem : AroraCommand("Solve Math/Code", "AI Intelligence")
    object TranslateScreen : AroraCommand("Translate Screen", "AI Intelligence")
    
    // Floating Windows
    data class OpenMiniBrowser(val url: String = "https://google.com") : AroraCommand("Open Floating Browser", "Floating Windows")
    object OpenClipboardStack : AroraCommand("Open Clipboard History", "Floating Windows")
    object OpenFloatingNotes : AroraCommand("Open Floating Notes", "Floating Windows")

    // Automation & Logic
    data class Delay(val millis: Long) : AroraCommand("Delay", "Automation")
    data class LaunchApp(val packageName: String) : AroraCommand("Launch App", "System")
    data class OpenUrl(val url: String) : AroraCommand("Open URL", "System")
    data class CopyToClipboard(val text: String) : AroraCommand("Copy Text", "Clipboard")
}

data class TaskStep(
    val id: String,
    val command: AroraCommand,
    val condition: String? = null
)

data class CustomTask(
    val id: String,
    val title: String,
    val triggerDescription: String,
    val steps: List<TaskStep>
)
