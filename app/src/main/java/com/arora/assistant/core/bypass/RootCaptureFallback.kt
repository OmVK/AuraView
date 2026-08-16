package com.arora.assistant.core.bypass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

object RootCaptureFallback {

    private const val TAG = "RootCapture"

    fun isRootAvailable(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exit = p.waitFor()
            exit == 0
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun captureRootScreen(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "/system/bin/screencap -p"))
            val bytes = process.inputStream.readBytes()
            process.waitFor()

            if (bytes.isNotEmpty()) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Root screencap failed: ${e.message}")
            null
        }
    }
}
