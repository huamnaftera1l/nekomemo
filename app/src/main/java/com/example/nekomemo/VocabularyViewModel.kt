package com.example.nekomemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import com.google.gson.Gson
import com.google.gson.JsonObject
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

enum class ConnState { Idle, Checking, Success, Fail }

// 简化的 LLM 提供商枚举 - 每个只有一个模型
enum class LLMProvider(
    val displayName: String,
    val baseUrl: String,
    val modelName: String
) {
    OPENAI(
        displayName = "OpenAI GPT-4o",
        baseUrl = "https://api.openai.com/",
        modelName = "gpt-4o"
    ),
    DEEPSEEK(
        displayName = "DeepSeek Chat",
        baseUrl = "https://api.deepseek.com/",
        modelName = "deepseek-chat"
    ),
    QWEN(
        displayName = "通义千问 Max",
        "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/",
        "qwen-max-2025-01-25"
    )
}

class VocabularyViewModel(
    private val securePrefs: SecurePreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabularyUiState())
    val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

    private val _currentStory = MutableStateFlow("")
    val currentStory: StateFlow<String> = _currentStory.asStateFlow()

    private val _wordDefinitions = MutableStateFlow<List<WordDefinition>>(emptyList())
    val wordDefinitions: StateFlow<List<WordDefinition>> = _wordDefinitions.asStateFlow()

    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    private val _currentQuizIndex = MutableStateFlow(0)
    val currentQuizIndex: StateFlow<Int> = _currentQuizIndex.asStateFlow()

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore.asStateFlow()

    /* ----------- 连接测试 ----------- */
    private val _connState = MutableStateFlow(ConnState.Idle)
    val connState: StateFlow<ConnState> = _connState.asStateFlow()

    fun checkConnection() {
        val key = securePrefs.getApiKey().orEmpty()
        if (key.isBlank()) { _connState.value = ConnState.Fail; return }

        val retrofit = Retrofit.Builder()
            .baseUrl(_uiState.value.llmProvider.baseUrl)
            .client(OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        viewModelScope.launch {
            _connState.value = ConnState.Checking
            val ok = runCatching {
                retrofit.create(OpenAIService::class.java)
                    .listModels("Bearer $key")      // 第 3 步会补这个接口
                    .isSuccessful
            }.getOrDefault(false)
            _connState.value = if (ok) ConnState.Success else ConnState.Fail
        }
    }


    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            apiKey = securePrefs.getApiKey() ?: "",
            storyTheme = securePrefs.getStoryTheme(),
            storyLength = securePrefs.getStoryLength(),
            llmProvider = try {
                LLMProvider.valueOf(securePrefs.getLLMProvider())
            } catch (e: Exception) {
                LLMProvider.OPENAI
            }
        )
    }

    fun updateApiKey(apiKey: String) {
        securePrefs.saveApiKey(apiKey)
        _uiState.value = _uiState.value.copy(apiKey = apiKey)
    }

    fun updateLLMProvider(provider: LLMProvider) {
        securePrefs.saveLLMProvider(provider.name)
        _uiState.value = _uiState.value.copy(llmProvider = provider)
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
                val words = extractWordDefinitions(story)
                _wordDefinitions.value = words
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
            val provider = _uiState.value.llmProvider
            val theme = securePrefs.getStoryTheme()
            val length = securePrefs.getStoryLength()

            // 根据不同平台调整 prompt（有些平台对中文支持更好）
            val prompt = when (provider) {
                LLMProvider.QWEN -> {
                    // 通义千问对中文指令理解更好，可以用更详细的中文说明
                    """
                    请写一个 $length 词左右的英文故事，必须包含以下所有单词：${wordList.joinToString(", ")}
                    
                    重要要求：
                    1. 每个单词必须以 **word** [词性] (中文释义) *[上下文释义]* 的格式出现
                    2. 词性用英文缩写：n.(名词), v.(动词), adj.(形容词), adv.(副词), prep.(介词)等
                    3. 中文翻译要准确简洁，上下文释义要说明在此语境下的特定含义
                    4. 故事要连贯有趣
                    5. 每个单词只出现一次，且必须全部包含
                    6. 故事主题：$theme
                    
                    示例格式：The traveler had to **abandon** [v.] (放弃) *在此语境下指放弃原定计划* his quest...
                    """.trimIndent()
                }
                else -> {
                    // OpenAI 和 DeepSeek 使用英文指令
                    """
                    Write a $length-word English story that includes all of the following vocabulary words: ${wordList.joinToString(", ")}. 
                    
                    Important requirements:
                    1. Each word must appear in **word** [part-of-speech] (Chinese translation) *[contextual meaning]* format
                    2. Part-of-speech abbreviations: n.(noun), v.(verb), adj.(adjective), adv.(adverb), prep.(preposition), etc.
                    3. Chinese translations should be accurate and concise, contextual meaning explains the specific meaning in this context
                    4. The story should be coherent and interesting
                    5. Each word should appear only once and all must be included
                    6. Theme: $theme
                    
                    Example format: The traveler had to **abandon** [v.] (放弃) *give up the original plan in this context* his quest...
                    """.trimIndent()
                }
            }

            // 构建请求，某些平台可能需要调整参数
            val request = when (provider) {
                LLMProvider.DEEPSEEK -> {
                    // DeepSeek 可能需要调整 temperature 以获得更稳定的输出
                    ChatCompletionRequest(
                        model = provider.modelName,
                        messages = listOf(
                            Message("system", "You are a helpful assistant that creates educational English stories with Chinese translations."),
                            Message("user", prompt)
                        ),
                        temperature = 0.7,
                        max_tokens = 1000
                    )
                }
                else -> {
                    // OpenAI 和 Qwen 使用标准参数
                    ChatCompletionRequest(
                        model = provider.modelName,
                        messages = listOf(Message("user", prompt)),
                        temperature = 0.8,
                        max_tokens = 800
                    )
                }
            }

            // 创建 OkHttpClient 以设置超时时间
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  // 生成故事可能需要较长时间
                .build()

            // 创建 Retrofit 实例
            val retrofit = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(provider.baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val apiService = retrofit.create(OpenAIService::class.java)

            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful) {
                val content = response.body()?.choices?.firstOrNull()?.message?.content
                if (content.isNullOrEmpty()) {
                    throw Exception("API 返回内容为空")
                }

                // 验证返回的故事是否包含所需格式
                if (!content.contains("**") || !content.contains("(")) {
                    throw Exception("生成的故事格式不正确，请重试")
                }

                content
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMessage = when (provider) {
                    LLMProvider.OPENAI -> parseOpenAIError(response.code(), errorBody)
                    LLMProvider.DEEPSEEK -> parseDeepSeekError(response.code(), errorBody)
                    LLMProvider.QWEN -> parseQwenError(response.code(), errorBody)
                }
                throw Exception("${provider.displayName} API 错误: $errorMessage")
            }
        } catch (e: Exception) {
            when (e) {
                is java.net.UnknownHostException -> throw Exception("网络连接失败，请检查网络")
                is java.net.SocketTimeoutException -> throw Exception("请求超时，请重试")
                is javax.net.ssl.SSLException -> throw Exception("SSL 连接错误，请检查网络环境")
                else -> throw e
            }
        }
    }

    // 解析不同平台的错误信息
    private fun parseOpenAIError(code: Int, errorBody: String?): String {
        return when (code) {
            401 -> "API Key 无效，请检查是否正确复制"
            429 -> "请求频率超限或余额不足"
            500, 502, 503 -> "OpenAI 服务器错误，请稍后再试"
            else -> "错误代码 $code: ${extractErrorMessage(errorBody)}"
        }
    }

    private fun parseDeepSeekError(code: Int, errorBody: String?): String {
        return when (code) {
            401 -> "DeepSeek API Key 无效"
            429 -> "请求过于频繁，请稍候再试"
            500, 502, 503 -> "DeepSeek 服务暂时不可用"
            else -> "错误代码 $code: ${extractErrorMessage(errorBody)}"
        }
    }

    private fun parseQwenError(code: Int, errorBody: String?): String {
        return when (code) {
            401 -> "DashScope API Key 无效或未开通服务"
            429 -> "调用频率超限，请稍后再试"
            400 -> "请求参数错误，可能是模型名称不正确"
            500, 502, 503 -> "阿里云服务暂时不可用"
            else -> "错误代码 $code: ${extractErrorMessage(errorBody)}"
        }
    }

    private fun extractErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrEmpty()) return "未知错误"
        return try {
            // 尝试解析 JSON 错误信息
            val gson = com.google.gson.Gson()
            val errorJson = gson.fromJson(errorBody, com.google.gson.JsonObject::class.java)
            errorJson.getAsJsonObject("error")?.get("message")?.asString
                ?: errorJson.get("message")?.asString
                ?: errorBody.take(100)
        } catch (e: Exception) {
            errorBody.take(100)
        }
    }

    private fun extractWordDefinitions(story: String): List<WordDefinition> {
        val pattern = Pattern.compile("\\*\\*(\\w+)\\*\\*\\s*\\[([^\\]]+)\\]\\s*\\(([^)]+)\\)\\s*\\*([^*]+)\\*")
        val matcher = pattern.matcher(story)
        val results = mutableListOf<WordDefinition>()

        while (matcher.find()) {
            val word = matcher.group(1)?.lowercase() ?: ""
            val partOfSpeech = matcher.group(2)?.trim() ?: ""
            val translation = matcher.group(3)?.trim() ?: ""
            val contextMeaning = matcher.group(4)?.trim() ?: ""
            
            if (word.isNotEmpty() && translation.isNotEmpty()) {
                results.add(WordDefinition(
                    word = word,
                    partOfSpeech = partOfSpeech,
                    translation = translation,
                    contextMeaning = if (contextMeaning.isNotEmpty()) contextMeaning else null
                ))
            }
        }
        
        // 如果新格式解析失败，回退到旧格式
        if (results.isEmpty()) {
            val oldPattern = Pattern.compile("\\*\\*(\\w+)\\*\\*\\s*\\((.*?)\\)")
            val oldMatcher = oldPattern.matcher(story)
            while (oldMatcher.find()) {
                val word = oldMatcher.group(1)?.lowercase() ?: ""
                val translation = oldMatcher.group(2)?.trim() ?: ""
                if (word.isNotEmpty() && translation.isNotEmpty()) {
                    results.add(WordDefinition(
                        word = word,
                        partOfSpeech = "unknown",
                        translation = translation,
                        contextMeaning = null
                    ))
                }
            }
        }
        
        return results
    }

    fun startQuiz() {
        val questions = generateQuizQuestions(_wordDefinitions.value)
        _quizQuestions.value = questions
        _currentQuizIndex.value = 0
        _quizScore.value = 0
        _uiState.value = _uiState.value.copy(currentScreen = Screen.Quiz)
    }

    private fun generateQuizQuestions(wordDefinitions: List<WordDefinition>): List<QuizQuestion> {
        val allTranslations = wordDefinitions.map { it.translation }

        return wordDefinitions.map { wordDef ->
            val distractorPool = allTranslations.filter { it != wordDef.translation }
            val numDistractors = minOf(3, distractorPool.size)
            val distractors = distractorPool.shuffled().take(numDistractors)
            val options = (distractors + wordDef.translation).shuffled()
            val correctIndex = options.indexOf(wordDef.translation)

            QuizQuestion(
                word = wordDef.word,
                question = "What is the meaning of the word '${wordDef.word}' (${wordDef.partOfSpeech})?",
                options = options,
                correctIndex = correctIndex,
                correctTranslation = wordDef.translation
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
            Once upon a time, a young adventurer found himself in a difficult situation. He had to **abandon** [v.] (放弃) *give up his original plan* when he discovered that the ancient map was **fragile** [adj.] (脆弱的) *easily broken or damaged* and barely readable. The mysterious circumstances seemed to **compel** [v.] (强迫) *force him to take action* him to take a different path through the **obscure** [adj.] (模糊的) *unclear and hard to see* forest.

            Along the way, he met a stranger who tried to **deceive** [v.] (欺骗) *trick him with lies* him with false promises of treasure. However, the adventurer had made a **pledge** [n.] (承诺) *a solemn promise* to his village to return with the sacred artifact. Despite feeling **weary** [adj.] (疲惫的) *extremely tired* from the long journey, he pressed on with **vivid** [adj.] (生动的) *clear and detailed* memories of his home motivating him.

            In the end, truth and determination would **prevail** [v.] (获胜) *succeed against all odds*, and he would finally **embrace** [v.] (拥抱) *welcome with open arms* the success that awaited him.
        """.trimIndent()
    }
}

data class VocabularyUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentScreen: Screen = Screen.Home,
    val apiKey: String = "",
    val storyTheme: String = "adventure",
    val storyLength: Int = 250,
    val llmProvider: LLMProvider = LLMProvider.OPENAI
)

enum class Screen {
    Home, Settings, Story, Quiz, Result
}

