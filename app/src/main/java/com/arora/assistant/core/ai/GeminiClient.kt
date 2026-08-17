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
        var defaultWorkingModel: String = "gemini-1.5-flash"
        @Volatile
        var defaultWorkingVersion: String = "v1beta"
        @Volatile
        private var isModelDiscovered: Boolean = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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

        val discoveredModels = mutableListOf<String>()
        var lastErrorMsg = ""

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
                                discoveredModels.add(name)
                            }
                        }
                    }
                    if (discoveredModels.isNotEmpty()) {
                        defaultWorkingVersion = version
                        Log.i(TAG, "Discovered models on $version: $discoveredModels")
                        break
                    }
                } else if (body != null) {
                    lastErrorMsg = extractErrorMessage(resp.code, body)
                    if (resp.code == 400 && lastErrorMsg.contains("API_KEY_INVALID", ignoreCase = true)) {
                        return@withContext Result.failure(Exception("API Key Invalid. Please check that you copied the complete key from Google AI Studio."))
                    }
                }
            } catch (e: Exception) {
                lastErrorMsg = e.message ?: "Network error"
            }
        }

        val candidateModels = mutableListOf<String>()
        val stablePriority = listOf(
            "gemini-1.5-flash",
            "gemini-1.5-flash-002",
            "gemini-1.5-flash-001",
            "gemini-1.5-pro",
            "gemini-1.5-pro-002",
            "gemini-2.0-flash",
            "gemini-pro"
        )

        for (p in stablePriority) {
            if (discoveredModels.contains(p)) {
                candidateModels.add(p)
            }
        }

        for (m in discoveredModels) {
            if (!candidateModels.contains(m) && !m.contains("2.5", ignoreCase = true)) {
                candidateModels.add(m)
            }
        }

        if (candidateModels.isEmpty()) {
            candidateModels.addAll(listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-2.0-flash"))
        }

        for (candidate in candidateModels) {
            defaultWorkingModel = candidate
            val testResult = executeDirectGenerate(
                cleanKey = cleanKey,
                version = defaultWorkingVersion,
                modelName = candidate,
                contentsArray = listOf(mapOf("parts" to listOf(mapOf("text" to "Hello")))),
                maxTokens = 10,
                temperature = 0.2
            )

            if (testResult.isSuccess) {
                isModelDiscovered = true
                Log.i(TAG, "Successfully validated active model: $candidate")
                return@withContext Result.success(candidate)
            } else {
                val err = testResult.exceptionOrNull()?.message ?: ""
                Log.w(TAG, "Model candidate $candidate failed: $err")
                lastErrorMsg = err
            }
        }

        Result.failure(Exception(if (lastErrorMsg.isNotBlank()) lastErrorMsg else "Could not find a working Gemini model for this API key."))
    }

    suspend fun generateContent(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null,
        maxTokens: Int = 2500,
        temperature: Double = 0.2
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty. Please paste your key from Google AI Studio."))
        }

        if (!isModelDiscovered) {
            testConnection()
        }

        val partsList = mutableListOf<Map<String, Any>>()
        if (bitmap != null) {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
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
        val contents = listOf(mapOf("parts" to partsList))

        executeDirectGenerate(
            cleanKey = cleanKey,
            version = defaultWorkingVersion,
            modelName = defaultWorkingModel,
            contentsArray = contents,
            systemInstruction = systemInstruction,
            maxTokens = maxTokens,
            temperature = temperature
        )
    }

    suspend fun generateChat(
        messages: List<ChatMessage>,
        systemInstruction: String? = null,
        maxTokens: Int = 2500,
        temperature: Double = 0.2
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = getSanitizedKey()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API Key is empty. Please paste your key from Google AI Studio."))
        }

        if (!isModelDiscovered) {
            testConnection()
        }

        val contentsArray = messages.map { msg ->
            mapOf(
                "role" to msg.role,
                "parts" to listOf(mapOf("text" to msg.text))
            )
        }

        executeDirectGenerate(
            cleanKey = cleanKey,
            version = defaultWorkingVersion,
            modelName = defaultWorkingModel,
            contentsArray = contentsArray,
            systemInstruction = systemInstruction,
            maxTokens = maxTokens,
            temperature = temperature
        )
    }

    private fun executeDirectGenerate(
        cleanKey: String,
        version: String,
        modelName: String,
        contentsArray: List<Map<String, Any>>,
        systemInstruction: String? = null,
        maxTokens: Int = 2500,
        temperature: Double = 0.2
    ): Result<String> {
        val cleanModel = modelName.removePrefix("models/").trim()
        try {
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
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

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
            return Result.failure(Exception(errorMsg))
        } catch (e: Exception) {
            return Result.failure(e)
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
