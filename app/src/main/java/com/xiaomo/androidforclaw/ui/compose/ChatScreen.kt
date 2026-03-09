package com.xiaomo.androidforclaw.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomo.androidforclaw.ui.session.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat interface - Inspired by Stream Chat Android UI design style
 *
 * Design reference:
 * - Message bubbles: Rounded corners, different alignment for left and right
 * - Color theme: User messages blue, AI messages white/gray
 * - Input box: Rounded rectangle with send button
 * - Avatar: Circular, distinguishing between user and AI
 */

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    // Session-related parameters
    sessions: List<SessionManager.Session> = emptyList(),
    currentSession: SessionManager.Session? = null,
    onSessionChange: (String) -> Unit = {},
    onNewSession: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White) // Stream Chat style: white background
    ) {
        // Session control bar (reference OpenClaw)
        if (sessions.isNotEmpty()) {
            SessionControlBar(
                sessions = sessions,
                currentSession = currentSession,
                onSessionChange = onSessionChange,
                onNewSession = onNewSession,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Message list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "👋",
                        style = TextStyle(fontSize = 48.sp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "开始聊天",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF000000)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "向 AI 助手发送消息来控制手机",
                        style = TextStyle(
                            fontSize = 14.sp,
                            color = Color(0xFF999999)
                        )
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageItem(message = message)
                    }
                }
            }

            // Loading indicator
            if (isLoading) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI 正在思考...",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }

        // Divider
        Divider(
            color = Color(0xFFE0E0E0),
            thickness = 1.dp
        )

        // Message input box
        MessageComposer(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    // Stream Chat style colors
    val backgroundColor = if (message.isUser) Color(0xFF005FFF) else Color(0xFFEFEFEF)
    val textColor = if (message.isUser) Color.White else Color(0xFF000000)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        // AI avatar (left)
        if (!message.isUser) {
            Avatar(
                text = "AI",
                backgroundColor = Color(0xFF6C5CE7),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Message bubble
        Column(
            horizontalAlignment = alignment,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (message.isUser) 18.dp else 2.dp,
                    bottomEnd = if (message.isUser) 2.dp else 18.dp
                ),
                color = backgroundColor,
                shadowElevation = if (message.isUser) 0.dp else 1.dp,
                tonalElevation = if (message.isUser) 0.dp else 0.dp,
                modifier = Modifier
                    .widthIn(max = 260.dp)
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    )
                ) {
                    // Message content
                    Text(
                        text = message.content,
                        style = TextStyle(
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    )

                    // Timestamp and status
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(message.timestamp),
                            style = TextStyle(
                                color = textColor.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        )

                        if (message.isUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            StatusIndicator(status = message.status, color = textColor)
                        }
                    }
                }
            }
        }

        // User avatar (right)
        if (message.isUser) {
            Avatar(
                text = "You",
                backgroundColor = Color(0xFF005FFF),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Circular avatar component
 */
@Composable
fun Avatar(
    text: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(2).uppercase(),
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun StatusIndicator(
    status: MessageStatus,
    color: Color
) {
    val iconText = when (status) {
        MessageStatus.SENDING -> "⏱"
        MessageStatus.SENT -> "✓"
        MessageStatus.ERROR -> "⚠"
    }

    Text(
        text = iconText,
        style = TextStyle(
            color = color.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    )
}

@Composable
fun MessageComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shadowElevation = 4.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Input box - Stream Chat style
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF7F7F7),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Color(0xFFE0E0E0)
                )
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("chat_input"),
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = Color.Black,
                        lineHeight = 20.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (value.isNotBlank()) {
                                onSend()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "发送消息",
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = Color(0xFF999999)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send button - Stream Chat style
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .testTag("send_button"),
                shape = CircleShape,
                color = if (value.isNotBlank()) Color(0xFF005FFF) else Color(0xFFE0E0E0),
                onClick = {
                    if (value.isNotBlank()) {
                        onSend()
                    }
                }
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = if (value.isNotBlank()) Color.White else Color(0xFF999999),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Session control bar - Reference OpenClaw design
 *
 * Features:
 * - Display current session
 * - Dropdown selector to switch sessions
 * - New session button
 */
@Composable
fun SessionControlBar(
    sessions: List<SessionManager.Session>,
    currentSession: SessionManager.Session?,
    onSessionChange: (String) -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = Color(0xFFF7F7F7),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Session selector (similar to OpenClaw's select)
            Box(
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = Color(0xFFE0E0E0)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Session title
                        Text(
                            text = currentSession?.title ?: "新对话",
                            style = TextStyle(
                                fontSize = 14.sp,
                                color = Color(0xFF333333),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Dropdown icon
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "选择会话",
                            tint = Color(0xFF666666),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Dropdown menu
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 200.dp, max = 300.dp)
                ) {
                    sessions.forEach { session ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = session.title,
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            fontWeight = if (session.id == currentSession?.id) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatSessionTime(session.createdAt),
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = Color(0xFF999999)
                                        )
                                    )
                                }
                            },
                            onClick = {
                                onSessionChange(session.id)
                                expanded = false
                            },
                            modifier = Modifier.background(
                                if (session.id == currentSession?.id) {
                                    Color(0xFFF0F0F0)
                                } else {
                                    Color.Transparent
                                }
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // New Session button
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFF005FFF),
                onClick = onNewSession
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "新建会话",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Format Session creation time
 */
private fun formatSessionTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

// Preview functionality removed, use in actual Activity
