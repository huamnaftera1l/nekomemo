package com.example.nekomemo

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SecurePreferencesManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()
    
    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_STORY_THEME = "story_theme"
        private const val KEY_STORY_LENGTH = "story_length"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_SAVED_STORIES = "saved_stories"
        private const val KEY_USER_INPUT_WORDS = "user_input_words"
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
    
    fun saveStory(story: SavedStory) {
        val currentStories = getSavedStories().toMutableList()
        
        // 如果故事已存在，更新它
        val existingIndex = currentStories.indexOfFirst { it.id == story.id }
        if (existingIndex != -1) {
            currentStories[existingIndex] = story
        } else {
            // 添加新故事，并限制最多保存20个故事
            currentStories.add(0, story) // 添加到开头（最新的）
            if (currentStories.size > 20) {
                currentStories.removeAt(currentStories.size - 1) // 删除最旧的
            }
        }
        
        val json = gson.toJson(currentStories)
        sharedPreferences.edit().putString(KEY_SAVED_STORIES, json).apply()
    }
    
    fun getSavedStories(): List<SavedStory> {
        val json = sharedPreferences.getString(KEY_SAVED_STORIES, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<SavedStory>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    fun deleteStory(storyId: String) {
        val currentStories = getSavedStories().toMutableList()
        currentStories.removeAll { it.id == storyId }
        val json = gson.toJson(currentStories)
        sharedPreferences.edit().putString(KEY_SAVED_STORIES, json).apply()
    }
    
    fun clearAllStories() {
        sharedPreferences.edit().remove(KEY_SAVED_STORIES).apply()
    }
    
    fun saveUserInputWords(wordsText: String) {
        sharedPreferences.edit().putString(KEY_USER_INPUT_WORDS, wordsText).apply()
    }
    
    fun getUserInputWords(): String {
        return sharedPreferences.getString(KEY_USER_INPUT_WORDS, "abandon\nfragile\ncompel\ndeceive\nobscure\npledge\nweary\nvivid\nprevail\nembrace") ?: "abandon\nfragile\ncompel\ndeceive\nobscure\npledge\nweary\nvivid\nprevail\nembrace"
    }
    
    fun clearUserInputWords() {
        sharedPreferences.edit().remove(KEY_USER_INPUT_WORDS).apply()
    }
}