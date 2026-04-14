/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/config/types.openclaw.ts (agents.list schema)
 *
 * AndroidForClaw: 独立 Agent 配置页面（对齐 OpenClaw agents.list）
 */
package com.xiaomo.androidforclaw.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaomo.androidforclaw.config.*

class AgentConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AgentConfigScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val loader = remember { ConfigLoader.getInstance() }

    var agents by remember { mutableStateOf<List<AgentEntry>>(emptyList()) }
    var defaultsModel by remember { mutableStateOf<String?>(null) }
    var editingIndex by remember { mutableStateOf<Int?>(null) } // null=new, >=0=edit
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }

    // Load agents from config
    LaunchedEffect(Unit) {
        val config = loader.loadOpenClawConfig()
        agents = config.agents?.list ?: emptyList()
        defaultsModel = config.agents?.defaults?.model?.primary
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent 配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { editingIndex = -1 }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 说明 ──────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                tonalElevation = 0.dp
            ) {
                Text(
                    text = "每个 Agent 独立运行，拥有自己的 workspace、模型和配置。" +
                        "对应 openclaw.json 的 agents.list 字段。",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // ── 默认模型 ──────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "默认模型",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        defaultsModel ?: "未配置（使用 provider 默认模型）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Agent 列表 ────────────────────────────────────
            if (agents.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "暂无独立 Agent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点击右上角「添加」创建新 Agent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            } else {
                agents.forEachIndexed { index, agent ->
                    AgentCard(
                        agent = agent,
                        onEdit = { editingIndex = index },
                        onDelete = { showDeleteConfirm = index }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── 编辑/新建对话框 ──────────────────────────────────────
    if (editingIndex != null) {
        val is新建 = editingIndex == -1
        val existing = if (!is新建) agents[editingIndex!!] else null

        AgentEditDialog(
            existing = existing,
            onDismiss = { editingIndex = null },
            onSave = { id, name, model, agentDir ->
                val newEntry = AgentEntry(
                    id = id,
                    name = name,
                    model = if (model.isNotBlank()) ModelSelectionConfig(primary = model) else null,
                    agentDir = agentDir.ifBlank { null }
                )
                agents = if (is新建) {
                    agents + newEntry
                } else {
                    agents.toMutableList().apply { set(editingIndex!!, newEntry) }
                }
                saveAgents(loader, agents)
                editingIndex = null
                Toast.makeText(context, if (is新建) "已添加" else "已更新", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ── 删除确认 ──────────────────────────────────────────────
    if (showDeleteConfirm != null) {
        val agent = agents[showDeleteConfirm!!]
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除 Agent") },
            text = { Text("确定删除「${agent.name}」(${agent.id})？\n此操作会从 openclaw.json 中移除该 agent 配置。") },
            confirmButton = {
                TextButton(onClick = {
                    agents = agents.toMutableList().apply { removeAt(showDeleteConfirm!!) }
                    saveAgents(loader, agents)
                    showDeleteConfirm = null
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AgentCard(
    agent: AgentEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        agent.name.ifBlank { agent.id },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "ID: ${agent.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Edit, "编辑", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 详情
            agent.model?.primary?.let { model ->
                DetailRow("模型", model)
            }
            agent.agentDir?.let { dir ->
                DetailRow("Workspace", dir)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AgentEditDialog(
    existing: AgentEntry?,
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, model: String, agentDir: String) -> Unit
) {
    var id by remember { mutableStateOf(existing?.id ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var model by remember { mutableStateOf(existing?.model?.primary ?: "") }
    var agentDir by remember { mutableStateOf(existing?.agentDir ?: "") }
    val isNew = existing == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加 Agent" else "编辑 Agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Agent ID *") },
                    placeholder = { Text("e.g. coder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isNew // ID 不可修改
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称 *") },
                    placeholder = { Text("e.g. 代码助手") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型") },
                    placeholder = { Text("e.g. openrouter/claude-sonnet-4 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = agentDir,
                    onValueChange = { agentDir = it },
                    label = { Text("Workspace 目录") },
                    placeholder = { Text("e.g. /sdcard/.androidforclaw/agents/coder (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isNew) {
                    Text(
                        "创建后会在对应目录生成 SOUL.md / AGENTS.md 等文件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (id.isNotBlank() && name.isNotBlank()) {
                        onSave(id.trim(), name.trim(), model.trim(), agentDir.trim())
                    }
                },
                enabled = id.isNotBlank() && name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun saveAgents(loader: ConfigLoader, agents: List<AgentEntry>) {
    val config = loader.loadOpenClawConfig()
    val newAgentsConfig = AgentsConfig(
        list = agents.ifEmpty { null },
        defaults = config.agents?.defaults ?: AgentDefaultsConfig()
    )
    val newConfig = config.copy(agents = newAgentsConfig)
    loader.saveOpenClawConfig(newConfig)
}
