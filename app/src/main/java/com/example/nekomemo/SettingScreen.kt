package com.example.nekomemo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VocabularyViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(uiState.apiKey) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("⚙️ 设置") },
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
                        text = "🔑 API Key配置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 🔐 安全的API Key输入框
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("OpenAI API密钥(暂时只支持这个，日后可能更新其他LLM的支持)") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.Lock else Icons.Default.Lock,
                                    contentDescription = if (showApiKey) "隐藏" else "显示"
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        Button(
                            onClick = { viewModel.updateApiKey(apiKeyInput) }
                        ) {
                            Text("💾 保存")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                apiKeyInput = ""
                                viewModel.updateApiKey("")
                            }
                        ) {
                            Text("🗑️ 清除")
                        }
                    }

                    // API Key状态指示
                    if (uiState.apiKey.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.Green
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("API密钥被猫猫妥善保管啦！", color = androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }
        }
        }
    }
}