package com.arora.assistant.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GroqWhisperClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun testApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("API Key cannot be empty"))

        try {
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val errorBody = response.body?.string() ?: "Invalid API Key"
                val errorMsg = try {
                    JSONObject(errorBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "Error code ${response.code}: $errorBody"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transcribeAudio(
        apiKey: String,
        audioFile: File,
        prompt: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(Exception("Groq API Key is missing"))
        if (!audioFile.exists() || audioFile.length() == 0L) return@withContext Result.failure(Exception("Audio file is empty"))

        try {
            val fileBody = audioFile.asRequestBody("audio/m4a".toMediaType())
            val requestBodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.name, fileBody)
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("response_format", "json")

            if (prompt.isNotBlank()) {
                requestBodyBuilder.addFormDataPart("prompt", prompt)
            }

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer ${apiKey.trim()}")
                .post(requestBodyBuilder.build())
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val text = json.optString("text", "")
                Result.success(text)
            } else {
                val errorMsg = try {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    "Transcription failed (${response.code})"
                }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
