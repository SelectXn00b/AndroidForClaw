package com.xiaomo.hermes.ui.activity

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomo.hermes.R
import com.xiaomo.hermes.config.*
import kotlinx.coroutines.launch

/**
 * 飞书 Channel 配置页面 — 只配置 bot 连接凭据（appId/appSecret）和消息策略
 * Agent 级别的配置（API Key/model）由 AgentConfigActivity 处理
 */
class FeishuChannelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MaterialTheme {
                FeishuChannelScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeishuChannelScreen(
    onBack: () -> Unit,
    context: android.content.Context = androidx.compose.ui.platform.LocalContext.current
) {
    val scope = rememberCoroutineScope()
    val configLoader = remember { ConfigLoader(context) }

    val openClawConfig = remember { configLoader.loadOpenClawConfig() }
    val savedConfig = remember { openClawConfig.channels.feishu }

    var enabled by remember { mutableStateOf(savedConfig.enabled) }
    var appId by remember { mutableStateOf(savedConfig.appId) }
    var appSecret by remember { mutableStateOf(savedConfig.appSecret) }
    var dmPolicy by remember { mutableStateOf(savedConfig.dmPolicy) }
    var groupPolicy by remember { mutableStateOf(savedConfig.groupPolicy) }
    var requireMention by remember { mutableStateOf(savedConfig.requireMention ?: savedConfig.groupPolicy != "open") }

    var showSaveSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feishu_channel_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val result = runCatching {
                                    val config = configLoader.loadOpenClawConfig()
                                    val updatedFeishu = config.channels.feishu.copy(
                                        enabled = enabled,
                                        appId = appId,
                                        appSecret = appSecret,
                                        dmPolicy = dmPolicy,
                                        groupPolicy = groupPolicy,
                                        requireMention = requireMention
                                    )
                                    val newConfig = config.copy(
                                        channels = config.channels.copy(feishu = updatedFeishu)
                                    )
                                    configLoader.saveOpenClawConfig(newConfig)
                                }
                                if (result.isSuccess) {
                                    showSaveSuccess = true
                                    showError = null
                                } else {
                                    showError = "保存失败: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            )
        },
        snackbarHost = {
            if (showSaveSuccess) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showSaveSuccess = false
                }
                Snackbar(modifier = Modifier.padding(16.dp)) {
                    Text("配置已保存，重启 Gateway 生效")
                }
            }
            showError?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { showError = null }) { Text("关闭") } }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ===== 启用开关 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用 Feishu Channel", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后将接收飞书消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            // ===== Bot 连接凭据 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Bot 连接凭据", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "在飞书开放平台创建应用获取 App ID 和 App Secret",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = appId,
                        onValueChange = { appId = it },
                        label = { Text("App ID") },
                        placeholder = { Text("cli_xxxxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = appSecret,
                        onValueChange = { appSecret = it },
                        label = { Text("App Secret") },
                        placeholder = { Text("输入 App Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // ===== 消息策略 =====
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("消息策略", style = MaterialTheme.typography.titleMedium)

                    Text("私聊策略", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("open", "pairing", "allowlist").forEach { policy ->
                            FilterChip(
                                selected = dmPolicy == policy,
                                onClick = { dmPolicy = policy },
                                label = { Text(policy) }
                            )
                        }
                    }

                    Text("群聊策略", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("open", "allowlist", "disabled").forEach { policy ->
                            FilterChip(
                                selected = groupPolicy == policy,
                                onClick = { groupPolicy = policy },
                                label = { Text(policy) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("群聊需 @机器人", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "开启后只有 @机器人 才会触发回复",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = requireMention,
                            onCheckedChange = { requireMention = it }
                        )
                    }
                }
            }

            // 配置文件路径提示
            Text(
                text = "配置保存在 openclaw.json → channels.feishu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
