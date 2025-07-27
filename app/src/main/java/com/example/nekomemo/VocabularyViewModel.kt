package com.example.nekomemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.regex.Pattern

class VocabularyViewModel(
    private val securePrefs: SecurePreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabularyUiState())
    val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

    private val _currentStory = MutableStateFlow("")
    val currentStory: StateFlow<String> = _currentStory.asStateFlow()

    private val _wordTranslations = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val wordTranslations: StateFlow<List<Pair<String, String>>> = _wordTranslations.asStateFlow()

    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    private val _currentQuizIndex = MutableStateFlow(0)
    val currentQuizIndex: StateFlow<Int> = _currentQuizIndex.asStateFlow()

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore.asStateFlow()

    private val openAIService = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenAIService::class.java)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            apiKey = securePrefs.getApiKey() ?: "",
            storyTheme = securePrefs.getStoryTheme(),
            storyLength = securePrefs.getStoryLength()
        )
    }

    fun updateApiKey(apiKey: String) {
        securePrefs.saveApiKey(apiKey)
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
    }

    fun updateStoryTheme(theme: String) {
        securePrefs.saveStoryTheme(theme)
        _uiState.value = _uiState.value.copy(storyTheme = theme)
    }

    fun updateStoryLength(length: Int) {
        securePrefs.saveStoryLength(length)
        _uiState.value = _uiState.value.copy(storyLength = length)
    }

    fun generateStory(wordList: List<String>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val story = if (securePrefs.getApiKey()?.isNotEmpty() == true) {
                    generateStoryWithAPI(wordList)
                } else {
                    getDemoStory()
                }

                _currentStory.value = story
                val words = extractWordTranslations(story)
                _wordTranslations.value = words
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentScreen = Screen.Story
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "生成故事失败: ${e.message}"
                )
            }
        }
    }

    private suspend fun generateStoryWithAPI(wordList: List<String>): String = withContext(Dispatchers.IO) {
        try {
            val apiKey = securePrefs.getApiKey() ?: return@withContext getDemoStory()
            val theme = securePrefs.getStoryTheme()
            val length = securePrefs.getStoryLength()

            val prompt = """
                Write a $length-word English story that includes all of the following vocabulary words: ${wordList.joinToString(", ")}. 
                
                Important requirements:
                1. Each word must appear in **word** (Chinese translation) format
                2. Chinese translations should be accurate and concise
                3. The story should be coherent and interesting
                4. Each word should appear only once and all must be included
                5. Theme: $theme
                
                Example format: The traveler had to **abandon** (放弃) his quest...
            """.trimIndent()

            val request = ChatCompletionRequest(
                messages = listOf(Message("user", prompt))
            )

            val response = openAIService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                response.body()?.choices?.firstOrNull()?.message?.content ?: getDemoStory()
            } else {
                getDemoStory()
            }
        } catch (e: Exception) {
            getDemoStory()
        }
    }

    private fun extractWordTranslations(story: String): List<Pair<String, String>> {
        val pattern = Pattern.compile("\\*\\*(\\w+)\\*\\*\\s*\\((.*?)\\)")
        val matcher = pattern.matcher(story)
        val results = mutableListOf<Pair<String, String>>()

        while (matcher.find()) {
            val word = matcher.group(1)?.lowercase() ?: ""
            val translation = matcher.group(2)?.trim() ?: ""
            if (word.isNotEmpty() && translation.isNotEmpty()) {
                results.add(Pair(word, translation))
            }
        }
        return results
    }

    fun startQuiz() {
        val questions = generateQuizQuestions(_wordTranslations.value)
        _quizQuestions.value = questions
        _currentQuizIndex.value = 0
        _quizScore.value = 0
        _uiState.value = _uiState.value.copy(currentScreen = Screen.Quiz)
    }

    private fun generateQuizQuestions(wordTranslations: List<Pair<String, String>>): List<QuizQuestion> {
        val allTranslations = wordTranslations.map { it.second }

        return wordTranslations.map { (word, correctTranslation) ->
            val distractorPool = allTranslations.filter { it != correctTranslation }
            val numDistractors = minOf(3, distractorPool.size)
            val distractors = distractorPool.shuffled().take(numDistractors)
            val options = (distractors + correctTranslation).shuffled()
            val correctIndex = options.indexOf(correctTranslation)

            QuizQuestion(
                word = word,
                question = "What is the meaning of the word '$word'?",
                options = options,
                correctIndex = correctIndex,
                correctTranslation = correctTranslation
            )
        }
    }

    fun submitAnswer(selectedIndex: Int) {
        val questions = _quizQuestions.value
        val currentIndex = _currentQuizIndex.value

        if (currentIndex < questions.size) {
            val question = questions[currentIndex]
            if (selectedIndex == question.correctIndex) {
                _quizScore.value = _quizScore.value + 1
            }

            if (currentIndex + 1 >= questions.size) {
                _uiState.value = _uiState.value.copy(currentScreen = Screen.Result)
            } else {
                _currentQuizIndex.value = currentIndex + 1
            }
        }
    }

    fun navigateToScreen(screen: Screen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun getDemoStory(): String {
        return """
            Once upon a time, a young adventurer found himself in a difficult situation. He had to **abandon** (放弃) his original plan when he discovered that the ancient map was **fragile** (脆弱的) and barely readable. The mysterious circumstances seemed to **compel** (强迫) him to take a different path through the **obscure** (模糊的) forest.

            Along the way, he met a stranger who tried to **deceive** (欺骗) him with false promises of treasure. However, the adventurer had made a **pledge** (承诺) to his village to return with the sacred artifact. Despite feeling **weary** (疲惫的) from the long journey, he pressed on with **vivid** (生动的) memories of his home motivating him.

            In the end, truth and determination would **prevail** (获胜), and he would finally **embrace** (拥抱) the success that awaited him.
        """.trimIndent()
    }
}

data class VocabularyUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentScreen: Screen = Screen.Home,
    val apiKey: String = "",
    val storyTheme: String = "adventure",
    val storyLength: Int = 250
)

enum class Screen {
    Home, Settings, Story, Quiz, Result
}