package com.ai.assistance.operit.ui.features.floating.agentstatus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI 状态：悬浮球展开时的面板内容。
 *
 * 由 [com.ai.assistance.operit.services.AgentStatusOverlayService] 根据
 * [com.ai.assistance.operit.hermes.gateway.GatewayChatEventBus] 事件 +
 * [com.ai.assistance.operit.hermes.gateway.AgentEventBus] +
 * [com.ai.assistance.operit.hermes.gateway.AgentTokenBus] 实时计算。
 */
data class AgentStatusUiState(
    /** 平台展示名："feishu" / "wechat" / ... */
    val platform: String,
    /** chatId 短哈希用于面板展示："oc_b13c…" */
    val chatIdShort: String,
    /** 当前状态文字："思考中" / "调用工具: search_weather" / ... */
    val statusText: String,
    /** 已运行毫秒（从 ProcessingStarted 时刻起算） */
    val elapsedMs: Long,
    /** 同时在跑的 chat 数（>1 时面板提示"N 个对话进行中"） */
    val activeChatCount: Int = 1,
    /** 当前 turn 数（从 AgentEvent.turn / Final.turnsUsed 取，0 表示未知） */
    val turn: Int = 0,
    /** 累计 input token（来自 AgentTokenBus.onTurnComplete 累加） */
    val tokensIn: Int = 0,
    /** 累计 output token */
    val tokensOut: Int = 0,
    /** 是否处于失败闪烁状态（小球变红 2.5s） */
    val errorFlash: Boolean = false,
)

private val BallColor = Color(0xFF6750A4)        // M3 primary 紫色
private val BallColorInner = Color(0xFF7F67BE)   // 渐变内圈
private val BallColorError = Color(0xFFB3261E)   // M3 error 红
private val BallColorErrorInner = Color(0xFFE46962)
private val PanelBg = Color(0xCC1C1B1F)          // 半透明深色

/**
 * 收起态：56dp 圆球，半透明紫色背景 + 闪电图标 + 持续旋转动画。
 *
 * 拖拽改变屏幕位置；点击展开面板。
 *
 * @param errorFlash 失败时短暂变红 2.5s（由 Service 控制超时后切回正常或 hide）
 */
@Composable
fun AgentStatusBall(
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit = {},
    errorFlash: Boolean = false,
) {
    val infinite = rememberInfiniteTransition(label = "agent-ball-spin")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )

    val outer = if (errorFlash) BallColorError else BallColor
    val inner = if (errorFlash) BallColorErrorInner else BallColorInner

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(inner, outer),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                ) { _, dragAmount ->
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { onTap() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Bolt,
            contentDescription = "Agent 运行中",
            tint = Color.White,
            modifier = Modifier
                .size(28.dp)
                .rotate(if (errorFlash) 0f else angle),
        )
    }
}

/**
 * 展开态：宽 ~280dp 卡片面板。
 *
 * 显示平台 / chatId 短哈希 / 已运行秒数 / 状态文字 / activeChatCount（>1 时）。
 * 点 X 隐藏整个悬浮窗（用户主动关），下次新 agent 启动时再出现。
 * 点空白处收起回小球。
 */
@Composable
fun AgentStatusPanel(
    state: AgentStatusUiState,
    onClose: () -> Unit,
    onCollapse: () -> Unit,
) {
    Box(
        modifier = Modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PanelBg)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures { onCollapse() }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 顶栏：标题 + 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = Color(0xFFB69DF8),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Agent 运行中",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .pointerInput(Unit) {
                            detectTapGestures { onClose() }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 来源行：平台 · chatId
            Text(
                text = "${state.platform} · ${state.chatIdShort}",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(8.dp))

            // 状态文字
            Text(
                text = state.statusText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(6.dp))

            // 运行时间 + turn + token
            val seconds = (state.elapsedMs / 1000L).coerceAtLeast(0L)
            val timeText = if (seconds < 60) {
                "已运行 ${seconds}s"
            } else {
                "已运行 ${seconds / 60}m${seconds % 60}s"
            }
            Text(
                text = timeText,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )

            // turn / token 行（只有有数据时才显示，避免空 "—" 占位）
            if (state.turn > 0 || state.tokensIn > 0 || state.tokensOut > 0) {
                Spacer(Modifier.height(4.dp))
                val parts = mutableListOf<String>()
                if (state.turn > 0) parts.add("Turn ${state.turn}")
                if (state.tokensIn > 0 || state.tokensOut > 0) {
                    parts.add("In ${formatTokenCount(state.tokensIn)} / Out ${formatTokenCount(state.tokensOut)}")
                }
                Text(
                    text = parts.joinToString(" · "),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
            }

            if (state.activeChatCount > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "另有 ${state.activeChatCount - 1} 个对话进行中",
                    color = Color(0xFFB69DF8),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** 把 token 数格式化为短形：1234 → "1.2k"；789 → "789"；123456 → "123k"。 */
private fun formatTokenCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 10_000 -> "%.1fk".format(n / 1000f)
    else -> "${n / 1000}k"
}
