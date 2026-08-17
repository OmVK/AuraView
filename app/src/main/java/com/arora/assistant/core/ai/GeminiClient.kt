package com.arora.assistant.core.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val apiKey: String,
    private var model: String = "gemini-1.5-flash"
) {

    companion object {
        private const val TAG = "GeminiClient"
        @Volatile
        private var cachedWorkingModel: String? = null
        @Volatile
        private var cachedApiVersion: String = "v1beta"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()

    private fun getSanitizedKey(): String {
        return apiKey.trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
            .replace(" ", "")
    }

    /**
     * Dynamically queries Google's listModels API to find the best available model for this API key.
     */
    suspend fun discoverAvailableModel(): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("API key is empty"))
        }

        val versions = listOf("v1beta", "v1")
        for (version in versions) {
            try {
                val url = "https://generativelanguage.googleapis.com/$version/models?key=$cleanKey"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val json = gson.fromJson(responseBody, JsonObject::class.java)
                    val modelsArray = json.getAsJsonArray("models")
                    if (modelsArray != null && modelsArray.size() > 0) {
                        val availableModels = mutableListOf<String>()
                        for (i in 0 until modelsArray.size()) {
                            val m = modelsArray.get(i).asJsonObject
                            val name = m.get("name")?.asString?.removePrefix("models/") ?: ""
                            val supportedMethods = m.getAsJsonArray("supportedGenerationMethods")
                            val canGenerate = supportedMethods?.any { it.asString == "generateContent" } == true
                            if (canGenerate && name.isNotBlank()) {
                                availableModels.add(name)
                            }
                        }

                        // Select best model by priority
                        val preferred = listOf(
                            "gemini-1.5-flash",
                            "gemini-2.0-flash",
                            "gemini-2.0-flash-exp",
                            "gemini-1.5-flash-8b",
                            "gemini-1.5-pro",
                            "gemini-pro"
                        )

                        val selected = preferred.firstOrNull { p -> availableModels.any { it.contains(p, ignoreCase = true) } }
                            ?: availableModels.firstOrNull()

                        if (selected != null) {
                            cachedWorkingModel = selected
                            cachedApiVersion = version
                            model = selected
                            Log.i(TAG, "Discovered working Gemini model: $selected on $version")
                            return@withContext Result.success(selected)
                        }
                    }
                } else if (responseBody != null) {
                    val errorMsg = extractErrorMessage(response.code, responseBody)
                    // If key is invalid (400 / 403), return error immediately
                    if (response.code == 400 || response.code == 403) {
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed discovering models on $version", e)
            }
        }

        Result.failure(Exception("Could not find any available Gemini models for this key."))
    }

    suspend fun generateContent(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty. Please paste your key from Google AI Studio."))
        }

        // If no working model is cached, discover it first
        if (cachedWorkingModel == null) {
            val discoveryResult = discoverAvailableModel()
            if (discoveryResult.isFailure && discoveryResult.exceptionOrNull()?.message?.contains("API_KEY_INVALID", ignoreCase = true) == true) {
                return@withContext Result.failure(discoveryResult.exceptionOrNull()!!)
            }
        }

        val targetModel = cachedWorkingModel ?: model
        val targetVersion = cachedApiVersion

        // Construct endpoints to try: [discovered model, gemini-1.5-flash, gemini-2.0-flash, gemini-pro]
        val endpointsToTry = listOf(
            Pair(targetVersion, targetModel),
            Pair("v1beta", "gemini-1.5-flash"),
            Pair("v1beta", "gemini-2.0-flash"),
            Pair("v1", "gemini-1.5-flash"),
            Pair("v1", "gemini-pro")
        ).distinct()

        var lastError: Exception? = null

        for ((version, currentModel) in endpointsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/$version/models/$currentModel:generateContent?key=$cleanKey"

                val contentsArray = mutableListOf<Map<String, Any>>()
                val partsList = mutableListOf<Map<String, Any>>()

                if (bitmap != null) {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    partsList.add(
                        mapOf(
                            "inlineData" to mapOf(
                                "mimeType" to "image/jpeg",
                                "data" to base64Data
                            )
                        )
                    )
                }

                partsList.add(mapOf("text" to prompt))
                contentsArray.add(mapOf("parts" to partsList))

                val payload = mutableMapOf<String, Any>("contents" to contentsArray)
                if (!systemInstruction.isNullOrEmpty()) {
                    payload["systemInstruction"] = mapOf(
                        "parts" to listOf(mapOf("text" to systemInstruction))
                    )
                }

                val requestBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    val errorMsg = extractErrorMessage(response.code, responseBody)
                    // If key is invalid (400/403/UNAUTHENTICATED), fail immediately without retrying other endpoints
                    if (response.code == 400 && errorMsg.contains("API_KEY_INVALID", ignoreCase = true)) {
                        return@withContext Result.failure(Exception(errorMsg))
                    }
                    if (response.code == 404) {
                        Log.w(TAG, "Endpoint $version/models/$currentModel not found (404), trying fallback...")
                        lastError = Exception(errorMsg)
                        continue
                    }
                    return@withContext Result.failure(Exception(errorMsg))
                }

                val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                val candidates = jsonObject.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val firstCandidate = candidates.get(0).asJsonObject
                    val content = firstCandidate.getAsJsonObject("content")
                    val parts = content?.getAsJsonArray("parts")
                    val text = parts?.get(0)?.asJsonObject?.get("text")?.asString ?: ""

                    cachedWorkingModel = currentModel
                    cachedApiVersion = version
                    return@withContext Result.success(text)
                } else {
                    return@withContext Result.failure(Exception("Gemini returned empty response candidates."))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network error on $version/$currentModel", e)
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Failed to connect to Google Gemini API. Please check your internet connection."))
    }

    private fun extractErrorMessage(statusCode: Int, responseBody: String?): String {
        if (responseBody.isNullOrBlank()) return "HTTP Error $statusCode"
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val errorObj = json?.getAsJsonObject("error")
            val message = errorObj?.get("message")?.asString
            val status = errorObj?.get("status")?.asString
            if (!message.isNullOrBlank()) {
                "[$status] $message"
            } else {
                "HTTP $statusCode: ${errorObj?.toString() ?: responseBody}"
            }
        } catch (e: Exception) {
            "HTTP $statusCode: $responseBody"
        }
    }
}
