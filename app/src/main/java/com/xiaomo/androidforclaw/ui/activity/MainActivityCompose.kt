package com.xiaomo.androidforclaw.ui.activity

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaomo.androidforclaw.service.PhoneAccessibilityService
import com.xiaomo.androidforclaw.ui.compose.ChatScreen
import com.xiaomo.androidforclaw.ui.viewmodel.ChatViewModel
import com.xiaomo.androidforclaw.util.ChatBroadcastReceiver
import com.xiaomo.androidforclaw.util.MediaProjectionHelper
import com.xiaomo.androidforclaw.ui.float.SessionFloatWindow
import com.tencent.mmkv.MMKV
import com.xiaomo.androidforclaw.util.MMKVKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 检查 S4Claw (observer扩展) 的无障碍服务是否已启用
 */
fun isS4ClawAccessibilityEnabled(context: Context): Boolean {
    return try {
        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1

        if (!accessibilityEnabled) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        // S4Claw 的无障碍服务包名
        val s4clawServiceName = "com.xiaomo.androidforclaw.accessibility/com.xiaomo.androidforclaw.accessibility.service.PhoneAccessibilityService"

        enabledServices.contains(s4clawServiceName)
    } catch (e: Exception) {
        Log.e("MainActivityCompose", "检查 S4Claw 无障碍服务失败", e)
        false
    }
}

/**
 * MainActivity - Compose 版本
 *
 * 包含三个Tab：
 * 1. 对话 - AI 助手聊天界面
 * 2. 状态 - 系统状态卡片
 * 3. 设置 - 配置和测试入口
 */
class MainActivityCompose : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivityCompose"
        private const val REQUEST_MANAGE_EXTERNAL_STORAGE = 1001
    }

    private var chatBroadcastReceiver: ChatBroadcastReceiver? = null
    private var chatViewModel: ChatViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查并请求文件管理权限
        checkAndRequestStoragePermission()

        setContent {
            // 保存 ViewModel 引用以供 BroadcastReceiver 使用
            val viewModel: ChatViewModel = viewModel()
            chatViewModel = viewModel

            MaterialTheme {
                MainScreen(
                    chatViewModel = viewModel,
                    onNavigateToPermissions = {
                        startActivity(Intent(this, PermissionsActivity::class.java))
                    },
                    onNavigateToSkills = {
                        startActivity(Intent(this, SkillsActivity::class.java))
                    },
                    onNavigateToConfig = {
                        Log.d("MainActivityCompose", "点击了模型配置")
                        try {
                            startActivity(Intent(this, ConfigActivity::class.java))
                            Log.d("MainActivityCompose", "成功启动 ConfigActivity")
                        } catch (e: Exception) {
                            Log.e("MainActivityCompose", "启动 ConfigActivity 失败", e)
                        }
                    },
                    onNavigateToTest = {
                        // AgentTestActivity已移除
                        Toast.makeText(this, "Agent测试功能已废弃", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // 注册 ADB 测试接口
        registerChatBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()
        // 主页面可见时通知悬浮窗管理器
        SessionFloatWindow.setMainActivityVisible(true, this)
    }

    override fun onPause() {
        super.onPause()
        // 主页面不可见时通知悬浮窗管理器
        SessionFloatWindow.setMainActivityVisible(false, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterChatBroadcastReceiver()
    }

    /**
     * 注册 Chat Broadcast Receiver
     *
     * 注意：使用 RECEIVER_EXPORTED 以支持 ADB 测试
     */
    private fun registerChatBroadcastReceiver() {
        chatBroadcastReceiver = ChatBroadcastReceiver { message ->
            Log.d(TAG, "📨 [BroadcastReceiver] 收到消息: $message")
            chatViewModel?.sendMessage(message)
        }

        val filter = ChatBroadcastReceiver.createIntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Log.i(TAG, "✅ 注册 ChatBroadcastReceiver (EXPORTED, SDK >= 33)")
            registerReceiver(chatBroadcastReceiver, filter, RECEIVER_EXPORTED)
        } else {
            Log.i(TAG, "✅ 注册 ChatBroadcastReceiver (SDK < 33)")
            registerReceiver(chatBroadcastReceiver, filter)
        }
    }

    /**
     * 注销 Chat Broadcast Receiver
     */
    private fun unregisterChatBroadcastReceiver() {
        chatBroadcastReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * 检查并请求文件管理权限
     */
    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 权限
            if (!Environment.isExternalStorageManager()) {
                // Debug版本跳过权限请求页面,避免跳转Settings导致Activity进入后台影响测试
                if (com.draco.ladb.BuildConfig.SKIP_PERMISSION_REQUEST) {
                    Log.w(TAG, "⚠️ DEBUG模式: 文件管理权限未授予,但跳过请求页面")
                    Log.w(TAG, "   配置文件读写可能失败,请手动授予权限")
                    return
                }

                Log.i(TAG, "文件管理权限未授予，请求权限...")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
                } catch (e: Exception) {
                    Log.e(TAG, "无法打开文件管理权限设置页面", e)
                    // 降级到通用设置页面
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE)
                    } catch (e2: Exception) {
                        Log.e(TAG, "无法打开文件管理权限设置", e2)
                    }
                }
            } else {
                Log.i(TAG, "✅ 文件管理权限已授予")
            }
        } else {
            // Android 10 及以下使用传统权限
            Log.i(TAG, "Android 10 及以下，使用传统存储权限")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Log.i(TAG, "✅ 文件管理权限已授予")
                } else {
                    Log.w(TAG, "⚠️ 文件管理权限未授予，配置文件读取可能失败")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    chatViewModel: ChatViewModel,
    onNavigateToPermissions: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onNavigateToTest: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.values().forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> ChatTab(chatViewModel)
                1 -> StatusTab(
                    onNavigateToPermissions = onNavigateToPermissions,
                    onNavigateToSkills = onNavigateToSkills
                )
                2 -> SettingsTab(onNavigateToConfig, onNavigateToTest)
            }
        }
    }
}

enum class MainTab(val title: String, val icon: ImageVector) {
    CHAT("对话", Icons.Default.Chat),
    STATUS("状态", Icons.Default.Dashboard),
    SETTINGS("设置", Icons.Default.Settings)
}

@Composable
fun ChatTab(chatViewModel: ChatViewModel) {
    val messages by chatViewModel.messages.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val sessions by chatViewModel.sessions.collectAsState()
    val currentSession by chatViewModel.currentSession.collectAsState()

    ChatScreen(
        messages = messages,
        onSendMessage = { message ->
            chatViewModel.sendMessage(message)
        },
        isLoading = isLoading,
        sessions = sessions,
        currentSession = currentSession,
        onSessionChange = { sessionId ->
            chatViewModel.switchSession(sessionId)
        },
        onNewSession = {
            chatViewModel.createNewSession()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTab(
    onNavigateToPermissions: () -> Unit,
    onNavigateToSkills: () -> Unit = {}
) {
    val context = LocalContext.current

    // 动态获取 Gateway 状态
    val gatewayRunning = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 检查 Gateway 端口是否在监听
        try {
            val result = withContext(Dispatchers.IO) {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", 8765), 100)
                    true
                }
            }
            gatewayRunning.value = result
        } catch (e: Exception) {
            gatewayRunning.value = false
        }
    }

    // 动态获取 Skills 数量
    val skillsCount = remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val loader = com.xiaomo.androidforclaw.agent.skills.SkillsLoader(context)
            val stats = loader.getStatistics()
            skillsCount.value = stats.totalSkills
        } catch (e: Exception) {
            Log.e("StatusTab", "获取 Skills 数量失败", e)
            skillsCount.value = 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AndroidForClaw",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "AI 移动自动化平台",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 权限卡片
        PermissionsCard(onClick = onNavigateToPermissions)

        // Gateway 卡片
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Gateway",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (gatewayRunning.value) "运行中 (ws://0.0.0.0:8765)" else "未运行",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (gatewayRunning.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }

        // Skills 卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToSkills
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Skills",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (skillsCount.value > 0) "${skillsCount.value} 个 Skills" else "加载中...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsCard(onClick: () -> Unit) {
    val context = LocalContext.current
    // 检查 S4Claw (observer) 的无障碍服务
    val accessibility = isS4ClawAccessibilityEnabled(context)
    // 检查悬浮窗权限
    val overlay = Settings.canDrawOverlays(context)
    // 检查截图权限
    val screenCapture = MediaProjectionHelper.isMediaProjectionGranted()

    val allGranted = accessibility && overlay && screenCapture
    val grantedCount = listOf(accessibility, overlay, screenCapture).count { it }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "权限",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (allGranted) "已授权" else "已授予 $grantedCount/3 个权限",
                style = MaterialTheme.typography.bodyMedium,
                color = if (allGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "无障碍: ${if (accessibility) "✓" else "✗"}  " +
                        "悬浮窗: ${if (overlay) "✓" else "✗"}  " +
                        "录屏: ${if (screenCapture) "✓" else "✗"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    onNavigateToConfig: () -> Unit,
    onNavigateToTest: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 配置按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                android.util.Log.d("SettingsTab", "卡片被点击了")
                onNavigateToConfig()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "配置"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "模型配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "配置 API Key 和模型参数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Channels 按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val intent = Intent(context, ChannelListActivity::class.java)
                context.startActivity(intent)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Channels"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Channels",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "配置多渠道接入（飞书等）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 查看 openclaw.json
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val openclawJsonPath = "/sdcard/.androidforclaw/config/openclaw.json"
                val file = java.io.File(openclawJsonPath)
                if (file.exists()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        intent.setDataAndType(uri, "text/plain")
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(intent, "选择文本编辑器"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "无法打开文件: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "文件不存在: $openclawJsonPath", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "openclaw.json"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "openclaw.json",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "查看 OpenClaw 配置文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "/sdcard/.androidforclaw/config/openclaw.json",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 测试按钮
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToTest
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "测试"
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Agent Test",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Execute Agent Task",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 悬浮窗开关
        FloatWindowSwitch()

        // 关于
        AboutCard()
    }
}

@Composable
fun FloatWindowSwitch() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mmkv = remember { MMKV.defaultMMKV() }
    var isEnabled by remember {
        mutableStateOf(mmkv.decodeBool(MMKVKeys.FLOAT_WINDOW_ENABLED.key, false))
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "会话悬浮窗",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "在后台显示会话信息",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    isEnabled = enabled
                    SessionFloatWindow.setEnabled(context, enabled)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutCard() {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 获取版本信息
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "Unknown"
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "Unknown"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "Unknown"
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "关于",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Divider()

            // 邮箱
            InfoRow(
                label = "邮箱",
                value = "xiaomochn@gmail.com",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:xiaomochn@gmail.com")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 复制到剪贴板
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Email", "xiaomochn@gmail.com")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "邮箱已复制", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            // 微信
            InfoRow(
                label = "微信",
                value = "xiaomocn",
                onClick = {
                    // 复制到剪贴板
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("WeChat ID", "xiaomocn")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "微信号已复制", Toast.LENGTH_SHORT).show()
                }
            )

            Divider()

            // 飞书群
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val feishuUrl = "https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(feishuUrl))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "飞书群",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "飞书体验群",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "https://applink.feishu.cn/client/chat/chatter/add_by_link?link_token=566r8836-6547-43e0-b6be-d6c4a5b12b74",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            maxLines = 2
                        )
                    }
                }
            }

            // GitHub
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val githubUrl = "https://github.com/xiaomochn/AndroidForClaw"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "GitHub"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "GitHub 仓库",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "查看源码、提交 Issue、参与贡献",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Divider()

            // 版本和版权信息
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "版本：$versionName ($versionCode)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    text = "© 2024-2025 AndroidForClaw",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                Text(
                    text = "Inspired by OpenClaw",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

