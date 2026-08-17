package com.arora.assistant.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arora.assistant.core.ai.GeminiClient
import com.arora.assistant.core.bypass.AroraAccessibilityService
import com.arora.assistant.core.bypass.MediaProjectionService
import com.arora.assistant.core.bypass.OemPermissionHelper
import com.arora.assistant.core.bypass.ShizukuBypassService
import com.arora.assistant.core.data.AppPreferences
import com.arora.assistant.core.overlay.FloatingBallService
import com.arora.assistant.ui.components.GlassCard
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.AroraTheme
import com.arora.assistant.ui.theme.CarbonCardBorder
import com.arora.assistant.ui.theme.CarbonDark
import com.arora.assistant.ui.theme.CarbonElevated
import com.arora.assistant.ui.theme.CarbonSurface
import com.arora.assistant.ui.theme.CyberCyan
import com.arora.assistant.ui.theme.ElectricIndigo
import com.arora.assistant.ui.theme.HyperViolet
import com.arora.assistant.ui.theme.MintEmerald
import com.arora.assistant.ui.theme.RoseCrimson
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import com.arora.assistant.ui.theme.TextSubtle
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appPreferences = AppPreferences(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 101)
        }

        setContent {
            AroraTheme {
                MainDashboardScreen(appPreferences = appPreferences)
            }
        }
    }
}

@Composable
fun MainDashboardScreen(appPreferences: AppPreferences) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val ballEnabled by appPreferences.ballEnabled.collectAsState(initial = true)
    val savedApiKey by appPreferences.geminiApiKey.collectAsState(initial = "")
    var apiKeyInput by remember(savedApiKey) { mutableStateOf(savedApiKey) }

    // StateFlow Service States
    val isAccessibilityActive by com.arora.assistant.core.service.ServiceStateManager.isAccessibilityActive.collectAsState()
    val isScreenSnipReady by com.arora.assistant.core.service.ServiceStateManager.isMediaProjectionActive.collectAsState()

    // Live Auto-Refreshing Permission States on Resume
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryOptimized by remember { mutableStateOf(OemPermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var isShizukuReady by remember { mutableStateOf(ShizukuBypassService.hasShizukuPermission()) }

    // Gemini API Test State
    var isTestingKey by remember { mutableStateOf(false) }
    var testKeyResult by remember { mutableStateOf<String?>(null) }
    var isKeyValid by remember { mutableStateOf<Boolean?>(null) }

    // Step-by-step OEM Guide Dialog
    var showOemGuideDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isBatteryOptimized = OemPermissionHelper.isIgnoringBatteryOptimizations(context)
                isShizukuReady = ShizukuBypassService.hasShizukuPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                putExtra(MediaProjectionService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(MediaProjectionService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, intent)
            Toast.makeText(context, "Screen Snip Service Activated", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = CarbonDark,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(44.dp))

            // Hero Live Preview & Master Activation Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(20.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = HyperViolet)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                CarbonElevated,
                                CarbonSurface
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(HyperViolet.copy(alpha = 0.6f), CarbonCardBorder, CyberCyan.copy(alpha = 0.3f))),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AuraView AI",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPureWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Autonomous Multimodal Copilot",
                                fontSize = 13.sp,
                                color = CyberCyan,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Switch(
                            checked = ballEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    appPreferences.setBallEnabled(enabled)
                                    if (enabled) {
                                        if (!Settings.canDrawOverlays(context)) {
                                            Toast.makeText(context, "Grant Overlay Permission first", Toast.LENGTH_LONG).show()
                                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                        } else {
                                            ContextCompat.startForegroundService(context, Intent(context, FloatingBallService::class.java))
                                        }
                                    } else {
                                        context.stopService(Intent(context, FloatingBallService::class.java))
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPureWhite,
                                checkedTrackColor = HyperViolet
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Live Interactive Capsule Visual Demo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CarbonDark.copy(alpha = 0.7f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CarbonElevated)
                                .border(1.dp, HyperViolet, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(HyperViolet))
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.size(width = 10.dp, height = 3.dp).clip(RoundedCornerShape(1.dp)).background(CyberCyan))
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic Iris Capsule Ready", color = TextPureWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tap orb on screen to launch tools", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Detected Device & OEM Diagnostic Banner
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HyperViolet.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = HyperViolet, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Device Profile", color = TextMuted, fontSize = 11.sp)
                        Text(OemPermissionHelper.getBrandDisplayName(), color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    IconButton(
                        onClick = { showOemGuideDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CarbonElevated)
                    ) {
                        Icon(Icons.Default.Info, "Setup Guide", tint = CyberCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gemini Multimodal Engine Card
            GlassCard(modifier = Modifier.fillMaxWidth(), borderGlow = true, cornerRadius = 18.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = HyperViolet, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini 1.5 Flash Vision", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))

                        // Get Free Key Direct Link
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(HyperViolet.copy(alpha = 0.15f))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Get Free Key", color = HyperViolet, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInNew, null, tint = HyperViolet, modifier = Modifier.size(12.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Powers Circle to Search reasoning, step-by-step LaTeX math proofs, and code debugging.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            testKeyResult = null
                            isKeyValid = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Paste AI Studio Gemini Key...", fontSize = 13.sp, color = TextSubtle) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = CarbonElevated,
                            unfocusedContainerColor = CarbonElevated,
                            focusedTextColor = TextPureWhite,
                            unfocusedTextColor = TextPureWhite,
                            focusedIndicatorColor = HyperViolet,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        NeonButton(
                            text = "Save Key",
                            onClick = {
                                scope.launch {
                                    appPreferences.setGeminiApiKey(apiKeyInput.trim())
                                    Toast.makeText(context, "API Key Saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            icon = Icons.Default.Key,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        NeonButton(
                            text = if (isTestingKey) "Testing..." else "Test Key",
                            onClick = {
                                if (apiKeyInput.isBlank()) {
                                    Toast.makeText(context, "Please paste a key first", Toast.LENGTH_SHORT).show()
                                    return@NeonButton
                                }
                                scope.launch {
                                    isTestingKey = true
                                    testKeyResult = null
                                    val client = GeminiClient(apiKeyInput.trim())
                                    val result = client.generateContent("Hello, verify key.")
                                    isTestingKey = false
                                    if (result.isSuccess) {
                                        isKeyValid = true
                                        testKeyResult = "Verified (Model: gemini-1.5-flash ready)"
                                        appPreferences.setGeminiApiKey(apiKeyInput.trim())
                                    } else {
                                        isKeyValid = false
                                        testKeyResult = "Invalid Key / Network error"
                                    }
                                }
                            },
                            icon = Icons.Default.Verified,
                            isPrimary = false,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isTestingKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = HyperViolet, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting to Gemini API...", color = TextMuted, fontSize = 12.sp)
                        }
                    } else if (testKeyResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isKeyValid == true) MintEmerald.copy(alpha = 0.15f) else RoseCrimson.copy(alpha = 0.15f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isKeyValid == true) Icons.Default.CheckCircle else Icons.Default.Close,
                                null,
                                tint = if (isKeyValid == true) MintEmerald else RoseCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = testKeyResult ?: "",
                                color = if (isKeyValid == true) MintEmerald else RoseCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // System Permissions & OEM Checklist
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("System & OEM Permissions", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text("Live Auto-Check", color = MintEmerald, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            PermissionCardRow(
                title = "1. Floating Overlay Permission",
                description = "Draws the floating Iris Capsule & mini-apps above all apps.",
                icon = Icons.Default.Layers,
                isGranted = hasOverlayPermission,
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                },
                onInfoClick = { showOemGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCardRow(
                title = "2. Accessibility Service",
                description = "Performs global navigation gestures and extracts protected text.",
                icon = Icons.Default.Accessibility,
                isGranted = isAccessibilityActive,
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onInfoClick = { showOemGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCardRow(
                title = "3. Battery Optimization Exemption",
                description = "Keeps the floating assistant running in background without OEM freeze.",
                icon = Icons.Default.BatteryChargingFull,
                isGranted = isBatteryOptimized,
                onGrant = {
                    OemPermissionHelper.requestIgnoreBatteryOptimization(context)
                },
                onInfoClick = { showOemGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCardRow(
                title = "4. OEM Auto-Start / Popup Whitelist",
                description = "Opens ${OemPermissionHelper.getDeviceBrand().name} specific security center.",
                icon = Icons.Default.RocketLaunch,
                isGranted = false,
                onGrant = {
                    OemPermissionHelper.openOemAutoStartSettings(context)
                },
                onInfoClick = { showOemGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCardRow(
                title = "5. Screen Snip & Circle Capture",
                description = "Grants hardware screen capture for Circle to Search.",
                icon = Icons.Default.CameraAlt,
                isGranted = isScreenSnipReady,
                onGrant = {
                    val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projectionLauncher.launch(mpManager.createScreenCaptureIntent())
                },
                onInfoClick = { showOemGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCardRow(
                title = "6. Shizuku (FLAG_SECURE Bypass)",
                description = "Wireless ADB to assist in protected student & exam apps.",
                icon = Icons.Default.Shield,
                isGranted = isShizukuReady,
                onGrant = {
                    if (ShizukuBypassService.isShizukuAvailable()) {
                        Shizuku.requestPermission(100)
                    } else {
                        Toast.makeText(context, "Shizuku app is not running", Toast.LENGTH_SHORT).show()
                    }
                },
                onInfoClick = { showOemGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Master Launch Button
            NeonButton(
                text = "Launch Floating Assistant",
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        Toast.makeText(context, "Grant Overlay Permission first", Toast.LENGTH_SHORT).show()
                    } else {
                        ContextCompat.startForegroundService(context, Intent(context, FloatingBallService::class.java))
                        Toast.makeText(context, "AuraView Floating Assistant Launched!", Toast.LENGTH_SHORT).show()
                    }
                },
                icon = Icons.Default.PowerSettingsNew,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Step-by-Step OEM Guide Dialog
    if (showOemGuideDialog) {
        Dialog(onDismissRequest = { showOemGuideDialog = false }) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                cornerRadius = 20.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = HyperViolet, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${OemPermissionHelper.getDeviceBrand().name} Setup Guide",
                            color = TextPureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showOemGuideDialog = false }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Close", tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Follow these instructions for ${OemPermissionHelper.getBrandDisplayName()} to keep AuraView running smoothly without being killed by the system:",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val steps = OemPermissionHelper.getOemStepByStepGuide()
                    steps.forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(step, color = TextOffWhite, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    NeonButton(
                        text = "Open Device Settings",
                        onClick = {
                            showOemGuideDialog = false
                            OemPermissionHelper.openOemAutoStartSettings(context)
                        },
                        icon = Icons.Default.RocketLaunch,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCardRow(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onGrant: () -> Unit,
    onInfoClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isGranted) MintEmerald.copy(alpha = 0.15f) else HyperViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MintEmerald else HyperViolet,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPureWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(description, color = TextMuted, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))

            IconButton(onClick = onInfoClick, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Info, "Help", tint = TextSubtle, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (isGranted) {
                Icon(Icons.Default.CheckCircle, "Granted", tint = MintEmerald, modifier = Modifier.size(22.dp))
            } else {
                NeonButton(
                    text = "Grant",
                    onClick = onGrant,
                    isPrimary = false
                )
            }
        }
    }
}
