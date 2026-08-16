package com.arora.assistant.core.ai

import android.graphics.Bitmap
import android.util.Base64
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

class GeminiClient(private val apiKey: String, private val model: String = "gemini-1.5-flash") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun generateContent(
        prompt: String,
        bitmap: Bitmap? = null,
        systemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Gemini API Key is missing"))

        try {
            // Secure Endpoint: Key is transmitted via x-goog-api-key HTTP header, NEVER in URL query strings
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            
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

            val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey.trim())
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = try {
                    val json = gson.fromJson(responseBody, JsonObject::class.java)
                    json.getAsJsonObject("error").get("message").asString
                } catch (e: Exception) {
                    "API Error (${response.code})"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = jsonObject.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val firstCandidate = candidates.get(0).asJsonObject
                val content = firstCandidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                val text = parts.get(0).asJsonObject.get("text").asString
                Result.success(text)
            } else {
                Result.failure(Exception("No candidate in Gemini response"))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
