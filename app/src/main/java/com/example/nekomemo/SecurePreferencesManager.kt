package com.example.nekomemo

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePreferencesManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_STORY_THEME = "story_theme"
        private const val KEY_STORY_LENGTH = "story_length"
        private const val KEY_LLM_PROVIDER = "llm_provider"
    }

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    fun clearApiKey() {
        sharedPreferences.edit().remove(KEY_API_KEY).apply()
    }

    fun saveStoryTheme(theme: String) {
        sharedPreferences.edit().putString(KEY_STORY_THEME, theme).apply()
    }

    fun getStoryTheme(): String {
        return sharedPreferences.getString(KEY_STORY_THEME, "adventure") ?: "adventure"
    }

    fun saveStoryLength(length: Int) {
        sharedPreferences.edit().putInt(KEY_STORY_LENGTH, length).apply()
    }

    fun getStoryLength(): Int {
        return sharedPreferences.getInt(KEY_STORY_LENGTH, 250)
    }

    fun saveLLMProvider(provider: String) {
        sharedPreferences.edit().putString(KEY_LLM_PROVIDER, provider).apply()
    }

    fun getLLMProvider(): String {
        return sharedPreferences.getString(KEY_LLM_PROVIDER, "OPENAI") ?: "OPENAI"
    }
}