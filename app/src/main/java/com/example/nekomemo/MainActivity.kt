package com.example.nekomemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nekomemo.ui.screens.SettingsScreen
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NekoMemoTheme {
                val context = LocalContext.current
                val securePrefs = remember { SecurePreferencesManager(context) }
                val viewModel: VocabularyViewModel = viewModel {
                    VocabularyViewModel(securePrefs)
                }

                NekoMemoApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NekoMemoApp(viewModel: VocabularyViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🐱 NekoMemo - 猫猫背单词") },
                actions = {
                    IconButton(onClick = { viewModel.navigateToScreen(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.currentScreen) {
                Screen.Home -> HomeScreen(viewModel)
                Screen.Settings -> SettingsScreen(viewModel)
                Screen.Story -> StoryScreen(viewModel)
                Screen.Quiz -> QuizScreen(viewModel)
                Screen.Result -> ResultScreen(viewModel)
                Screen.WrongAnswers -> WrongAnswersScreen(viewModel)
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在生成故事...")
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    // 自动清除错误状态
                    kotlinx.coroutines.delay(3000)
                    viewModel.clearError()
                }
                
                // 显示错误提示
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = Color.Red
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: VocabularyViewModel) {
    var wordInput by remember { mutableStateOf("abandon\nfragile\ncompel\ndeceive\nobscure\npledge\nweary\nvivid\nprevail\nembrace") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "📝 输入单词列表",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("请输入要学习的单词，每行一个:")
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = wordInput,
                    onValueChange = { wordInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("输入单词...") }
                )
            }
        }

        Button(
            onClick = {
                val words = wordInput.split("\n", ",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (words.size >= 2) {
                    viewModel.generateStory(words)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("✍️️生成故事")
        }

        OutlinedButton(
            onClick = {
                val defaultWords = listOf("abandon", "fragile", "compel", "deceive", "obscure", "pledge", "weary", "vivid", "prevail", "embrace")
                viewModel.generateStory(defaultWords)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("使用Demo故事")
        }
    }
}

// 富文本解析函数
@Composable
fun parseRichText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Pattern.compile("\\*\\*(\\w+)\\*\\*\\s*\\[([^\\]]+)\\]\\s*\\(([^)]+)\\)\\s*\\*([^*]+)\\*")
        val matcher = pattern.matcher(text)
        var lastEnd = 0
        
        while (matcher.find()) {
            // 添加前面的普通文本
            if (matcher.start() > lastEnd) {
                append(text.substring(lastEnd, matcher.start()))
            }
            
            val word = matcher.group(1) ?: ""
            val partOfSpeech = matcher.group(2) ?: ""
            val translation = matcher.group(3) ?: ""
            val contextMeaning = matcher.group(4) ?: ""
            
            // 添加加粗的单词
            withStyle(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            ) {
                append(word)
            }
            
            // 添加词性（小字体，紫色）
            withStyle(
                style = SpanStyle(
                    fontSize = 12.sp,
                    color = Color(0xFF9C27B0)
                )
            ) {
                append(" [$partOfSpeech]")
            }
            
            // 添加翻译（绿色）
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF388E3C)
                )
            ) {
                append(" ($translation)")
            }
            
            // 添加上下文释义（斜体，灰色）
            withStyle(
                style = SpanStyle(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = Color(0xFF757575),
                    fontSize = 14.sp
                )
            ) {
                append(" 💡$contextMeaning")
            }
            
            lastEnd = matcher.end()
        }
        
        // 添加剩余的文本
        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

// 暂时简化其他屏幕

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(viewModel: VocabularyViewModel) {
    val story by viewModel.currentStory.collectAsState()
    val wordDefinitions by viewModel.wordDefinitions.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("故事") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateToScreen(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "背单词故事",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = parseRichText(story),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
            
            if (wordDefinitions.isNotEmpty()) {
                item {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "📝 单词列表",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            wordDefinitions.forEach { wordDef ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = wordDef.word,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            
                                            Text(
                                                text = wordDef.partOfSpeech,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Text(
                                            text = wordDef.translation,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        
                                        wordDef.contextMeaning?.let { context ->
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "💡 $context",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    Button(
                        onClick = { viewModel.startQuiz() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("🧠 开始测验")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(viewModel: VocabularyViewModel) {
    val quizQuestions by viewModel.quizQuestions.collectAsState()
    val currentQuizIndex by viewModel.currentQuizIndex.collectAsState()
    val quizScore by viewModel.quizScore.collectAsState()
    
    if (quizQuestions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("测验题目加载中...")
        }
        return
    }
    
    val currentQuestion = quizQuestions[currentQuizIndex]
    val progress = (currentQuizIndex + 1).toFloat() / quizQuestions.size
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("🧠 测验 (${currentQuizIndex + 1}/${quizQuestions.size})") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateToScreen(Screen.Story) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 进度条
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "当前得分: $quizScore/${currentQuizIndex}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Card {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    currentQuestion.options.forEachIndexed { index, option ->
                        OutlinedButton(
                            onClick = { viewModel.submitAnswer(index) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "${('A' + index)}.  $option",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(viewModel: VocabularyViewModel) {
    val quizQuestions by viewModel.quizQuestions.collectAsState()
    val quizScore by viewModel.quizScore.collectAsState()
    val wrongAnswers by viewModel.wrongAnswers.collectAsState()
    
    if (quizQuestions.isEmpty()) return
    
    val totalQuestions = quizQuestions.size
    val percentage = (quizScore.toDouble() / totalQuestions * 100).toInt()
    val result = QuizResult(totalQuestions, quizScore, percentage.toDouble())
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("🎯 测验结果") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateToScreen(Screen.Home) }) {
                    Icon(Icons.Default.Home, contentDescription = "回到首页")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "测验完成！",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            percentage >= 90 -> Color(0xFF4CAF50)
                            percentage >= 70 -> Color(0xFFFF9800)
                            else -> Color(0xFFf44336)
                        }
                    )
                    
                    Text(
                        text = "$quizScore / $totalQuestions 题正确",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = result.evaluation,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 如果有错题，显示错题本按钮
            if (wrongAnswers.isNotEmpty()) {
                Button(
                    onClick = { viewModel.navigateToScreen(Screen.WrongAnswers) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B6B)
                    )
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📝 查看错题本 (${wrongAnswers.size}个错题)")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.navigateToScreen(Screen.Story) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新学习")
                }
                
                Button(
                    onClick = { viewModel.navigateToScreen(Screen.Home) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("回到首页")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongAnswersScreen(viewModel: VocabularyViewModel) {
    val wrongAnswers by viewModel.wrongAnswers.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("❌ 错题本") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateToScreen(Screen.Result) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )
        
        if (wrongAnswers.isEmpty()) {
            // 没有错题时的显示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🎉",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "恭喜！没有错题！",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "你已经完全掌握了所有单词！",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 有错题时的显示
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "📊 错题统计",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "共答错 ${wrongAnswers.size} 个单词",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                wrongAnswers.forEach { wrongAnswer ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF3E0)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = wrongAnswer.word,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color(0xFFD84315)
                                    )
                                    
                                    Text(
                                        text = wrongAnswer.partOfSpeech,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "你的答案：${wrongAnswer.userAnswer}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Red
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Green,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "正确答案：${wrongAnswer.correctTranslation}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Green
                                    )
                                }
                                
                                wrongAnswer.contextMeaning?.let { context ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "💡 $context",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NekoMemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2E86AB),
            secondary = Color(0xFFA23B72),
            tertiary = Color(0xFFF18F01)
        ),
        content = content
    )
}