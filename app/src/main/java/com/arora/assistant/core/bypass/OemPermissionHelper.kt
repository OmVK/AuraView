package com.arora.assistant.core.bypass

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object OemPermissionHelper {

    enum class OemBrand {
        XIAOMI,
        OPPO_REALME,
        VIVO_IQOO,
        HUAWEI_HONOR,
        SAMSUNG,
        ONEPLUS,
        STOCK_PIXEL_NOTHING
    }

    fun getDeviceBrand(): OemBrand {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> OemBrand.XIAOMI
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> OemBrand.OPPO_REALME
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> OemBrand.VIVO_IQOO
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> OemBrand.HUAWEI_HONOR
            manufacturer.contains("samsung") -> OemBrand.SAMSUNG
            manufacturer.contains("oneplus") -> OemBrand.ONEPLUS
            else -> OemBrand.STOCK_PIXEL_NOTHING
        }
    }

    fun getBrandDisplayName(): String {
        val brand = Build.BRAND.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return "$brand $model (${getDeviceBrand().name})"
    }

    fun getOemStepByStepGuide(): List<String> {
        return when (getDeviceBrand()) {
            OemBrand.XIAOMI -> listOf(
                "Step 1: In the popup or Security App, select 'Autostart' and turn it ON for AuraView.",
                "Step 2: Go to 'Permissions' > 'Other Permissions'.",
                "Step 3: Enable 'Display pop-up windows while running in the background'.",
                "Step 4: Enable 'Show on Lock screen' and 'Permanent notification'.",
                "Step 5: Set Battery Saver to 'No restrictions'."
            )
            OemBrand.OPPO_REALME -> listOf(
                "Step 1: In Phone Manager / Security Center, go to 'Privacy Permissions' > 'Startup Manager'.",
                "Step 2: Turn ON 'Allow Auto-Launch' and 'Allow Background Activity' for AuraView.",
                "Step 3: In Settings > Battery, turn OFF 'Sleep Standby Optimization' or exclude AuraView.",
                "Step 4: In Floating Window management, enable Floating Windows for AuraView."
            )
            OemBrand.VIVO_IQOO -> listOf(
                "Step 1: In iManager, tap 'App Manager' > 'Permission management' > 'Autostart'.",
                "Step 2: Allow AuraView to auto-start.",
                "Step 3: Tap 'Single permission settings' > 'Floating Window' and enable it.",
                "Step 4: Enable 'Background pop-up' permission so the assistant can appear over games and PDFs.",
                "Step 5: In Battery > High Background Power Consumption, allow AuraView."
            )
            OemBrand.SAMSUNG -> listOf(
                "Step 1: Open Settings > Battery and Device Care > Battery.",
                "Step 2: Tap 'Background usage limits' > 'Never sleeping apps' and add AuraView.",
                "Step 3: Under 'More battery settings', disable 'Adaptive battery' for AuraView.",
                "Step 4: In Settings > Apps > AuraView > Appear on top, toggle ON."
            )
            OemBrand.HUAWEI_HONOR -> listOf(
                "Step 1: In Optimizer / Phone Manager, go to 'App Launch'.",
                "Step 2: Find AuraView, toggle OFF 'Manage Automatically', and enable 'Auto-launch', 'Secondary launch', and 'Run in background'.",
                "Step 3: In Settings > Apps > Special Access > Ignore Battery Optimization, set to 'Allow'."
            )
            else -> listOf(
                "Step 1: In Settings > Apps > Special App Access > 'Display over other apps', select AuraView and enable 'Allow display over other apps'.",
                "Step 2: In Settings > Accessibility, find 'AuraView' in Downloaded Apps and turn it ON.",
                "Step 3: In Settings > Apps > AuraView > Battery, select 'Unrestricted'."
            )
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (ex: Exception) {
                Toast.makeText(context, "Battery settings not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openOemAutoStartSettings(context: Context) {
        val brand = getDeviceBrand()
        val intents = mutableListOf<Intent>()

        when (brand) {
            OemBrand.XIAOMI -> {
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", context.packageName))
            }
            OemBrand.OPPO_REALME -> {
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.sysfloatwindow.FloatWindowListActivity")))
            }
            OemBrand.VIVO_IQOO -> {
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
            }
            OemBrand.HUAWEI_HONOR -> {
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
            }
            OemBrand.SAMSUNG -> {
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
                intents.add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            else -> {
                intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }
        }

        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next
            }
        }

        Toast.makeText(context, "Opened App Settings", Toast.LENGTH_SHORT).show()
    }
}
