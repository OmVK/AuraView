package com.arora.assistant.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "arora_settings")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_BALL_ENABLED = booleanPreferencesKey("ball_enabled")
        val KEY_BALL_SIZE_DP = intPreferencesKey("ball_size_dp")
        val KEY_BALL_OPACITY = floatPreferencesKey("ball_opacity")
        val KEY_EDGE_AUTO_HIDE = booleanPreferencesKey("edge_auto_hide")
        val KEY_AUTO_HIDE_DELAY_MS = intPreferencesKey("auto_hide_delay_ms")
        
        // Gestures
        val KEY_GESTURE_DOUBLE_TAP = stringPreferencesKey("gesture_double_tap")
        val KEY_GESTURE_LONG_PRESS = stringPreferencesKey("gesture_long_press")
        val KEY_GESTURE_SWIPE_UP = stringPreferencesKey("gesture_swipe_up")
        val KEY_GESTURE_SWIPE_DOWN = stringPreferencesKey("gesture_swipe_down")
        val KEY_GESTURE_SWIPE_INWARD = stringPreferencesKey("gesture_swipe_inward")

        // AI Engine
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val KEY_TRANSCRIBER_MODE = stringPreferencesKey("transcriber_mode") // "offline" or "groq"
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_OFFLINE_OCR_ONLY = booleanPreferencesKey("offline_ocr_only")
        val KEY_PREFERRED_AI_ENGINE = stringPreferencesKey("preferred_ai_engine") // "auto", "groq", "gemini"
        val KEY_SHIZUKU_ENABLED = booleanPreferencesKey("shizuku_enabled")
        val KEY_HUB_WIDTH_DP = floatPreferencesKey("hub_width_dp")
    }

    val ballEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_BALL_ENABLED] ?: true }
    val ballSizeDp: Flow<Int> = context.dataStore.data.map { it[KEY_BALL_SIZE_DP] ?: 52 }
    val ballOpacity: Flow<Float> = context.dataStore.data.map { it[KEY_BALL_OPACITY] ?: 0.85f }
    val edgeAutoHide: Flow<Boolean> = context.dataStore.data.map { it[KEY_EDGE_AUTO_HIDE] ?: true }
    val autoHideDelayMs: Flow<Int> = context.dataStore.data.map { it[KEY_AUTO_HIDE_DELAY_MS] ?: 2000 }
    
    val geminiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_GEMINI_API_KEY] ?: "" }
    val groqApiKey: Flow<String> = context.dataStore.data.map { it[KEY_GROQ_API_KEY] ?: "" }
    val preferredAiEngine: Flow<String> = context.dataStore.data.map { it[KEY_PREFERRED_AI_ENGINE] ?: "auto" }
    val transcriberMode: Flow<String> = context.dataStore.data.map { it[KEY_TRANSCRIBER_MODE] ?: "offline" }
    val aiModel: Flow<String> = context.dataStore.data.map { it[KEY_AI_MODEL] ?: "gemini-1.5-flash" }
    val offlineOcrOnly: Flow<Boolean> = context.dataStore.data.map { it[KEY_OFFLINE_OCR_ONLY] ?: false }
    val shizukuEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SHIZUKU_ENABLED] ?: false }
    val hubWidthDp: Flow<Float> = context.dataStore.data.map { it[KEY_HUB_WIDTH_DP] ?: 340f }

    suspend fun setBallEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_BALL_ENABLED] = enabled }
    suspend fun setBallSizeDp(size: Int) = context.dataStore.edit { it[KEY_BALL_SIZE_DP] = size }
    suspend fun setBallOpacity(opacity: Float) = context.dataStore.edit { it[KEY_BALL_OPACITY] = opacity }
    suspend fun setGeminiApiKey(key: String) = context.dataStore.edit { it[KEY_GEMINI_API_KEY] = key }
    suspend fun setGroqApiKey(key: String) = context.dataStore.edit { it[KEY_GROQ_API_KEY] = key }
    suspend fun setPreferredAiEngine(engine: String) = context.dataStore.edit { it[KEY_PREFERRED_AI_ENGINE] = engine }
    suspend fun setTranscriberMode(mode: String) = context.dataStore.edit { it[KEY_TRANSCRIBER_MODE] = mode }
    suspend fun setAiModel(model: String) = context.dataStore.edit { it[KEY_AI_MODEL] = model }
    suspend fun setOfflineOcrOnly(enabled: Boolean) = context.dataStore.edit { it[KEY_OFFLINE_OCR_ONLY] = enabled }
    suspend fun setShizukuEnabled(enabled: Boolean) = context.dataStore.edit { it[KEY_SHIZUKU_ENABLED] = enabled }
    suspend fun setHubWidthDp(width: Float) = context.dataStore.edit { it[KEY_HUB_WIDTH_DP] = width }
}
