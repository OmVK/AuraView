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
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiClient(
    private val apiKey: String,
    private var model: String = "gemini-2.0-flash"
) {

    companion object {
        private const val TAG = "GeminiClient"

        @Volatile
        var defaultWorkingModel: String = "gemini-2.0-flash"

        @Volatile
        var defaultWorkingVersion: String = "v1beta"

        val FALLBACK_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-1.5-flash-002",
            "gemini-1.5-flash-001",
            "gemini-1.5-pro-latest",
            "gemini-1.5-pro",
            "gemini-pro"
        )

        fun compressAndEncodeBitmap(bitmap: Bitmap): String {
            val maxDim = 1024
            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (targetW, targetH) = if (ratio > 1f) {
                    maxDim to (maxDim / ratio).toInt().coerceAtLeast(1)
                } else {
                    (maxDim * ratio).toInt().coerceAtLeast(1) to maxDim
                }
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
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

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("API Key is empty. Please paste your key from Google AI Studio."))
        }

        for (candidate in FALLBACK_MODELS) {
            for (ver in listOf("v1beta", "v1")) {
                val testResult = tryDirectGenerate(
                    cleanKey = cleanKey,
                    version = ver,
                    modelName = candidate,
                    contentsArray = listOf(mapOf("parts" to listOf(mapOf("text" to "ping")))),
                    maxTokens = 5,
                    temperature = 0.1
                )

                if (testResult.isSuccess) {
                    defaultWorkingModel = candidate
                    defaultWorkingVersion = ver
                    Log.i(TAG, "Validated working model: $candidate on $ver")
                    return@withContext Result.success(candidate)
                }
            }
        }

        Result.failure(Exception("Could not find a working Gemini model for your API key. Please check your key in Google AI Studio."))
    }

    suspend fun generateContent(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null,
        maxTokens: Int = 1200,
        temperature: Double = 0.2
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty. Please paste your key from Google AI Studio."))
        }

        val partsList = mutableListOf<Map<String, Any>>()
        if (bitmap != null) {
            val base64Data = compressAndEncodeBitmap(bitmap)
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
        val contents = listOf(mapOf("parts" to partsList))

        executeAutoFallbackGenerate(
            cleanKey = cleanKey,
            contentsArray = contents,
            systemInstruction = systemInstruction,
            maxTokens = maxTokens,
            temperature = temperature
        )
    }

    suspend fun streamGenerate(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null,
        maxTokens: Int = 1200,
        temperature: Double = 0.2,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty. Please paste your key from Google AI Studio."))
        }

        val partsList = mutableListOf<Map<String, Any>>()
        if (bitmap != null) {
            val base64Data = compressAndEncodeBitmap(bitmap)
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
        val contents = listOf(mapOf("parts" to partsList))

        executeAutoFallbackStream(
            cleanKey = cleanKey,
            contentsArray = contents,
            systemInstruction = systemInstruction,
            maxTokens = maxTokens,
            temperature = temperature,
            onChunk = onChunk
        )
    }

    suspend fun generateChat(
        messages: List<ChatMessage>,
        systemInstruction: String? = null,
        maxTokens: Int = 1000,
        temperature: Double = 0.2
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty. Please paste your key from Google AI Studio."))
        }

        val contentsArray = messages.map { msg ->
            mapOf(
                "role" to msg.role,
                "parts" to listOf(mapOf("text" to msg.text))
            )
        }

        executeAutoFallbackGenerate(
            cleanKey = cleanKey,
            contentsArray = contentsArray,
            systemInstruction = systemInstruction,
            maxTokens = maxTokens,
            temperature = temperature
        )
    }

    suspend fun streamGenerateChat(
        messages: List<ChatMessage>,
        systemInstruction: String? = null,
        maxTokens: Int = 800,
        temperature: Double = 0.2,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty."))
        }

        val contentsArray = messages.map { msg ->
            mapOf(
                "role" to msg.role,
                "parts" to listOf(mapOf("text" to msg.text))
            )
        }

        executeAutoFallbackStream(
            cleanKey = cleanKey,
            contentsArray = contentsArray,
            systemInstruction = systemInstruction,
            maxTokens = maxTokens,
            temperature = temperature,
            onChunk = onChunk
        )
    }

    private suspend fun executeAutoFallbackStream(
        cleanKey: String,
        contentsArray: List<Map<String, Any>>,
        systemInstruction: String? = null,
        maxTokens: Int = 1200,
        temperature: Double = 0.2,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> {
        // Build candidate list with defaultWorkingModel first
        val candidates = linkedSetOf<String>().apply {
            add(defaultWorkingModel)
            addAll(FALLBACK_MODELS)
        }

        var lastError = "Unknown error"

        for (candidate in candidates) {
            val result = tryStreamGenerate(
                cleanKey = cleanKey,
                version = defaultWorkingVersion,
                modelName = candidate,
                contentsArray = contentsArray,
                systemInstruction = systemInstruction,
                maxTokens = maxTokens,
                temperature = temperature,
                onChunk = onChunk
            )

            if (result.isSuccess) {
                defaultWorkingModel = candidate
                return result
            } else {
                val err = result.exceptionOrNull()?.message ?: ""
                lastError = err
                // If error is 404/not found, try next candidate
                if (!err.contains("404") && !err.contains("not found", ignoreCase = true) && !err.contains("models/")) {
                    // Non-model error (e.g. invalid API key), return immediately
                    return result
                }
            }
        }

        return Result.failure(Exception(lastError))
    }

    private fun executeAutoFallbackGenerate(
        cleanKey: String,
        contentsArray: List<Map<String, Any>>,
        systemInstruction: String? = null,
        maxTokens: Int = 1200,
        temperature: Double = 0.2
    ): Result<String> {
        val candidates = linkedSetOf<String>().apply {
            add(defaultWorkingModel)
            addAll(FALLBACK_MODELS)
        }

        var lastError = "Unknown error"

        for (candidate in candidates) {
            val result = tryDirectGenerate(
                cleanKey = cleanKey,
                version = defaultWorkingVersion,
                modelName = candidate,
                contentsArray = contentsArray,
                systemInstruction = systemInstruction,
                maxTokens = maxTokens,
                temperature = temperature
            )

            if (result.isSuccess) {
                defaultWorkingModel = candidate
                return result
            } else {
                val err = result.exceptionOrNull()?.message ?: ""
                lastError = err
                if (!err.contains("404") && !err.contains("not found", ignoreCase = true) && !err.contains("models/")) {
                    return result
                }
            }
        }

        return Result.failure(Exception(lastError))
    }

    private suspend fun tryStreamGenerate(
        cleanKey: String,
        version: String,
        modelName: String,
        contentsArray: List<Map<String, Any>>,
        systemInstruction: String? = null,
        maxTokens: Int = 1200,
        temperature: Double = 0.2,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> {
        val cleanModel = modelName.removePrefix("models/").trim()
        val url = "https://generativelanguage.googleapis.com/$version/models/$cleanModel:streamGenerateContent?alt=sse&key=$cleanKey"

        val genConfig = mutableMapOf<String, Any>(
            "maxOutputTokens" to maxTokens,
            "temperature" to temperature,
            "topP" to 0.8
        )

        val payload = mutableMapOf<String, Any>(
            "contents" to contentsArray,
            "generationConfig" to genConfig
        )

        if (!systemInstruction.isNullOrEmpty()) {
            payload["systemInstruction"] = mapOf(
                "parts" to listOf(mapOf("text" to systemInstruction))
            )
        }

        val requestBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                return Result.failure(Exception(extractErrorMessage(response.code, errorBody)))
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            val accumulated = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.startsWith("data:")) {
                    val jsonStr = trimmed.removePrefix("data:").trim()
                    if (jsonStr.isNotBlank()) {
                        try {
                            val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java)
                            val candidates = jsonObject.getAsJsonArray("candidates")
                            if (candidates != null && candidates.size() > 0) {
                                val firstCandidate = candidates.get(0).asJsonObject
                                val content = firstCandidate.getAsJsonObject("content")
                                val parts = content?.getAsJsonArray("parts")
                                if (parts != null) {
                                    for (i in 0 until parts.size()) {
                                        val partObj = parts.get(i).asJsonObject
                                        val isThought = partObj.get("thought")?.asBoolean ?: false
                                        if (!isThought) {
                                            val text = partObj.get("text")?.asString ?: ""
                                            if (text.isNotEmpty()) {
                                                accumulated.append(text)
                                                onChunk(accumulated.toString())
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Ignored per-chunk parse warning
                        }
                    }
                }
            }
            Result.success(accumulated.toString().trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryDirectGenerate(
        cleanKey: String,
        version: String,
        modelName: String,
        contentsArray: List<Map<String, Any>>,
        systemInstruction: String? = null,
        maxTokens: Int = 1200,
        temperature: Double = 0.2
    ): Result<String> {
        val cleanModel = modelName.removePrefix("models/").trim()
        val url = "https://generativelanguage.googleapis.com/$version/models/$cleanModel:generateContent?key=$cleanKey"

        val genConfig = mutableMapOf<String, Any>(
            "maxOutputTokens" to maxTokens,
            "temperature" to temperature,
            "topP" to 0.8
        )

        val payload = mutableMapOf<String, Any>(
            "contents" to contentsArray,
            "generationConfig" to genConfig
        )

        if (!systemInstruction.isNullOrEmpty()) {
            payload["systemInstruction"] = mapOf(
                "parts" to listOf(mapOf("text" to systemInstruction))
            )
        }

        val requestBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                val candidates = jsonObject.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val firstCandidate = candidates.get(0).asJsonObject
                    val content = firstCandidate.getAsJsonObject("content")
                    val parts = content?.getAsJsonArray("parts")

                    val answerBuilder = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.size()) {
                            val partObj = parts.get(i).asJsonObject
                            val isThought = partObj.get("thought")?.asBoolean ?: false
                            if (!isThought) {
                                val partText = partObj.get("text")?.asString ?: ""
                                answerBuilder.append(partText)
                            }
                        }
                    }

                    val finalAnswer = if (answerBuilder.isNotBlank()) {
                        answerBuilder.toString().trim()
                    } else {
                        parts?.lastOrNull()?.asJsonObject?.get("text")?.asString?.trim() ?: ""
                    }

                    return Result.success(finalAnswer)
                }
            }

            val errorMsg = extractErrorMessage(response.code, responseBody)
            Result.failure(Exception(errorMsg))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractErrorMessage(statusCode: Int, responseBody: String?): String {
        if (responseBody.isNullOrBlank()) return "HTTP Error $statusCode"
        return try {
            val json = gson.fromJson(responseBody, JsonObject::class.java)
            val errorObj = json?.getAsJsonObject("error")
            val message = errorObj?.get("message")?.asString
            val status = errorObj?.get("status")?.asString
            if (!message.isNullOrBlank()) {
                if (!status.isNullOrBlank()) "[$status] $message" else message
            } else {
                "HTTP $statusCode: ${errorObj?.toString() ?: responseBody}"
            }
        } catch (e: Exception) {
            "HTTP $statusCode: $responseBody"
        }
    }
}
