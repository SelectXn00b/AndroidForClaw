/**
 * Weixin channel setup page — QR code login + status display.
 */
package com.xiaomo.androidforclaw.ui.activity

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaomo.weixin.WeixinChannel
import com.xiaomo.weixin.WeixinConfig
import com.xiaomo.weixin.auth.QRCodeGenerator
import com.xiaomo.weixin.storage.WeixinAccountStore
import kotlinx.coroutines.launch

class WeixinChannelActivity : ComponentActivity() {
    companion object {
        private const val TAG = "WeixinChannelActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WeixinChannelScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeixinChannelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val configLoader = remember { com.xiaomo.androidforclaw.config.ConfigLoader(context) }
    val openClawConfig = remember { configLoader.loadOpenClawConfig() }
    val weixinCfg = openClawConfig.channels.weixin

    var enabled by remember { mutableStateOf(weixinCfg?.enabled ?: false) }
    var statusText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var accountInfo by remember { mutableStateOf("") }

    // Check existing account on load
    LaunchedEffect(Unit) {
        val account = WeixinAccountStore.loadAccount()
        if (account != null && !account.token.isNullOrBlank()) {
            isLoggedIn = true
            accountInfo = "账号: ${account.accountId ?: "未知"}\n用户: ${account.userId ?: "未知"}"
            statusText = "✅ 已登录"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("微信 (Weixin)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "通过微信 ClawBot 插件接入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 启用开关
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启用微信 Channel",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "开启后将接收微信消息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { newValue ->
                            enabled = newValue
                            // 保存到配置
                            val currentConfig = configLoader.loadOpenClawConfig()
                            val updatedWeixin = (currentConfig.channels.weixin ?: com.xiaomo.androidforclaw.config.WeixinChannelConfig())
                                .copy(enabled = newValue)
                            val updatedConfig = currentConfig.copy(
                                channels = currentConfig.channels.copy(weixin = updatedWeixin)
                            )
                            configLoader.saveOpenClawConfig(updatedConfig)

                            if (newValue) {
                                // 启用：尝试启动通道
                                (context.applicationContext as? com.xiaomo.androidforclaw.core.MyApplication)
                                    ?.restartWeixinChannel()
                                statusText = "✅ 已启用"
                            } else {
                                // 禁用：停止通道
                                com.xiaomo.androidforclaw.core.MyApplication.getWeixinChannel()?.stop()
                                statusText = "已禁用"
                            }
                        },
                    )
                }
            }

            if (isLoggedIn) {
                // Show logged-in state
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("✅ 已连接微信", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(accountInfo, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Logout button
                OutlinedButton(
                    onClick = {
                        // 停止微信消息监听
                        com.xiaomo.androidforclaw.core.MyApplication.getWeixinChannel()?.stop()
                        WeixinAccountStore.clearAccount()
                        isLoggedIn = false
                        accountInfo = ""
                        statusText = "已退出登录"
                        qrBitmap = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("退出登录")
                }
            } else {
                // QR code display
                qrBitmap?.let { bmp ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("使用微信扫描二维码", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(12.dp))
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "微信登录二维码",
                                modifier = Modifier.size(250.dp),
                            )
                        }
                    }
                }

                // Login button
                Button(
                    onClick = {
                        isLoggingIn = true
                        statusText = "正在获取二维码..."
                        qrBitmap = null

                        scope.launch {
                            try {
                                val baseUrl = weixinCfg?.baseUrl
                                    ?.takeIf { it.isNotBlank() }
                                    ?: WeixinConfig.DEFAULT_BASE_URL

                                val channel = WeixinChannel(
                                    WeixinConfig(baseUrl = baseUrl, routeTag = weixinCfg?.routeTag)
                                )
                                val qrLogin = channel.createQRLogin()

                                // Fetch QR code
                                val qrResult = qrLogin.fetchQRCode()
                                if (qrResult == null) {
                                    statusText = "❌ 获取二维码失败"
                                    isLoggingIn = false
                                    return@launch
                                }

                                val (qrcodeUrl, qrcode) = qrResult

                                // Generate QR image locally
                                // qrcodeUrl is a web page link, not an image — use it as QR content
                                statusText = "正在生成二维码..."
                                val bitmap = QRCodeGenerator.generate(qrcodeUrl, 512)
                                if (bitmap != null) {
                                    qrBitmap = bitmap
                                    statusText = "请使用微信扫描二维码"
                                } else {
                                    statusText = "⚠️ 二维码生成失败，请重试"
                                    isLoggingIn = false
                                    return@launch
                                }

                                // Wait for login
                                val loginResult = qrLogin.waitForLogin(
                                    qrcode = qrcode,
                                    onStatusUpdate = { status ->
                                        statusText = when (status) {
                                            "wait" -> "等待扫码..."
                                            "scaned" -> "👀 已扫码，请在微信上确认"
                                            "expired" -> "二维码已过期，正在刷新..."
                                            "confirmed" -> "✅ 登录成功！"
                                            else -> status
                                        }
                                    },
                                    onQRRefreshed = { newUrl ->
                                        val newBitmap = QRCodeGenerator.generate(newUrl, 512)
                                        if (newBitmap != null) {
                                            qrBitmap = newBitmap
                                        }
                                    }
                                )

                                if (loginResult.connected) {
                                    isLoggedIn = true
                                    accountInfo = "账号: ${loginResult.accountId ?: "未知"}\n用户: ${loginResult.userId ?: "未知"}"
                                    statusText = loginResult.message
                                    qrBitmap = null

                                    // 通知 MyApplication 重新启动微信消息监听
                                    (context.applicationContext as? com.xiaomo.androidforclaw.core.MyApplication)
                                        ?.restartWeixinChannel()
                                } else {
                                    statusText = "❌ ${loginResult.message}"
                                }
                            } catch (e: Exception) {
                                Log.e("WeixinLogin", "Login error", e)
                                statusText = "❌ 登录失败: ${e.message}"
                            } finally {
                                isLoggingIn = false
                            }
                        }
                    },
                    enabled = !isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isLoggingIn) "登录中..." else "扫码登录")
                }
            }

            // Status text
            if (statusText.isNotBlank()) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusText.startsWith("✅")) {
                        MaterialTheme.colorScheme.primary
                    } else if (statusText.startsWith("❌")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("说明", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "基于微信 ClawBot 插件协议，扫码后即可通过微信与 AI 对话。\n" +
                                "• 仅支持私聊消息\n" +
                                "• 支持文字、图片、语音、文件\n" +
                                "• 登录凭证保存在本地",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}


