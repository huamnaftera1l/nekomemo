package com.example.nekomemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nekomemo.ui.screens.SettingsScreen
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.*

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
                title = { Text("猫猫背单词 Beta v0.1") },
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
                Screen.StoryHistory -> StoryHistoryScreen(viewModel)
                Screen.About -> AboutScreen(viewModel)
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
            
            // Token使用情况弹窗
            uiState.showTokenUsage?.let { tokenUsage ->
                TokenUsageDialog(
                    tokenUsage = tokenUsage,
                    onDismiss = { viewModel.hideTokenUsage() }
                )
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: VocabularyViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var wordInput by remember { mutableStateOf("") }
    
    // 当uiState.userInputWords改变时，更新本地状态
    LaunchedEffect(uiState.userInputWords) {
        if (wordInput.isEmpty() || wordInput == "abandon\nfragile\ncompel\ndeceive\nobscure\npledge\nweary\nvivid\nprevail\nembrace") {
            wordInput = uiState.userInputWords
        }
    }

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
                    onValueChange = { 
                        wordInput = it
                        viewModel.updateUserInputWords(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { Text("输入单词...") }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            wordInput = ""
                            viewModel.clearUserInputWords()
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "清空",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清空", style = MaterialTheme.typography.bodySmall)
                    }
                }
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
            onClick = { viewModel.loadDemoStory() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("使用Demo故事")
        }
        
        OutlinedButton(
            onClick = { viewModel.navigateToScreen(Screen.StoryHistory) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.List, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("📚 故事历史")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFFF3E0)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "⚠️ 免责声明",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF8F00)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本应用生成的内容由AI自动生成，不代表开发者立场，开发者不对其准确性和适用性负责。" +
                            "请勿输入涉及种族歧视、恐怖主义、政治敏感等词语，否则可能导致生成失败或程序异常。\n" +
                            "因用户不当使用本应用所造成的任何后果，由用户自行承担全部法律责任，开发者不对此承担任何形式的法律或连带责任。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                    lineHeight = 19.sp
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryHistoryScreen(viewModel: VocabularyViewModel) {
    val savedStories by viewModel.savedStories.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("📚 故事历史") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateToScreen(Screen.Home) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            }
        )
        
        if (savedStories.isEmpty()) {
            // 没有保存的故事时的显示
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "📖",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Text(
                        text = "还没有保存的故事",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "生成新故事后会自动保存到这里",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // 有保存的故事时的显示
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "📊 故事统计",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "共保存了 ${savedStories.size} 个故事",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                savedStories.forEach { story ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF8E1)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = story.title,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "主题: ${story.theme}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF666666)
                                        )
                                        Text(
                                            text = "AI: ${story.llmProvider}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF666666)
                                        )
                                        Text(
                                            text = formatDate(story.createdAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF666666)
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { viewModel.deleteStory(story.id) }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = Color.Red
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = story.content.take(100) + if (story.content.length > 100) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF666666),
                                    maxLines = 2
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Button(
                                    onClick = { viewModel.loadStory(story) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("查看故事")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: VocabularyViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("关于猫猫背单词 Beta v0.1") },
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateToScreen(Screen.Settings) }) {
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
                            text = "App Intro",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "「猫猫背单词」是我的Android开发练手之作，受我的女友的启发。通过AI生成有趣的故事来帮助记忆单词，让学习更加生动有趣！",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            /*
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "✨ 主要功能",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val features = listOf(
                            "📚 AI智能故事生成",
                            "🎯 个性化单词测验",
                            "📝 错题本复习系统",
                            "📖 故事历史记录",
                            "🔧 多种AI模型支持",
                            "💾 数据安全加密存储"
                        )
                        
                        features.forEach { feature ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• $feature",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

             */
            
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "打赏",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "如果你喜欢「猫猫背单词」，可以请我喝一杯蜜雪冰城吗？Thx!",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 打赏按钮
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.showQRCode(QRCodeType.WECHAT) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("微信", style = MaterialTheme.typography.bodySmall)
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.showQRCode(QRCodeType.ZELLE) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Zelle", style = MaterialTheme.typography.bodySmall)
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.showQRCode(QRCodeType.PAYPAL) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PayPal", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "点击上方按钮查看对应的打赏二维码",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF999999),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "📧 联系开发者",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Email: liu.zl_6@outlook.com" +
                                    "\n" +
                                    "GitHub: huamnaftera1l",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "感谢您使用NekoMemo！🐱❤️",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // 二维码弹窗
    uiState.showQRCode?.let { qrType ->
        QRCodeDialog(
            qrType = qrType,
            onDismiss = { viewModel.hideQRCode() }
        )
    }
}

@Composable
fun TokenUsageDialog(
    tokenUsage: TokenUsage,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📊 Token 使用情况",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Token使用统计
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF3F4F6)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        TokenUsageRow("输入 Tokens", tokenUsage.promptTokens, Color(0xFF10B981))
                        Spacer(modifier = Modifier.height(8.dp))
                        TokenUsageRow("输出 Tokens", tokenUsage.completionTokens, Color(0xFF3B82F6))
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        TokenUsageRow("总计 Tokens", tokenUsage.totalTokens, Color(0xFF6366F1), isTotal = true)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "故事生成完成！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("好的")
                }
            }
        }
    }
}

@Composable
fun TokenUsageRow(
    label: String,
    value: Int,
    color: Color,
    isTotal: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (isTotal) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value.toString(),
            style = if (isTotal) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun QRCodeDialog(
    qrType: QRCodeType,
    onDismiss: () -> Unit
) {
    val (title, drawableRes) = when (qrType) {
        QRCodeType.WECHAT -> "微信支付" to R.drawable.wechat
        QRCodeType.ZELLE -> "Zelle转账" to R.drawable.zelle
        QRCodeType.PAYPAL -> "PayPal支付" to R.drawable.paypal
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Image(
                    painter = painterResource(id = drawableRes),
                    contentDescription = "$title 二维码",
                    modifier = Modifier
                        .size(250.dp)
                        .background(
                            Color.White,
                            RoundedCornerShape(8.dp)
                        ),
                    contentScale = ContentScale.Fit
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "扫描二维码进行打赏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "感谢您的支持！🙏",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center
                )
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