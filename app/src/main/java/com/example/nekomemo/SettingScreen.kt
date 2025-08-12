package com.example.nekomemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nekomemo.*

/**
 * 全局「设置」界面。
 * 依赖 VocabularyViewModel 中的状态与更新函数，不再声明 ViewModel / 枚举等重复实体。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VocabularyViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    var providerMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ 设置") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateToScreen(Screen.Home) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            /* === API‑Key === */
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::updateApiKey,
                label = { Text("API Key") },
                placeholder = { Text("sk-…") },
                visualTransformation = if (uiState.apiKey.isEmpty())
                    VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            /* === LLM Provider === */
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = !providerMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.llmProvider.displayName,
                    onValueChange = { /* read‑only */ },
                    label = { Text("模型供应商") },
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    LLMProvider.values().forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                viewModel.updateLLMProvider(provider)
                                providerMenuExpanded = false
                            }
                        )
                    }
                }

            }


            /* === Story Theme === */
            OutlinedTextField(
                value = uiState.storyTheme,
                onValueChange = viewModel::updateStoryTheme,
                label = { Text("故事主题（英文）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            /* === Story Length === */
            OutlinedTextField(
                value = uiState.storyLength.toString(),
                onValueChange = { s ->
                    s.toIntOrNull()?.let(viewModel::updateStoryLength)
                },
                label = { Text("故事长度（词数）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            val connState by viewModel.connState.collectAsState()

            OutlinedButton(
                onClick = { viewModel.checkConnection() },
                enabled = connState != ConnState.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (connState == ConnState.Checking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("测试中…")
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("测试连接")
                }
            }
            when (connState) {
                ConnState.Success -> Text("✅ 连接成功")
                ConnState.Fail    -> Text("❌ 连接失败", color = MaterialTheme.colorScheme.error)
                else -> {}
            }


            /* === About button === */
            OutlinedButton(
                onClick = { viewModel.navigateToScreen(Screen.About) },
                modifier = Modifier.fillMaxWidth()
            ) {
//                Text("💝", style = MaterialTheme.typography.bodyLarge)
//                Spacer(Modifier.width(8.dp))
                Text("关于猫猫背单词")
            }

            /* === Save button === */
            Button(
                onClick = { viewModel.navigateToScreen(Screen.Home) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存并返回")
            }
        }
    }
}
