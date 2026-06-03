package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryInfoDialog(
        memory: Memory,
        onDismiss: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit,
        onTogglePersistent: (Boolean) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val tagNames = memory.tags.map { it.name }
    val isPersistent = tagNames.contains("#persistent_instruction")

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.memory_details_title)) },
            text = {
                Column(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${stringResource(R.string.memory_title)}: ${memory.title}", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    Text(stringResource(R.string.memory_content) + ":", style = MaterialTheme.typography.titleSmall)
                    Text(memory.content)
                    HorizontalDivider()
                    if (tagNames.isNotEmpty()) {
                        Text(
                                "Tags: ${tagNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPersistent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                        )
                        if (isPersistent) {
                            Text(
                                    "⭐ Persistent instruction — injected into every system prompt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // R-UI-002 — 手动 toggle 持久化指令开关
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                                stringResource(R.string.memory_persistent_instruction_toggle),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                                checked = isPersistent,
                                onCheckedChange = onTogglePersistent
                        )
                    }
                    Text("${stringResource(R.string.memory_folder)}: ${memory.folderPath?.ifEmpty { stringResource(R.string.memory_uncategorized) }}", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.memory_uuid)}: ${memory.uuid}", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.memory_source)}: ${memory.source}", style = MaterialTheme.typography.bodySmall)
                    Text(
                            "${stringResource(R.string.memory_importance)}: ${String.format("%.2f", memory.importance)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "${stringResource(R.string.memory_credibility)}: ${String.format("%.2f", memory.credibility)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "${stringResource(R.string.memory_created_at)}: ${dateFormat.format(memory.createdAt)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                            "${stringResource(R.string.memory_updated_at)}: ${dateFormat.format(memory.updatedAt)}",
                            style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onEdit) { Text(stringResource(R.string.memory_edit)) }
                    Button(
                            onClick = onDelete,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text(stringResource(R.string.memory_delete)) }
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_close)) }
                }
            }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgeInfoDialog(
    edge: Edge,
    graph: com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceNode = graph.nodes.find { it.id == edge.sourceId }
    val targetNode = graph.nodes.find { it.id == edge.targetId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_link_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.memory_from)}: ${sourceNode?.label ?: stringResource(R.string.memory_uncategorized)}")
                Text("${stringResource(R.string.memory_to)}: ${targetNode?.label ?: stringResource(R.string.memory_uncategorized)}")
                HorizontalDivider()
                Text("${stringResource(R.string.memory_type)}: ${edge.label}")
                Text("${stringResource(R.string.memory_weight)}: ${edge.weight}")
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = onEdit) { Text(stringResource(R.string.memory_edit)) }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.memory_delete)) }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_close)) }
            }
        }
    )
}

@Composable
fun EditEdgeDialog(
    edge: Edge,
    onDismiss: () -> Unit,
    onSave: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf(edge.label ?: "related") }
    var weight by remember { mutableStateOf(edge.weight.toString()) }
    var description by remember { mutableStateOf("") } // 假设需要编辑description

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_edit_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text(stringResource(R.string.memory_type)) })
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(stringResource(R.string.memory_weight)) })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.memory_description)) })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(type, weight.toFloatOrNull() ?: 1.0f, description)
            }) { Text(stringResource(R.string.memory_save)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) } }
    )
}

@Composable
fun LinkMemoryDialog(
    sourceNodeLabel: String,
    targetNodeLabel: String,
    onDismiss: () -> Unit,
    onLink: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf("related") }
    var weight by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_link_nodes, sourceNodeLabel, targetNodeLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.memory_type)) }
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text(stringResource(R.string.memory_weight)) }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.memory_description)) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 1.0f
                    onLink(type, w, description)
                }
            ) { Text(stringResource(R.string.memory_create_link)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) } }
    )
}

@Composable
fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.memory_delete_confirmation, selectedCount),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.memory_delete_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.confirm_delete))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * R-AGENT-003 后续：手动重复清理弹窗。
 *
 * 列出 [groups] 重复组，每条 memory 一个 checkbox：
 *  - 每组首条**强制保留**（checkbox disabled）
 *  - 其余默认勾选
 *  - 用户可改勾选
 *  - 底部「删除选中 N 条」走二次确认（[BatchDeleteConfirmDialog] 风格）
 *
 * 不做"合并 content"——只删，避免破坏性误操作。
 */
@Composable
fun DedupCleanupDialog(
    isScanning: Boolean,
    isDeleting: Boolean,
    groups: List<List<Memory>>,
    lastDeletedCount: Int,
    onDismiss: () -> Unit,
    onDelete: (List<Long>) -> Unit,
) {
    // 默认勾选：每组首条 false，其余 true
    val checkedIds = remember(groups) {
        val s = mutableStateOf<Set<Long>>(
            groups.flatMap { g -> g.drop(1).map { it.id } }.toSet()
        )
        s
    }
    var showConfirm by remember { mutableStateOf(false) }
    val selectedIds: List<Long> = checkedIds.value.toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    isScanning -> stringResource(R.string.memory_dedup_scanning)
                    groups.isEmpty() && lastDeletedCount > 0 ->
                        stringResource(R.string.memory_dedup_done_with_count, lastDeletedCount)
                    groups.isEmpty() -> stringResource(R.string.memory_dedup_no_duplicates)
                    else -> stringResource(R.string.memory_dedup_found_groups, groups.size)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (lastDeletedCount > 0 && groups.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.memory_dedup_last_round_deleted, lastDeletedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (isScanning) {
                    Text(
                        text = stringResource(R.string.memory_dedup_scanning_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (groups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.memory_dedup_no_duplicates_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.memory_dedup_keep_first_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    groups.forEachIndexed { gi, group ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.memory_dedup_group_header, gi + 1, group.size),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            group.forEachIndexed { mi, mem ->
                                val isFirst = mi == 0
                                val checked = if (isFirst) false else mem.id in checkedIds.value
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = checked,
                                        enabled = !isFirst && !isDeleting,
                                        onCheckedChange = { now ->
                                            val cur = checkedIds.value.toMutableSet()
                                            if (now) cur.add(mem.id) else cur.remove(mem.id)
                                            checkedIds.value = cur
                                        },
                                    )
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        val titleLabel = if (isFirst) {
                                            stringResource(R.string.memory_dedup_keep_label, mem.title.ifBlank { "(no title)" })
                                        } else {
                                            mem.title.ifBlank { "(no title)" }
                                        }
                                        Text(text = titleLabel, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = mem.content.take(80),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (selectedIds.isNotEmpty()) showConfirm = true },
                enabled = !isScanning && !isDeleting && selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.memory_dedup_delete_n, selectedIds.size))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isDeleting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (showConfirm) {
        BatchDeleteConfirmDialog(
            selectedCount = selectedIds.size,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                onDelete(selectedIds)
            },
        )
    }
}
