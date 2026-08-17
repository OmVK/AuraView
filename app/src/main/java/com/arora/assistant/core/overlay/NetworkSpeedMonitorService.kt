package com.arora.assistant.core.overlay

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftAmber
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs

class NetworkSpeedMonitorService : Service() {

    companion object {
        const val NOTIFICATION_ID = 4001
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var floatingManager: FloatingManager

    private var speedView: View? = null
    private var monitorJob: Job? = null

    private var currentDownloadSpeed by mutableStateOf("0 KB/s")
    private var currentPingMs by mutableIntStateOf(28)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        com.arora.assistant.core.service.ServiceStateManager.setSpeedMonitorActive(true)
        floatingManager = FloatingManager(this)

        val notification = NotificationCompat.Builder(this, AroraApplication.CHANNEL_ID)
            .setContentTitle("AuraView Speedometer")
            .setContentText("Monitoring network speed & ping")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        initFloatingSpeedometer()
        startNetworkMonitoring()
    }

    private fun initFloatingSpeedometer() {
        val displayMetrics = resources.displayMetrics
        val pillWidth = (140 * displayMetrics.density).toInt()
        val pillHeight = (32 * displayMetrics.density).toInt()

        val params = FloatingManager.createLayoutParams(
            width = pillWidth,
            height = pillHeight,
            x = (displayMetrics.widthPixels / 2) - (pillWidth / 2),
            y = (36 * displayMetrics.density).toInt()
        )

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        val view = floatingManager.createFloatingComposeView(params) {
            FloatingSpeedometerPill(
                downloadSpeed = currentDownloadSpeed,
                pingMs = currentPingMs,
                onClose = { stopSelf() }
            )
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    if (abs(deltaX) > 4 || abs(deltaY) > 4) {
                        isDragging = true
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        floatingManager.updateViewLayout(view, params)
                    }
                    true
                }
                else -> false
            }
        }

        speedView = view
    }

    private fun startNetworkMonitoring() {
        monitorJob = serviceScope.launch(Dispatchers.IO) {
            var lastRxBytes = TrafficStats.getTotalRxBytes()

            while (isActive) {
                delay(1000)
                val currentRxBytes = TrafficStats.getTotalRxBytes()
                val speedBytes = (currentRxBytes - lastRxBytes).coerceAtLeast(0)
                lastRxBytes = currentRxBytes

                val speedFormatted = if (speedBytes > 1024 * 1024) {
                    String.format("%.1f MB/s", speedBytes / (1024f * 1024f))
                } else {
                    String.format("%.0f KB/s", speedBytes / 1024f)
                }

                val ping = try {
                    val start = System.currentTimeMillis()
                    val socket = Socket()
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 350)
                    socket.close()
                    (System.currentTimeMillis() - start).toInt()
                } catch (e: Exception) {
                    35
                }

                launch(Dispatchers.Main) {
                    currentDownloadSpeed = speedFormatted
                    currentPingMs = ping
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        speedView?.let { floatingManager.removeView(it) }
        com.arora.assistant.core.service.ServiceStateManager.setSpeedMonitorActive(false)
        isRunning = false
    }
}

@Composable
fun FloatingSpeedometerPill(
    downloadSpeed: String,
    pingMs: Int,
    onClose: () -> Unit
) {
    val pingColor = when {
        pingMs < 45 -> SageMint
        pingMs < 100 -> SoftAmber
        else -> PastelRose
    }

    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 30.dp)
            .shadow(12.dp, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        SoftSurfaceElevated.copy(alpha = 0.96f),
                        SoftDarkBg.copy(alpha = 0.96f)
                    )
                )
            )
            .border(1.dp, SoftCardBorder, RoundedCornerShape(15.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("↓ $downloadSpeed", color = TextPureWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(pingColor))
            Spacer(modifier = Modifier.width(3.dp))
            Text("${pingMs}ms", color = pingColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
