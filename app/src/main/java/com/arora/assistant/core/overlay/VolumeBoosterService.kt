package com.arora.assistant.core.overlay

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.arora.assistant.AroraApplication
import com.arora.assistant.R
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftAmber
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite

class VolumeBoosterService : Service() {

    companion object {
        const val NOTIFICATION_ID = 3001
        var isRunning = false
            private set

        private var loudnessEnhancer: LoudnessEnhancer? = null
        var currentBoostLevel = 100 // 100% is normal, up to 200% (+20dB)
            private set

        fun setBoost(level: Int) {
            currentBoostLevel = level
            try {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = LoudnessEnhancer(0) // Apply to audio session 0 (global)
                }
                if (level > 100) {
                    val gainMb = ((level - 100) * 20).coerceIn(0, 2000) // up to +2000 mB (+20 dB)
                    loudnessEnhancer?.setTargetGain(gainMb)
                    loudnessEnhancer?.enabled = true
                } else {
                    loudnessEnhancer?.enabled = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val notification = NotificationCompat.Builder(this, AroraApplication.CHANNEL_ID)
            .setContentTitle("AuraView Volume & Dimmer")
            .setContentText("Hardware booster active")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loudnessEnhancer?.enabled = false
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        isRunning = false
    }
}

@Composable
fun VolumeAndDimmerControlDialog(
    initialBoost: Int = VolumeBoosterService.currentBoostLevel,
    initialDimPercent: Float = 0f,
    onBoostChange: (Int) -> Unit,
    onDimChange: (Float, Boolean) -> Unit,
    onClose: () -> Unit
) {
    var boostPercent by remember { mutableFloatStateOf(initialBoost.toFloat()) }
    var dimLevel by remember { mutableFloatStateOf(initialDimPercent) }
    var isBlueLightFilterOn by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(340.dp)
                .shadow(24.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = SoftAmber.copy(alpha = 0.4f))
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SoftSurfaceElevated.copy(alpha = 0.96f),
                            SoftDarkBg.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(1.dp, SoftCardBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SoftAmber.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = SoftAmber, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Sound & Screen", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Boost volume & dim below 0%", color = TextMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: 200% Volume Booster
                Text(
                    text = "🔊 200% VOLUME BOOSTER: ${boostPercent.toInt()}%",
                    color = if (boostPercent > 100f) PastelRose else SoftAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Slider(
                    value = boostPercent,
                    onValueChange = {
                        boostPercent = it
                        onBoostChange(it.toInt())
                        VolumeBoosterService.setBoost(it.toInt())
                    },
                    valueRange = 100f..200f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = if (boostPercent > 100f) PastelRose else SoftAmber,
                        activeTrackColor = if (boostPercent > 100f) PastelRose else SoftAmber,
                        inactiveTrackColor = SoftSurface
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Section 2: Sub-0% Ultra Night Dimmer
                Text(
                    text = "🌙 ULTRA-DIM NIGHT FILTER: ${(dimLevel * 100).toInt()}%",
                    color = SkyOpal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Slider(
                    value = dimLevel,
                    onValueChange = {
                        dimLevel = it
                        onDimChange(it, isBlueLightFilterOn)
                    },
                    valueRange = 0f..0.85f,
                    colors = SliderDefaults.colors(
                        thumbColor = SkyOpal,
                        activeTrackColor = SkyOpal,
                        inactiveTrackColor = SoftSurface
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Blue Light Filter Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftSurface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Nightlight, null, tint = SoftAmber, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Warm Blue-Light Filter", color = TextOffWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = isBlueLightFilterOn,
                        onCheckedChange = {
                            isBlueLightFilterOn = it
                            onDimChange(dimLevel, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SoftAmber)
                    )
                }
            }
        }
    }
}
