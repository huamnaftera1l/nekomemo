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
                title = { Text("🐱 NekoMemo - 智能背单词") },
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
            Text("🚀 生成故事")
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
            Text("📖 使用演示故事")
        }
    }
}

// 暂时简化其他屏幕

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(viewModel: VocabularyViewModel) {
    val story by viewModel.currentStory.collectAsState()
    val wordTranslations by viewModel.wordTranslations.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("📖 故事") },
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
                            text = "📚 背单词故事",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = story,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
            
            if (wordTranslations.isNotEmpty()) {
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
                            
                            wordTranslations.forEach { (word, translation) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = word,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = translation,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End
                                    )
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