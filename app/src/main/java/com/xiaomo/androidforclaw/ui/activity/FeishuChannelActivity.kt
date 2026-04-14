/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.activity

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomo.androidforclaw.R
import com.xiaomo.androidforclaw.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bot 账号 UI 状态
 */
private data class BotAccountUi(
    val key: String,
    val name: String,
    val appId: String,
    val appSecret: String,
    val enabled: Boolean,
    val expanded: Boolean = true,
    val providerId: String = "",
    val modelId: String = "",
    val apiKey: String = "",
    val baseUrl: String = ""
)

/**
 * 飞书 Channel 配置页面 — 支持多 Bot 账号管理
 *
 * 对齐 OpenClaw channels.feishu.accounts + agents.list + bindings
 */
class FeishuChannelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 禁止截屏
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

    // 加载配置
    val openClawConfig = remember { configLoader.loadOpenClawConfig() }
    val savedConfig = remember { openClawConfig.channels.feishu }

    // ===== 基础状态 =====
    var enabled by remember { mutableStateOf(savedConfig.enabled) }
    var dmPolicy by remember { mutableStateOf(savedConfig.dmPolicy) }
    var groupPolicy by remember { mutableStateOf(savedConfig.groupPolicy) }
    var requireMention by remember { mutableStateOf(savedConfig.requireMention ?: savedConfig.groupPolicy != "open") }

    // ===== 多 Bot 账号状态 =====
    val initialAccounts: List<BotAccountUi> = remember {
        val accounts = savedConfig.accounts
        if (!accounts.isNullOrEmpty()) {
            accounts.map { (key, acc) ->
                val binding = openClawConfig.bindings.find { b -> b.match.accountId == key }
                val agentId = binding?.agentId ?: ""
                val agentEntry = openClawConfig.agents?.list?.find { e -> e.id == agentId }
                val modelRef = agentEntry?.model?.primary ?: ""
                val parts = modelRef.split("/", limit = 2)
                val providerIdRaw = parts.getOrElse(0) { "" }

                // 检查是否有独立 provider（格式：baseProvider_botKey）
                var baseProviderId = providerIdRaw
                var botApiKey = ""
                var botBaseUrl = ""
                val providerConfig = openClawConfig.models?.providers?.get(providerIdRaw)
                if (providerConfig != null && providerIdRaw.endsWith("_$key")) {
                    baseProviderId = providerIdRaw.removeSuffix("_$key")
                    val baseProvider = openClawConfig.models?.providers?.get(baseProviderId)
                    // 只读取与全局 provider 不同的值
                    if (providerConfig.apiKey != baseProvider?.apiKey) {
                        botApiKey = providerConfig.apiKey ?: ""
                    }
                    if (providerConfig.baseUrl != baseProvider?.baseUrl) {
                        botBaseUrl = providerConfig.baseUrl
                    }
                }

                BotAccountUi(
                    key = key,
                    name = acc.name ?: key,
                    appId = acc.appId ?: "",
                    appSecret = acc.appSecret ?: "",
                    enabled = acc.enabled,
                    expanded = false,
                    providerId = baseProviderId,
                    modelId = parts.getOrElse(1) { "" },
                    apiKey = botApiKey,
                    baseUrl = botBaseUrl
                )
            }
        } else if (savedConfig.appId.isNotBlank()) {
            listOf(
                BotAccountUi(
                    key = "default_account",
                    name = "默认 Bot",
                    appId = savedConfig.appId,
                    appSecret = savedConfig.appSecret,
                    enabled = true,
                    expanded = false
                )
            )
        } else {
            listOf(
                BotAccountUi(
                    key = "bot_1",
                    name = "Bot 1",
                    appId = "",
                    appSecret = "",
                    enabled = true,
                    expanded = true
                )
            )
        }
    }

    var accountList by remember { mutableStateOf(initialAccounts) }
    var showSaveSuccess by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }

    // 可选的 provider 列表（用于 model 下拉）
    val providers: List<Pair<String, List<String>>> = remember {
        openClawConfig.resolveProviders().map { (name, provider) ->
            name to provider.models.map { it.id }
        }
    }

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
                                val result = saveFeishuConfig(
                                    configLoader = configLoader,
                                    currentConfig = configLoader.loadOpenClawConfig(),
                                    accountList = accountList,
                                    enabled = enabled,
                                    dmPolicy = dmPolicy,
                                    groupPolicy = groupPolicy,
                                    requireMention = requireMention
                                )
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            // ===== 全局策略 =====
            Text("全局策略", style = MaterialTheme.typography.titleMedium)

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

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("群聊需要 @ 提及", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "开启后仅响应 @ 机器人的消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = requireMention, onCheckedChange = { requireMention = it })
                }
            }

            HorizontalDivider()

            // ===== 多 Bot 账号管理 =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bot 账号", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = {
                    val newKey = "bot_${System.currentTimeMillis() / 1000}"
                    accountList = accountList + BotAccountUi(
                        key = newKey,
                        name = "Bot ${accountList.size + 1}",
                        appId = "",
                        appSecret = "",
                        enabled = true,
                        expanded = true
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加 Bot")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加 Bot")
                }
            }

            if (accountList.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "还没有 Bot 账号，点击上方「添加 Bot」开始",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            accountList.forEachIndexed { index, account ->
                BotAccountCard(
                    account = account,
                    providers = providers,
                    onToggleExpand = {
                        accountList = accountList.toMutableList().apply {
                            this[index] = account.copy(expanded = !account.expanded)
                        }
                    },
                    onNameChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(name = it) } },
                    onAppIdChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(appId = it) } },
                    onAppSecretChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(appSecret = it) } },
                    onApiKeyChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(apiKey = it) } },
                    onBaseUrlChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(baseUrl = it) } },
                    onEnabledChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(enabled = it) } },
                    onProviderChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(providerId = it) } },
                    onModelChange = { accountList = accountList.toMutableList().apply { this[index] = account.copy(modelId = it) } },
                    onDelete = {
                        accountList = accountList.filterIndexed { i, _ -> i != index }
                    }
                )
            }

            // 配置文件路径提示
            Text(
                text = "配置保存在 openclaw.json 的 channels.feishu.accounts + agents.list + bindings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 保存飞书多 Bot 配置到 openclaw.json
 */
private fun saveFeishuConfig(
    configLoader: ConfigLoader,
    currentConfig: OpenClawConfig,
    accountList: List<BotAccountUi>,
    enabled: Boolean,
    dmPolicy: String,
    groupPolicy: String,
    requireMention: Boolean
): Result<Unit> = runCatching {
    // 构建 accounts map
    val accountsMap = mutableMapOf<String, FeishuAccountConfig>()
    val newBindings = mutableListOf<BindingEntry>()
    val newAgentEntries = mutableListOf<AgentEntry>()
    // 有独立 API key 的 bot 需要创建独立 provider
    val newProviders = mutableMapOf<String, ProviderConfig>()

    accountList.forEach { acc ->
        if (acc.appId.isBlank()) return@forEach
        accountsMap[acc.key] = FeishuAccountConfig(
            enabled = acc.enabled,
            name = acc.name,
            appId = acc.appId,
            appSecret = acc.appSecret
        )

        // 为每个有 model 的账号创建 agent + binding
        if (acc.providerId.isNotBlank() && acc.modelId.isNotBlank()) {
            val agentId = acc.key.replace("_account", "")

            // 确定 provider ID：有独立配置则创建副本 provider
            var effectiveProviderId = acc.providerId
            if (acc.apiKey.isNotBlank() || acc.baseUrl.isNotBlank()) {
                effectiveProviderId = "${acc.providerId}_${acc.key}"
                val baseProvider = currentConfig.models?.providers?.get(acc.providerId)
                if (baseProvider != null) {
                    newProviders[effectiveProviderId] = baseProvider.copy(
                        apiKey = if (acc.apiKey.isNotBlank()) acc.apiKey else baseProvider.apiKey,
                        baseUrl = if (acc.baseUrl.isNotBlank()) acc.baseUrl else baseProvider.baseUrl
                    )
                } else {
                    // 基础 provider 不存在（可能是手动输入），创建最小配置
                    newProviders[effectiveProviderId] = ProviderConfig(
                        baseUrl = acc.baseUrl,
                        apiKey = acc.apiKey,
                        api = "openai-completions"
                    )
                }
            }

            newAgentEntries.add(
                AgentEntry(
                    id = agentId,
                    name = acc.name,
                    model = ModelSelectionConfig(primary = "$effectiveProviderId/${acc.modelId}")
                )
            )
            newBindings.add(
                BindingEntry(
                    agentId = agentId,
                    match = BindingMatch(channel = "feishu", accountId = acc.key)
                )
            )
        }
    }

    // 更新 feishu 配置
    val firstAccount = accountList.firstOrNull { it.appId.isNotBlank() }
    val isMultiAccount = accountsMap.size > 1 ||
        (accountsMap.size == 1 && accountsMap.keys.first() != "default_account")

    val updatedFeishu = currentConfig.channels.feishu.copy(
        enabled = enabled,
        appId = firstAccount?.appId ?: "",
        appSecret = firstAccount?.appSecret ?: "",
        dmPolicy = dmPolicy,
        groupPolicy = groupPolicy,
        requireMention = requireMention,
        accounts = if (isMultiAccount) accountsMap else null,
        defaultAccount = if (accountsMap.size > 1) firstAccount?.key else null
    )

    // 更新 agents（合并现有 agents.list，保留非 feishu 的 agent）
    val newAgentIds = newAgentEntries.map { it.id }.toSet()
    val mergedAgentList = (newAgentEntries +
        (currentConfig.agents?.list?.filter { it.id !in newAgentIds } ?: emptyList())
    ).takeIf { it.isNotEmpty() }

    val updatedAgents = currentConfig.agents?.copy(list = mergedAgentList)
        ?: AgentsConfig(list = mergedAgentList)

    // 合并 bindings（保留非 feishu 的 binding）
    val mergedBindings = newBindings + currentConfig.bindings.filter {
        it.match.channel != "feishu"
    }

    // 合并 providers（添加有独立 API key 的 bot 的 provider）
    val updatedModels = if (newProviders.isNotEmpty()) {
        (currentConfig.models ?: ModelsConfig()).copy(
            providers = (currentConfig.models?.providers ?: emptyMap()) + newProviders
        )
    } else {
        currentConfig.models
    }

    val updatedConfig = currentConfig.copy(
        channels = currentConfig.channels.copy(feishu = updatedFeishu),
        agents = updatedAgents,
        bindings = mergedBindings,
        models = updatedModels
    )

    configLoader.saveOpenClawConfig(updatedConfig)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotAccountCard(
    account: BotAccountUi,
    providers: List<Pair<String, List<String>>>,
    onToggleExpand: () -> Unit,
    onNameChange: (String) -> Unit,
    onAppIdChange: (String) -> Unit,
    onAppSecretChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProviderChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // 动态获取的模型列表
    var fetchedModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    // 合并来源：动态获取 > 全局 Provider
    val globalModels = providers.find { it.first == account.providerId }?.second ?: emptyList()
    val displayModels = if (fetchedModels.isNotEmpty()) fetchedModels else globalModels

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (account.enabled) CardDefaults.cardColors()
        else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = account.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.size(width = 48.dp, height = 24.dp)
                    )
                    Text(
                        text = account.name.ifBlank { "未命名 Bot" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (account.appId.isNotBlank()) {
                        Text(
                            text = "(${account.appId.takeLast(8)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            if (account.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (account.expanded) "收起" else "展开"
                        )
                    }
                }
            }

            AnimatedVisibility(visible = account.expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = account.name,
                        onValueChange = onNameChange,
                        label = { Text("Bot 名称") },
                        placeholder = { Text("例如：大狗") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = account.appId,
                        onValueChange = onAppIdChange,
                        label = { Text("App ID") },
                        placeholder = { Text("cli_xxxxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = account.appSecret,
                        onValueChange = onAppSecretChange,
                        label = { Text("App Secret") },
                        placeholder = { Text("输入 App Secret") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // API Key + 获取模型按钮
                    OutlinedTextField(
                        value = account.apiKey,
                        onValueChange = {
                            onApiKeyChange(it)
                            // API Key 变了，清空已获取的模型
                            fetchedModels = emptyList()
                        },
                        label = { Text("API Key") },
                        placeholder = { Text("输入 API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Base URL + 获取模型按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = account.baseUrl,
                            onValueChange = {
                                onBaseUrlChange(it)
                                fetchedModels = emptyList()
                            },
                            label = { Text("Base URL") },
                            placeholder = { Text("例如：https://api.xxx.com/v1") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (account.baseUrl.isBlank()) {
                                    fetchError = "请先填写 Base URL"
                                    return@Button
                                }
                                if (account.apiKey.isBlank()) {
                                    fetchError = "请先填写 API Key"
                                    return@Button
                                }
                                isFetching = true
                                fetchError = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val client = OkHttpClient.Builder()
                                            .connectTimeout(10, TimeUnit.SECONDS)
                                            .readTimeout(10, TimeUnit.SECONDS)
                                            .build()
                                        val url = "${account.baseUrl.trimEnd('/')}/models"
                                        val request = Request.Builder()
                                            .url(url)
                                            .get()
                                            .addHeader("Authorization", "Bearer ${account.apiKey}")
                                            .build()
                                        val response = client.newCall(request).execute()
                                        val body = response.body?.string()
                                            ?: throw Exception("Empty response")
                                        val json = JSONObject(body)
                                        val data = json.optJSONArray("data")
                                        val models = mutableListOf<String>()
                                        if (data != null) {
                                            for (i in 0 until data.length()) {
                                                val m = data.getJSONObject(i)
                                                val id = m.optString("id", "").trim()
                                                if (id.isNotBlank()) models.add(id)
                                            }
                                        }
                                        fetchedModels = models.sorted()
                                        if (models.isEmpty()) {
                                            fetchError = "未找到模型"
                                        }
                                    } catch (e: Exception) {
                                        fetchError = "获取失败: ${e.message}"
                                    } finally {
                                        isFetching = false
                                    }
                                }
                            },
                            enabled = !isFetching,
                            modifier = Modifier.height(56.dp)
                        ) {
                            if (isFetching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("获取模型")
                            }
                        }
                    }

                    fetchError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Provider 下拉（全局 provider）
                    if (providers.isNotEmpty()) {
                        var providerExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = providerExpanded,
                            onExpandedChange = { providerExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = account.providerId.ifBlank { "选择全局 Provider（可选）" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Provider") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = providerExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = providerExpanded,
                                onDismissRequest = { providerExpanded = false }
                            ) {
                                providers.forEach { (providerName, _) ->
                                    DropdownMenuItem(
                                        text = { Text(providerName) },
                                        onClick = {
                                            onProviderChange(providerName)
                                            // 切换 provider 时清空已获取的模型
                                            fetchedModels = emptyList()
                                            providerExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Model 下拉
                    if (displayModels.isNotEmpty()) {
                        var modelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = account.modelId.ifBlank { "选择 Model" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = modelExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                displayModels.forEach { mId ->
                                    DropdownMenuItem(
                                        text = { Text(mId) },
                                        onClick = {
                                            onModelChange(mId)
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // 没有模型列表时，手动输入 model id
                        OutlinedTextField(
                            value = account.modelId,
                            onValueChange = onModelChange,
                            label = { Text("Model ID") },
                            placeholder = { Text("点击「获取模型」或手动输入") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }
    }
}
