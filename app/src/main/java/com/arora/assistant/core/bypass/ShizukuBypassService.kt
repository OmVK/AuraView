package com.arora.assistant.core.bypass

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.InputStream

object ShizukuBypassService {

    private const val TAG = "ShizukuBypass"

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Executes screencap directly via Shizuku shell with ADB privileges.
     * Completely bypasses app FLAG_SECURE restrictions on Android.
     */
    suspend fun captureSecureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        if (!hasShizukuPermission()) return@withContext null

        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("/system/bin/screencap", "-p"), null, null) as Process

            val inputStream: InputStream = process.inputStream
            val bytes = inputStream.readBytes()
            process.waitFor()

            if (bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to capture secure screen via Shizuku: ${e.message}")
            null
        }
    }
}
