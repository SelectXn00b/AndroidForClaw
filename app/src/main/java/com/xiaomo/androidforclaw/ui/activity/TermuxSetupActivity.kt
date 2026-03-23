/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaomo.androidforclaw.agent.tools.TermuxBridgeTool
import com.xiaomo.androidforclaw.agent.tools.TermuxSetupStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TermuxSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TermuxSetupScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { TermuxBridgeTool(context) }

    // Status
    var termuxInstalled by remember { mutableStateOf(false) }
    var keypairReady by remember { mutableStateOf(false) }
    var sshReachable by remember { mutableStateOf(false) }
    var sshAuthOk by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var publicKey by remember { mutableStateOf<String?>(null) }

    fun refreshStatus() {
        scope.launch {
            val status = withContext(Dispatchers.IO) { bridge.getStatus() }
            termuxInstalled = status.termuxInstalled
            keypairReady = status.keypairPresent
            sshReachable = status.sshReachable
            sshAuthOk = status.sshAuthOk
            publicKey = withContext(Dispatchers.IO) { bridge.getPublicKey() }
            checking = false
        }
    }

    // Initial check + auto-refresh every 3s
    LaunchedEffect(Unit) {
        // Ensure BouncyCastle
        try {
            val bc = org.bouncycastle.jce.provider.BouncyCastleProvider()
            java.security.Security.removeProvider(bc.name)
            java.security.Security.insertProviderAt(bc, 1)
        } catch (_: Exception) {}
        refreshStatus()
        while (true) {
            delay(3000)
            refreshStatus()
        }
    }

    // Build the key setup command (only when public key is available)
    val keySetupCommand = remember(publicKey) {
        if (publicKey != null) {
            "mkdir -p ~/.ssh && echo '${publicKey}' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termux \u914d\u7f6e") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "\u8fd4\u56de")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Header
            Text(
                "\u6309\u4ee5\u4e0b\u6b65\u9aa4\u914d\u7f6e Termux\uff0c\u8ba9 AI \u80fd\u5728\u624b\u673a\u4e0a\u6267\u884c\u547d\u4ee4\u3002",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ============ Step 1: Install Termux ============
            SetupStepCard(
                step = 1,
                title = "\u5b89\u88c5 Termux",
                done = termuxInstalled,
            ) {
                Text(
                    "\u4ece F-Droid \u4e0b\u8f7d\u5b89\u88c5 Termux\uff08\u4e0d\u8981\u7528 Play Store \u7248\u672c\uff09",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!termuxInstalled) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://f-droid.org/packages/com.termux/")))
                        } catch (_: Exception) {
                            Toast.makeText(context, "\u8bf7\u624b\u52a8\u641c\u7d22\u5b89\u88c5 Termux", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("\u53bb\u4e0b\u8f7d")
                    }
                }
            }

            // ============ Step 2: Generate keypair ============
            SetupStepCard(
                step = 2,
                title = "\u751f\u6210 SSH \u5bc6\u94a5",
                done = keypairReady,
            ) {
                Text(
                    "\u70b9\u51fb\u4e0b\u65b9\u6309\u94ae\u81ea\u52a8\u751f\u6210\u5bc6\u94a5\u5bf9\uff08\u4ec5\u9700\u4e00\u6b21\uff09",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!keypairReady) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { bridge.ensureKeypair() }
                            refreshStatus()
                        }
                    }) {
                        Text("\u751f\u6210\u5bc6\u94a5")
                    }
                }
            }

            // ============ Step 3: termux-setup-storage ============
            SetupStepCard(
                step = 3,
                title = "\u6388\u6743 Termux \u8bbf\u95ee\u5b58\u50a8",
                done = sshAuthOk, // we know storage works if auth succeeded
            ) {
                Text(
                    "\u6253\u5f00 Termux\uff0c\u8f93\u5165\u4ee5\u4e0b\u547d\u4ee4\u5e76\u56de\u8f66\uff0c\u7136\u540e\u70b9\u300c\u5141\u8bb8\u300d\uff1a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                CommandBox(
                    command = "termux-setup-storage",
                    context = context
                )
            }

            // ============ Step 4: Install openssh ============
            SetupStepCard(
                step = 4,
                title = "\u5b89\u88c5 SSH \u670d\u52a1",
                done = sshAuthOk,
            ) {
                Text(
                    "\u5728 Termux \u4e2d\u6267\u884c\uff08\u9700\u7b49\u5f85\u5b89\u88c5\u5b8c\u6210\uff09\uff1a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                CommandBox(
                    command = "pkg install -y openssh",
                    context = context
                )
            }

            // ============ Step 5: Configure key ============
            SetupStepCard(
                step = 5,
                title = "\u914d\u7f6e SSH \u5bc6\u94a5",
                done = sshAuthOk,
            ) {
                if (keySetupCommand != null) {
                    Text(
                        "\u5728 Termux \u4e2d\u6267\u884c\u4ee5\u4e0b\u547d\u4ee4\uff0c\u5c06\u5bc6\u94a5\u6dfb\u52a0\u5230 SSH\uff1a",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    CommandBox(
                        command = keySetupCommand,
                        context = context
                    )
                } else {
                    Text(
                        "\u8bf7\u5148\u5b8c\u6210\u7b2c 2 \u6b65\u751f\u6210\u5bc6\u94a5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ============ Step 6: Start sshd ============
            SetupStepCard(
                step = 6,
                title = "\u542f\u52a8 SSH \u670d\u52a1",
                done = sshReachable,
            ) {
                Text(
                    "\u5728 Termux \u4e2d\u6267\u884c\uff1a",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                CommandBox(
                    command = "sshd",
                    context = context
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\u63d0\u793a\uff1a\u6bcf\u6b21\u91cd\u542f Termux \u540e\u9700\u91cd\u65b0\u8fd0\u884c sshd",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ============ Step 7: Verify ============
            SetupStepCard(
                step = 7,
                title = "\u9a8c\u8bc1\u8fde\u63a5",
                done = sshAuthOk,
            ) {
                if (sshAuthOk) {
                    Text(
                        "SSH \u8fde\u63a5\u6b63\u5e38\uff0c\u914d\u7f6e\u5b8c\u6210\uff01",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                } else if (sshReachable) {
                    Text(
                        "SSH \u7aef\u53e3\u53ef\u8fbe\uff0c\u4f46\u8ba4\u8bc1\u5931\u8d25\u3002\u8bf7\u786e\u8ba4\u7b2c 5 \u6b65\u7684\u5bc6\u94a5\u547d\u4ee4\u5df2\u6267\u884c\u3002",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        "\u7b49\u5f85\u8fde\u63a5... \u8bf7\u786e\u4fdd Termux \u5df2\u6253\u5f00\u4e14 sshd \u5df2\u8fd0\u884c\u3002",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        checking = true
                        refreshStatus()
                    },
                    enabled = !checking
                ) {
                    Text("\u91cd\u65b0\u68c0\u6d4b")
                }
            }

            // ============ Success banner ============
            if (sshAuthOk) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            "\u5b8c\u6210",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Termux \u914d\u7f6e\u5b8c\u6210\uff01",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "AI \u73b0\u5728\u53ef\u4ee5\u901a\u8fc7 exec \u547d\u4ee4\u6267\u884c Shell \u811a\u672c\u4e86\u3002",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onBack) {
                            Text("\u5b8c\u6210")
                        }
                    }
                }
            }

            // Loading
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupStepCard(
    step: Int,
    title: String,
    done: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Step indicator
            if (done) {
                Icon(
                    Icons.Default.CheckCircle,
                    "\u5b8c\u6210",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$step",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

@Composable
private fun CommandBox(command: String, context: Context) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E1E)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                color = Color(0xFF4EC9B0),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("command", command))
                    Toast.makeText(context, "\u5df2\u590d\u5236", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    "\u590d\u5236",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
