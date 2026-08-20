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
    private var model: String = "gemini-1.5-flash"
) {

    companion object {
        private const val TAG = "GeminiClient"

        @Volatile
        var defaultWorkingModel: String = "gemini-1.5-flash"

        @Volatile
        var defaultWorkingVersion: String = "v1beta"

        @Volatile
        var cachedAvailableModels: List<String> = emptyList()

        val FALLBACK_CANDIDATES = listOf(
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp",
            "gemini-1.5-flash-002",
            "gemini-1.5-flash-001",
            "gemini-1.5-pro",
            "gemini-1.5-pro-latest",
            "gemini-1.5-pro-002"
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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
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

    private fun fetchAvailableModelsFromApi(cleanKey: String): List<String> {
        val result = mutableListOf<String>()
        for (version in listOf("v1beta", "v1")) {
            try {
                val url = "https://generativelanguage.googleapis.com/$version/models?key=$cleanKey"
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string()

                if (resp.isSuccessful && !body.isNullOrBlank()) {
                    val json = gson.fromJson(body, JsonObject::class.java)
                    val models = json.getAsJsonArray("models")
                    if (models != null && models.size() > 0) {
                        for (i in 0 until models.size()) {
                            val item = models.get(i).asJsonObject
                            val name = item.get("name")?.asString?.removePrefix("models/") ?: ""
                            val methods = item.getAsJsonArray("supportedGenerationMethods")
                            val canGen = methods?.any { it.asString == "generateContent" } == true
                            if (canGen && name.isNotBlank()) {
                                result.add(name)
                            }
                        }
                    }
                    if (result.isNotEmpty()) {
                        defaultWorkingVersion = version
                        Log.i(TAG, "Discovered ${result.size} models on $version: $result")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchAvailableModelsFromApi error on $version: ${e.message}")
            }
        }
        return result
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("API Key is empty. Please paste your key from Google AI Studio."))
        }

        val discovered = fetchAvailableModelsFromApi(cleanKey)
        if (discovered.isNotEmpty()) {
            cachedAvailableModels = discovered
        }

        val candidateList = buildList {
            if (discovered.isNotEmpty()) {
                // Prioritize flash models from discovered list
                addAll(discovered.filter { it.contains("flash", ignoreCase = true) })
                addAll(discovered.filter { !it.contains("flash", ignoreCase = true) })
            }
            addAll(FALLBACK_CANDIDATES)
        }.distinct()

        for (candidate in candidateList) {
            for (ver in listOf(defaultWorkingVersion, "v1beta", "v1").distinct()) {
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
                    Log.i(TAG, "Successfully validated active model: $candidate on $ver")
                    return@withContext Result.success(candidate)
                }
            }
        }

        Result.failure(Exception("Could not connect to Gemini. Please verify your API Key."))
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
        val candidateModels = getCandidateModels(cleanKey)
        var lastError = "Request failed"

        for (candidate in candidateModels) {
            for (ver in listOf(defaultWorkingVersion, "v1beta", "v1").distinct()) {
                val streamRes = tryStreamGenerate(
                    cleanKey = cleanKey,
                    version = ver,
                    modelName = candidate,
                    contentsArray = contentsArray,
                    systemInstruction = systemInstruction,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    onChunk = onChunk
                )

                if (streamRes.isSuccess && (streamRes.getOrNull()?.isNotBlank() == true)) {
                    defaultWorkingModel = candidate
                    defaultWorkingVersion = ver
                    return streamRes
                }

                // If streaming failed, immediately try direct generation with the same model
                val directRes = tryDirectGenerate(
                    cleanKey = cleanKey,
                    version = ver,
                    modelName = candidate,
                    contentsArray = contentsArray,
                    systemInstruction = systemInstruction,
                    maxTokens = maxTokens,
                    temperature = temperature
                )

                if (directRes.isSuccess && (directRes.getOrNull()?.isNotBlank() == true)) {
                    val fullText = directRes.getOrNull()!!
                    onChunk(fullText)
                    defaultWorkingModel = candidate
                    defaultWorkingVersion = ver
                    return directRes
                }

                val err = streamRes.exceptionOrNull()?.message ?: directRes.exceptionOrNull()?.message ?: ""
                lastError = err

                // If API Key is fundamentally invalid or quota reached, stop looping
                if (err.contains("API_KEY_INVALID", ignoreCase = true) || err.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                    return Result.failure(Exception(err))
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
        val candidateModels = getCandidateModels(cleanKey)
        var lastError = "Request failed"

        for (candidate in candidateModels) {
            for (ver in listOf(defaultWorkingVersion, "v1beta", "v1").distinct()) {
                val result = tryDirectGenerate(
                    cleanKey = cleanKey,
                    version = ver,
                    modelName = candidate,
                    contentsArray = contentsArray,
                    systemInstruction = systemInstruction,
                    maxTokens = maxTokens,
                    temperature = temperature
                )

                if (result.isSuccess && (result.getOrNull()?.isNotBlank() == true)) {
                    defaultWorkingModel = candidate
                    defaultWorkingVersion = ver
                    return result
                }

                val err = result.exceptionOrNull()?.message ?: ""
                lastError = err

                if (err.contains("API_KEY_INVALID", ignoreCase = true) || err.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                    return result
                }
            }
        }

        return Result.failure(Exception(lastError))
    }

    private fun getCandidateModels(cleanKey: String): List<String> {
        if (cachedAvailableModels.isEmpty()) {
            try {
                val discovered = fetchAvailableModelsFromApi(cleanKey)
                if (discovered.isNotEmpty()) {
                    cachedAvailableModels = discovered
                }
            } catch (e: Exception) {
                // fallback to static list
            }
        }

        return buildList {
            add(defaultWorkingModel)
            if (cachedAvailableModels.isNotEmpty()) {
                addAll(cachedAvailableModels.filter { it.contains("flash", ignoreCase = true) })
                addAll(cachedAvailableModels)
            }
            addAll(FALLBACK_CANDIDATES)
        }.distinct()
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

            val bodyStream = response.body?.byteStream() ?: return Result.failure(Exception("Empty SSE stream"))
            val reader = BufferedReader(InputStreamReader(bodyStream))
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
            val resultText = accumulated.toString().trim()
            if (resultText.isNotEmpty()) {
                Result.success(resultText)
            } else {
                Result.failure(Exception("Empty streaming response"))
            }
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
