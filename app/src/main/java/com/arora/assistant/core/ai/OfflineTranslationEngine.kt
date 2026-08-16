package com.arora.assistant.core.ai

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object OfflineTranslationEngine {

    private val languageIdClient = LanguageIdentification.getClient()

    suspend fun identifyLanguage(text: String): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            languageIdClient.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    if (languageCode == "und") {
                        continuation.resume(TranslateLanguage.ENGLISH)
                    } else {
                        continuation.resume(languageCode)
                    }
                }
                .addOnFailureListener {
                    continuation.resume(TranslateLanguage.ENGLISH)
                }
        }
    }

    fun getMlKitLanguageCode(langName: String): String? {
        return when (langName.lowercase()) {
            "hindi" -> TranslateLanguage.HINDI
            "arabic" -> TranslateLanguage.ARABIC
            "korean" -> TranslateLanguage.KOREAN
            "english" -> TranslateLanguage.ENGLISH
            "spanish" -> TranslateLanguage.SPANISH
            "japanese" -> TranslateLanguage.JAPANESE
            "french" -> TranslateLanguage.FRENCH
            "german" -> TranslateLanguage.GERMAN
            "chinese" -> TranslateLanguage.CHINESE
            else -> null
        }
    }

    suspend fun translateOnDevice(
        text: String,
        targetLanguageName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetLangCode = getMlKitLanguageCode(targetLanguageName)
            ?: return@withContext Result.failure(Exception("Offline model not available for $targetLanguageName"))

        val sourceLangCode = identifyLanguage(text)

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build()

        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        return@withContext try {
            // Ensure on-device neural model is downloaded and ready
            suspendCancellableCoroutine<Unit> { cont ->
                translator.downloadModelIfNeeded(conditions)
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { e -> cont.resume(Unit) }
            }

            val translated = suspendCancellableCoroutine<String> { cont ->
                translator.translate(text)
                    .addOnSuccessListener { res -> cont.resume(res) }
                    .addOnFailureListener { e -> cont.resume("Error: ${e.message}") }
            }

            translator.close()
            Result.success(translated)
        } catch (e: Exception) {
            translator.close()
            Result.failure(e)
        }
    }
}
