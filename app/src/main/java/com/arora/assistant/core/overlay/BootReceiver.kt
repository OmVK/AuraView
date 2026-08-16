package com.arora.assistant.core.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.arora.assistant.core.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = AppPreferences(context)
            CoroutineScope(Dispatchers.IO).launch {
                val isEnabled = prefs.ballEnabled.first()
                if (isEnabled && Settings.canDrawOverlays(context)) {
                    val serviceIntent = Intent(context, FloatingBallService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                }
            }
        }
    }
}
