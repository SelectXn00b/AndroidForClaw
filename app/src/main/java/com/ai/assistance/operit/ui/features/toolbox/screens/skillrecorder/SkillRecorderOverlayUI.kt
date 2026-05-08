package com.ai.assistance.operit.ui.features.toolbox.screens.skillrecorder

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.skillrecorder.RecordingState
import com.ai.assistance.operit.services.skillrecorder.SkillRecorderOverlayManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Skill Recorder 悬浮窗内容（根据模式切换 UI）。
 */
@Composable
fun SkillRecorderOverlayContent(
    mode: SkillRecorderOverlayManager.Mode,
    recordingState: StateFlow<RecordingState>,
    stepFrameCount: StateFlow<Int>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onBallDrag: (Float, Float) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onConfirmLabel: (String) -> Unit,
    onDiscardLabel: () -> Unit,
    onStartRecording: () -> Unit = {},
    onFinishBuilding: () -> Unit = {}
) {
    when (mode) {
        SkillRecorderOverlayManager.Mode.RECORDING_BALL -> {
            RecordingBallContent(
                recordingState = recordingState,
                stepFrameCount = stepFrameCount,
                isExpanded = isExpanded,
                onToggleExpand = onToggleExpand,
                onBallDrag = onBallDrag,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                onDiscard = onDiscard
            )
        }
        SkillRecorderOverlayManager.Mode.LABELING_PANEL -> {
            LabelInputPanelContent(
                stepFrameCount = stepFrameCount,
                onConfirm = onConfirmLabel,
                onDiscard = onDiscardLabel
            )
        }
        SkillRecorderOverlayManager.Mode.BUILDING_BALL -> {
            BuildingBallContent(
                isExpanded = isExpanded,
                onToggleExpand = onToggleExpand,
                onBallDrag = onBallDrag,
                onStartRecording = onStartRecording,
                onFinishBuilding = onFinishBuilding
            )
        }
    }
}

/**
 * 录制控制球：未展开时为红色小圆球，展开时显示录制状态和控制按钮。
 */
@Composable
private fun RecordingBallContent(
    recordingState: StateFlow<RecordingState>,
    stepFrameCount: StateFlow<Int>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onBallDrag: (Float, Float) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    val state by recordingState.collectAsState()
    val frameCount by stepFrameCount.collectAsState()
    val isPaused = state == RecordingState.STEP_PAUSED

    if (!isExpanded) {
        // 未展开：红色小圆球，可拖拽，点击展开
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isPaused) Color(0xFFFF9800) else Color(0xFFE53935))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onBallDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { onToggleExpand() }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.Pause else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "$frameCount",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    } else {
        // 展开：控制面板
        Card(
            modifier = Modifier
                .width(220.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onBallDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 状态行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isPaused) Color(0xFFFF9800) else Color(0xFFE53935))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPaused) "已暂停" else "录制中",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$frameCount 帧",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 控制按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 暂停/继续
                    IconButton(onClick = { if (isPaused) onResume() else onPause() }) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "继续" else "暂停",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // 停止
                    IconButton(onClick = onStop) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "停止",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    // 丢弃
                    IconButton(onClick = onDiscard) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "丢弃",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 收起按钮
                TextButton(onClick = onToggleExpand) {
                    Text("收起", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * 描述输入面板：底部卡片，包含帧数提示 + 输入框 + 确认/丢弃按钮。
 */
@Composable
private fun LabelInputPanelContent(
    stepFrameCount: StateFlow<Int>,
    onConfirm: (String) -> Unit,
    onDiscard: () -> Unit
) {
    val frameCount by stepFrameCount.collectAsState()
    var labelText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "描述这段操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "已捕获 $frameCount 帧。请简要描述刚才录制的操作。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 120.dp),
                placeholder = { Text("如：从主页点击房态房量入口") },
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDiscard) {
                    Text("丢弃")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(labelText) },
                    enabled = labelText.isNotBlank()
                ) {
                    Text("确认")
                }
            }
        }
    }
}

/**
 * 构建模式悬浮球：绿色圆球，表示可以开始下一段录制。
 * 未展开时为绿色"+"球，展开时显示"开始录制"和"完成"按钮。
 */
@Composable
private fun BuildingBallContent(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onBallDrag: (Float, Float) -> Unit,
    onStartRecording: () -> Unit,
    onFinishBuilding: () -> Unit
) {
    if (!isExpanded) {
        // 未展开：绿色小圆球，可拖拽，点击展开
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50))
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onBallDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { onToggleExpand() }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        // 展开：操作面板
        Card(
            modifier = Modifier
                .width(200.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onBallDrag(dragAmount.x, dragAmount.y)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "步骤已保存",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 开始录制下一步
                Button(
                    onClick = onStartRecording,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("录制下一步")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 完成构建
                OutlinedButton(
                    onClick = onFinishBuilding,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("完成")
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 收起按钮
                TextButton(onClick = onToggleExpand) {
                    Text("收起", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
