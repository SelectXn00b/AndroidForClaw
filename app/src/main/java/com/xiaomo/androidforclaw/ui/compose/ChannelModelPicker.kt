/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * 渠道模型选择器 — 从 OpenClaw 已配置的 providers 动态读取可用模型列表。
 *
 * 该 Composable 不做任何持久化，状态由调用方管理。
 *
 * @param config  当前 OpenClawConfig，用于读取 providers
 * @param selected 当前选中的模型 ID（格式 "providerId/modelId"），null 表示使用全局默认
 * @param onSelected 用户选择后的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelModelPicker(
    config: OpenClawConfig,
    selected: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // 构建可用模型列表：null → 全局默认；其他项来自已配置 providers
    val models: List<Pair<String?, String>> = remember(config) {
        val list = mutableListOf<Pair<String?, String>>()
        list.add(null to "使用全局默认模型")
        config.resolveProviders().forEach { (providerId, providerCfg) ->
            providerCfg.models.forEach { model ->
                val key = "$providerId/${model.id}"
                val label = "$providerId / ${model.name.ifEmpty { model.id }}"
                list.add(key to label)
            }
        }
        list
    }

    var expanded by remember { mutableStateOf(false) }

    val currentLabel = models.find { it.first == selected }?.second
        ?: selected
        ?: "使用全局默认模型"

    Column(modifier = modifier) {
        Text(
            text = "模型（可选覆盖）",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                label = { Text("模型") }
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                models.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            onSelected(key)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
        if (models.size <= 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "⚠ 未配置任何 Provider，请先在「模型配置」页添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
