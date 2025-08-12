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

    private val _originalWordList = MutableStateFlow<List<String>>(emptyList())
    val originalWordList: StateFlow<List<String>> = _originalWordList.asStateFlow()

    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    private val _currentQuizIndex = MutableStateFlow(0)
    val currentQuizIndex: StateFlow<Int> = _currentQuizIndex.asStateFlow()

    private val _quizScore = MutableStateFlow(0)
    val quizScore: StateFlow<Int> = _quizScore.asStateFlow()

    private val _wrongAnswers = MutableStateFlow<List<WrongAnswer>>(emptyList())
    val wrongAnswers: StateFlow<List<WrongAnswer>> = _wrongAnswers.asStateFlow()

    private val _savedStories = MutableStateFlow<List<SavedStory>>(emptyList())
    val savedStories: StateFlow<List<SavedStory>> = _savedStories.asStateFlow()

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
        loadSavedStories()
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
            },
            userInputWords = securePrefs.getUserInputWords()
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
            
            // 保存原始单词列表用于测验
            _originalWordList.value = wordList

            try {
                val story = if (securePrefs.getApiKey()?.isNotEmpty() == true) {
                    generateStoryWithAPIRetry(wordList)
                } else {
                    getDemoStory()
                }

                _currentStory.value = story
                val words = extractWordDefinitions(story)
                _wordDefinitions.value = words
                
                // 自动保存故事
                saveCurrentStory()
                
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
    
    fun loadDemoStory() {
        // Demo故事立即加载，不显示加载状态
        val defaultWords = listOf("abandon", "fragile", "compel", "deceive", "obscure", "pledge", "weary", "vivid", "prevail", "embrace")
        _originalWordList.value = defaultWords
        
        val story = getDemoStory()
        _currentStory.value = story
        val words = extractWordDefinitions(story)
        _wordDefinitions.value = words
        
        // Demo故事不保存到历史
        _uiState.value = _uiState.value.copy(currentScreen = Screen.Story)
    }

    private suspend fun generateStoryWithAPIRetry(wordList: List<String>): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        
        repeat(5) { attempt ->
            try {
                return@withContext generateStoryWithAPI(wordList)
            } catch (e: Exception) {
                lastException = e
                if (attempt < 4) { // 还有重试机会
                    // 等待1秒再重试
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
        
        // 5次都失败了，抛出详细的错误信息
        throw Exception("尝试了5次都无法生成完整故事。建议：1) 增加故事长度到400-500词 2) 减少单词数量 3) 更换AI模型。最后错误：${lastException?.message}")
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
                    
                    ⚠️ 严格要求 - 必须遵守：
                    1. 每个单词必须以 **word** [词性] (中文释义) *[上下文释义]* 的格式出现
                    2. 词性用英文缩写：n.(名词), v.(动词), adj.(形容词), adv.(副词), prep.(介词)等
                    3. 中文翻译要准确简洁，上下文释义要说明在此语境下的特定含义
                    4. 故事要连贯有趣
                    5. 每个单词只出现一次，且必须全部包含 - 绝对不能遗漏任何一个单词！
                    6. 故事主题：$theme
                    7. 如果单词太多无法在一个故事中自然包含，请写多个相关的段落或章节
                    
                    ⚠️ 重要：输入的${wordList.size}个单词必须全部出现在故事中，一个都不能少！
                    
                    示例格式：The traveler had to **abandon** [v.] (放弃) *在此语境下指放弃原定计划* his quest...
                    """.trimIndent()
                }
                else -> {
                    // OpenAI 和 DeepSeek 使用英文指令
                    """
                    Write a $length-word English story that includes all of the following vocabulary words: ${wordList.joinToString(", ")}. 
                    
                    ⚠️ STRICT REQUIREMENTS - MUST FOLLOW:
                    1. Each word must appear in **word** [part-of-speech] (Chinese translation) *[contextual meaning]* format
                    2. Part-of-speech abbreviations: n.(noun), v.(verb), adj.(adjective), adv.(adverb), prep.(preposition), etc.
                    3. Chinese translations should be accurate and concise, contextual meaning explains the specific meaning in this context
                    4. The story should be coherent and interesting
                    5. Each word should appear only once and ALL ${wordList.size} WORDS MUST BE INCLUDED - NO EXCEPTIONS!
                    6. Theme: $theme
                    7. If there are too many words for one story, write multiple related paragraphs or chapters
                    
                    ⚠️ CRITICAL: All ${wordList.size} input words must appear in the story - not even one can be missed!
                    
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
                val responseBody = response.body()
                val content = responseBody?.choices?.firstOrNull()?.message?.content
                if (content.isNullOrEmpty()) {
                    throw Exception("API 返回内容为空")
                }

                // 验证返回的故事是否包含所需格式
                if (!content.contains("**") || !content.contains("(")) {
                    throw Exception("生成的故事格式不正确，请重试")
                }

                // 检查是否包含了所有输入的单词
                val extractedWords = extractWordDefinitions(content)
                val extractedWordNames = extractedWords.map { it.word.lowercase() }.toSet()
                val inputWordNames = wordList.map { it.lowercase().trim() }.toSet()
                val missingWords = inputWordNames - extractedWordNames
                
                if (missingWords.isNotEmpty()) {
                    throw Exception("AI未能包含所有单词，缺少：${missingWords.joinToString(", ")}。请重试生成故事。")
                }

                // 解析token使用情况并显示
                responseBody.usage?.let { usage ->
                    val tokenUsage = TokenUsage(
                        promptTokens = usage.prompt_tokens,
                        completionTokens = usage.completion_tokens,
                        totalTokens = usage.total_tokens
                    )
                    // 在主线程中显示token使用情况
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        showTokenUsage(tokenUsage)
                    }
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
        val questions = generateQuizQuestions(_originalWordList.value, _wordDefinitions.value)
        _quizQuestions.value = questions
        _currentQuizIndex.value = 0
        _quizScore.value = 0
        _wrongAnswers.value = emptyList()
        _uiState.value = _uiState.value.copy(currentScreen = Screen.Quiz)
    }

    private fun generateQuizQuestions(originalWords: List<String>, wordDefinitions: List<WordDefinition>): List<QuizQuestion> {
        val wordDefMap = wordDefinitions.associateBy { it.word.lowercase() }
        val allTranslations = wordDefinitions.map { it.translation }
        
        // 只为故事中实际出现的单词生成测验题目
        return originalWords.mapNotNull { originalWord ->
            val word = originalWord.lowercase().trim()
            val wordDef = wordDefMap[word]
            
            wordDef?.let {
                // 只有在故事中找到了这个单词的定义，才生成题目
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
    }

    fun submitAnswer(selectedIndex: Int) {
        val questions = _quizQuestions.value
        val currentIndex = _currentQuizIndex.value

        if (currentIndex < questions.size) {
            val question = questions[currentIndex]
            if (selectedIndex == question.correctIndex) {
                _quizScore.value = _quizScore.value + 1
            } else {
                // 记录错误答案
                val userAnswer = if (selectedIndex < question.options.size) {
                    question.options[selectedIndex]
                } else {
                    "未选择"
                }
                
                // 从wordDefinitions中获取词性和上下文信息
                val wordDef = _wordDefinitions.value.find { it.word.lowercase() == question.word.lowercase() }
                val wrongAnswer = WrongAnswer(
                    word = question.word,
                    partOfSpeech = wordDef?.partOfSpeech ?: "unknown",
                    correctTranslation = question.correctTranslation,
                    userAnswer = userAnswer,
                    contextMeaning = wordDef?.contextMeaning
                )
                
                _wrongAnswers.value = _wrongAnswers.value + wrongAnswer
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
    
    private fun loadSavedStories() {
        _savedStories.value = securePrefs.getSavedStories()
    }
    
    private fun saveCurrentStory() {
        val story = _currentStory.value
        val words = _wordDefinitions.value
        val originalWords = _originalWordList.value
        
        if (story.isNotEmpty() && words.isNotEmpty()) {
            val savedStory = SavedStory(
                id = System.currentTimeMillis().toString(),
                title = generateStoryTitle(originalWords),
                content = story,
                wordDefinitions = words,
                originalWords = originalWords,
                theme = securePrefs.getStoryTheme(),
                createdAt = System.currentTimeMillis(),
                llmProvider = _uiState.value.llmProvider.displayName
            )
            
            securePrefs.saveStory(savedStory)
            loadSavedStories() // 刷新列表
        }
    }
    
    private fun generateStoryTitle(words: List<String>): String {
        return when {
            words.isEmpty() -> "Demo故事"
            words.size <= 3 -> words.joinToString(", ")
            else -> "${words.take(3).joinToString(", ")}等${words.size}个单词"
        }
    }
    
    fun loadStory(savedStory: SavedStory) {
        _currentStory.value = savedStory.content
        _wordDefinitions.value = savedStory.wordDefinitions
        _originalWordList.value = savedStory.originalWords
        _uiState.value = _uiState.value.copy(currentScreen = Screen.Story)
    }
    
    fun deleteStory(storyId: String) {
        securePrefs.deleteStory(storyId)
        loadSavedStories() // 刷新列表
    }
    
    fun updateUserInputWords(words: String) {
        securePrefs.saveUserInputWords(words)
        _uiState.value = _uiState.value.copy(userInputWords = words)
    }
    
    fun clearUserInputWords() {
        securePrefs.clearUserInputWords()
        _uiState.value = _uiState.value.copy(userInputWords = securePrefs.getUserInputWords())
    }
    
    fun showQRCode(type: QRCodeType) {
        _uiState.value = _uiState.value.copy(showQRCode = type)
    }
    
    fun hideQRCode() {
        _uiState.value = _uiState.value.copy(showQRCode = null)
    }
    
    fun showTokenUsage(tokenUsage: TokenUsage) {
        _uiState.value = _uiState.value.copy(showTokenUsage = tokenUsage)
    }
    
    fun hideTokenUsage() {
        _uiState.value = _uiState.value.copy(showTokenUsage = null)
    }

    private fun getDemoStory(): String {
        return """
            In the mystical realm of Eldoria, a brave young explorer named Elena faced an impossible quest. When the ancient council asked her to retrieve the Lost Crystal of Wisdom, she knew she could not **abandon** [v.] (放弃) *give up on this crucial mission* her sacred duty, even though the task seemed overwhelming.

            The journey began at dawn, when Elena discovered that the only map to the crystal's location was incredibly **fragile** [adj.] (脆弱的) *easily damaged by time and weather*, its edges crumbling at the slightest touch. The urgency of her mission seemed to **compel** [v.] (强迫) *force her to act immediately* her to move quickly, despite the dangerous path ahead through the **obscure** [adj.] (模糊的) *hidden and mysterious* Shadowlands.

            During her travels, Elena encountered a cunning merchant who attempted to **deceive** [v.] (欺骗) *trick her with false information* her by offering fake directions in exchange for her magical compass. However, she had made a solemn **pledge** [n.] (承诺) *sacred vow to her people* to the Crystal Guardians that she would never trade away her protective artifacts.

            As the sun began to set, Elena felt increasingly **weary** [adj.] (疲惫的) *exhausted from the long journey*, but she pressed forward with **vivid** [adj.] (生动的) *bright and clear* memories of her village's suffering motivating every step. She knew that good must **prevail** [v.] (获胜) *triumph over darkness* over the evil forces threatening her homeland.

            Finally, as Elena reached the Crystal Chamber, she was ready to **embrace** [v.] (拥抱) *accept and welcome* whatever challenges awaited her, knowing that her courage and determination had brought her this far on the most important adventure of her life.
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
    val llmProvider: LLMProvider = LLMProvider.OPENAI,
    val userInputWords: String = "",
    val showQRCode: QRCodeType? = null,
    val showTokenUsage: TokenUsage? = null
)

enum class Screen {
    Home, Settings, Story, Quiz, Result, WrongAnswers, StoryHistory, About
}

enum class QRCodeType {
    WECHAT, ZELLE, PAYPAL
}

