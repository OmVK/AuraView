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

class GroqClient(
    private val apiKey: String
) {

    companion object {
        private const val TAG = "GroqClient"

        @Volatile
        var cachedTextModel: String = "llama-3.3-70b-versatile"

        @Volatile
        var cachedVisionModel: String = "llama-3.2-11b-vision-preview"

        @Volatile
        var cachedDiscoveredModels: List<String> = emptyList()

        val PREFERRED_TEXT_MODELS = listOf(
            "llama-3.3-70b-versatile",
            "llama3-70b-8192",
            "llama-3.1-70b-versatile",
            "llama-3.1-8b-instant",
            "llama3-8b-8192",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        )

        val PREFERRED_VISION_MODELS = listOf(
            "llama-3.2-11b-vision-preview",
            "llama-3.2-90b-vision-preview",
            "llama-3.2-11b-text-preview"
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

    private fun fetchAvailableGroqModels(cleanKey: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val url = "https://api.groq.com/openai/v1/models"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $cleanKey")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string()

            if (resp.isSuccessful && !body.isNullOrBlank()) {
                val json = gson.fromJson(body, JsonObject::class.java)
                val data = json.getAsJsonArray("data")
                if (data != null) {
                    for (i in 0 until data.size()) {
                        val item = data.get(i).asJsonObject
                        val id = item.get("id")?.asString ?: ""
                        if (id.isNotBlank()) {
                            list.add(id)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Groq models: ${e.message}")
        }
        return list
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Groq API Key is empty. Please paste your key from console.groq.com."))
        }

        val discovered = fetchAvailableGroqModels(cleanKey)
        if (discovered.isNotEmpty()) {
            cachedDiscoveredModels = discovered
            val bestText = PREFERRED_TEXT_MODELS.firstOrNull { discovered.contains(it) } ?: discovered.first()
            val bestVision = PREFERRED_VISION_MODELS.firstOrNull { discovered.contains(it) } ?: "llama-3.2-11b-vision-preview"
            cachedTextModel = bestText
            cachedVisionModel = bestVision
            Log.i(TAG, "Groq verified models: text=$bestText, vision=$bestVision")
            return@withContext Result.success("Groq LPU Active ($bestText)")
        }

        // Fallback test with candidates
        for (candidate in PREFERRED_TEXT_MODELS) {
            val res = tryDirectPing(cleanKey, candidate)
            if (res.isSuccess) {
                cachedTextModel = candidate
                return@withContext Result.success("Groq LPU Active ($candidate)")
            }
        }

        Result.failure(Exception("Invalid Groq API Key or network issue. Please check console.groq.com."))
    }

    private fun tryDirectPing(cleanKey: String, modelName: String): Result<String> {
        try {
            val url = "https://api.groq.com/openai/v1/chat/completions"
            val payload = mapOf(
                "model" to modelName,
                "messages" to listOf(mapOf("role" to "user", "content" to "hi")),
                "max_tokens" to 3
            )
            val requestBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $cleanKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) {
                return Result.success("OK")
            }
            return Result.failure(Exception(extractErrorMessage(response.code, body)))
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun getTextCandidateModels(cleanKey: String): List<String> {
        if (cachedDiscoveredModels.isEmpty()) {
            val discovered = fetchAvailableGroqModels(cleanKey)
            if (discovered.isNotEmpty()) {
                cachedDiscoveredModels = discovered
            }
        }

        return buildList {
            add(cachedTextModel)
            if (cachedDiscoveredModels.isNotEmpty()) {
                for (p in PREFERRED_TEXT_MODELS) {
                    if (cachedDiscoveredModels.contains(p)) add(p)
                }
                addAll(cachedDiscoveredModels.filter { !it.contains("whisper") && !it.contains("vision") })
            }
            addAll(PREFERRED_TEXT_MODELS)
        }.distinct()
    }

    private fun getVisionCandidateModels(cleanKey: String): List<String> {
        if (cachedDiscoveredModels.isEmpty()) {
            val discovered = fetchAvailableGroqModels(cleanKey)
            if (discovered.isNotEmpty()) {
                cachedDiscoveredModels = discovered
            }
        }

        return buildList {
            add(cachedVisionModel)
            if (cachedDiscoveredModels.isNotEmpty()) {
                for (p in PREFERRED_VISION_MODELS) {
                    if (cachedDiscoveredModels.contains(p)) add(p)
                }
                addAll(cachedDiscoveredModels.filter { it.contains("vision") })
            }
            addAll(PREFERRED_VISION_MODELS)
        }.distinct()
    }

    suspend fun generateContent(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null,
        maxTokens: Int = 1000,
        temperature: Double = 0.2
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Groq API Key is empty."))
        }

        val messages = mutableListOf<Map<String, Any>>()
        if (!systemInstruction.isNullOrBlank()) {
            messages.add(mapOf("role" to "system", "content" to systemInstruction))
        }

        if (bitmap != null) {
            val base64Data = compressAndEncodeBitmap(bitmap)
            messages.add(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to prompt),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf(
                                "url" to "data:image/jpeg;base64,$base64Data"
                            )
                        )
                    )
                )
            )
            val candidates = getVisionCandidateModels(cleanKey)
            for (m in candidates) {
                val res = executeDirectGenerate(cleanKey, m, messages, maxTokens, temperature)
                if (res.isSuccess) {
                    cachedVisionModel = m
                    return@withContext res
                }
            }
            Result.failure(Exception("Groq Vision model failed"))
        } else {
            messages.add(mapOf("role" to "user", "content" to prompt))
            val candidates = getTextCandidateModels(cleanKey)
            for (m in candidates) {
                val res = executeDirectGenerate(cleanKey, m, messages, maxTokens, temperature)
                if (res.isSuccess) {
                    cachedTextModel = m
                    return@withContext res
                }
            }
            Result.failure(Exception("Groq Text model failed"))
        }
    }

    suspend fun streamGenerate(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null,
        maxTokens: Int = 1000,
        temperature: Double = 0.2,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Groq API Key is empty."))
        }

        val messages = mutableListOf<Map<String, Any>>()
        if (!systemInstruction.isNullOrBlank()) {
            messages.add(mapOf("role" to "system", "content" to systemInstruction))
        }

        if (bitmap != null) {
            val base64Data = compressAndEncodeBitmap(bitmap)
            messages.add(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf("type" to "text", "text" to prompt),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf(
                                "url" to "data:image/jpeg;base64,$base64Data"
                            )
                        )
                    )
                )
            )
            val candidates = getVisionCandidateModels(cleanKey)
            for (m in candidates) {
                val res = executeStreamGenerate(cleanKey, m, messages, maxTokens, temperature, onChunk)
                if (res.isSuccess) {
                    cachedVisionModel = m
                    return@withContext res
                }
            }
            Result.failure(Exception("Groq Vision stream failed"))
        } else {
            messages.add(mapOf("role" to "user", "content" to prompt))
            val candidates = getTextCandidateModels(cleanKey)
            for (m in candidates) {
                val res = executeStreamGenerate(cleanKey, m, messages, maxTokens, temperature, onChunk)
                if (res.isSuccess) {
                    cachedTextModel = m
                    return@withContext res
                }
            }
            Result.failure(Exception("Groq Text stream failed"))
        }
    }

    suspend fun generateChat(
        messages: List<ChatMessage>,
        systemInstruction: String? = null,
        maxTokens: Int = 800,
        temperature: Double = 0.3
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Groq API Key is empty."))
        }

        val formattedMessages = mutableListOf<Map<String, Any>>()
        if (!systemInstruction.isNullOrBlank()) {
            formattedMessages.add(mapOf("role" to "system", "content" to systemInstruction))
        }
        for (m in messages) {
            val role = if (m.role == "model") "assistant" else m.role
            formattedMessages.add(mapOf("role" to role, "content" to m.text))
        }

        val candidates = getTextCandidateModels(cleanKey)
        for (m in candidates) {
            val res = executeDirectGenerate(cleanKey, m, formattedMessages, maxTokens, temperature)
            if (res.isSuccess) {
                cachedTextModel = m
                return@withContext res
            }
        }

        Result.failure(Exception("Groq Chat model failed"))
    }

    suspend fun streamGenerateChat(
        messages: List<ChatMessage>,
        systemInstruction: String? = null,
        maxTokens: Int = 800,
        temperature: Double = 0.3,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Groq API Key is empty."))
        }

        val formattedMessages = mutableListOf<Map<String, Any>>()
        if (!systemInstruction.isNullOrBlank()) {
            formattedMessages.add(mapOf("role" to "system", "content" to systemInstruction))
        }
        for (m in messages) {
            val role = if (m.role == "model") "assistant" else m.role
            formattedMessages.add(mapOf("role" to role, "content" to m.text))
        }

        val candidates = getTextCandidateModels(cleanKey)
        for (m in candidates) {
            val res = executeStreamGenerate(cleanKey, m, formattedMessages, maxTokens, temperature, onChunk)
            if (res.isSuccess) {
                cachedTextModel = m
                return@withContext res
            }
        }

        Result.failure(Exception("Groq Chat stream failed"))
    }

    private suspend fun executeStreamGenerate(
        cleanKey: String,
        modelName: String,
        messages: List<Map<String, Any>>,
        maxTokens: Int,
        temperature: Double,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val payload = mapOf(
            "model" to modelName,
            "messages" to messages,
            "max_tokens" to maxTokens,
            "temperature" to temperature,
            "stream" to true
        )

        val requestBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $cleanKey")
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                return Result.failure(Exception(extractErrorMessage(response.code, errorBody)))
            }

            val bodyStream = response.body?.byteStream() ?: return Result.failure(Exception("Empty Groq stream"))
            val reader = BufferedReader(InputStreamReader(bodyStream))
            val accumulated = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.startsWith("data:")) {
                    val jsonStr = trimmed.removePrefix("data:").trim()
                    if (jsonStr == "[DONE]") break
                    if (jsonStr.isNotBlank()) {
                        try {
                            val jsonObject = gson.fromJson(jsonStr, JsonObject::class.java)
                            val choices = jsonObject.getAsJsonArray("choices")
                            if (choices != null && choices.size() > 0) {
                                val firstChoice = choices.get(0).asJsonObject
                                val delta = firstChoice.getAsJsonObject("delta")
                                val text = delta?.get("content")?.asString ?: ""
                                if (text.isNotEmpty()) {
                                    accumulated.append(text)
                                    onChunk(accumulated.toString())
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
                Result.failure(Exception("Empty response from Groq LPU"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeDirectGenerate(
        cleanKey: String,
        modelName: String,
        messages: List<Map<String, Any>>,
        maxTokens: Int,
        temperature: Double
    ): Result<String> {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        val payload = mapOf(
            "model" to modelName,
            "messages" to messages,
            "max_tokens" to maxTokens,
            "temperature" to temperature
        )

        val requestBody = gson.toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $cleanKey")
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                val choices = jsonObject.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val firstChoice = choices.get(0).asJsonObject
                    val message = firstChoice.getAsJsonObject("message")
                    val content = message?.get("content")?.asString?.trim() ?: ""
                    return Result.success(content)
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
            if (!message.isNullOrBlank()) {
                message
            } else {
                "HTTP $statusCode: ${errorObj?.toString() ?: responseBody}"
            }
        } catch (e: Exception) {
            "HTTP $statusCode: $responseBody"
        }
    }
}
