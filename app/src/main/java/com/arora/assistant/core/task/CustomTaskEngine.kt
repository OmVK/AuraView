package com.arora.assistant.core.task

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.arora.assistant.core.bypass.AroraAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class CustomTaskEngine(private val context: Context) {

    suspend fun executeTask(task: CustomTask, onProgress: (String) -> Unit = {}) = withContext(Dispatchers.Main) {
        onProgress("Starting: ${task.title}")

        for (step in task.steps) {
            executeCommand(step.command)
        }

        onProgress("Completed: ${task.title}")
    }

    private suspend fun executeCommand(command: AroraCommand) {
        val accessibility = AroraAccessibilityService.instance

        when (command) {
            is AroraCommand.Back -> accessibility?.performBack()
            is AroraCommand.Home -> accessibility?.performHome()
            is AroraCommand.Recents -> accessibility?.performRecents()
            is AroraCommand.PullNotification -> accessibility?.performNotifications()
            is AroraCommand.LockScreen -> accessibility?.performLockScreen()
            is AroraCommand.ToggleSplitScreen -> accessibility?.performSplitScreen()
            
            is AroraCommand.Delay -> delay(command.millis)
            is AroraCommand.LaunchApp -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(command.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            }
            is AroraCommand.OpenUrl -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(command.url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
            is AroraCommand.CopyToClipboard -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Arora", command.text))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Screen, OCR and AI commands handled via FloatingManager orchestrator
            }
        }
    }
}
