package com.ai.assistance.operit.ui.features.cron.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.cron.CronAgentRunner
import com.xiaomo.hermes.hermes.cron.listJobs
import com.xiaomo.hermes.hermes.cron.pauseJob
import com.xiaomo.hermes.hermes.cron.removeJob
import com.xiaomo.hermes.hermes.cron.resumeJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * R-UI-063: cron jobs management screen.
 *
 * Read-only listing of jobs from `Jobs.listJobs()` with 4 row-level actions:
 *   - Trigger now (calls `CronAgentRunner.run` directly — UI is in the app
 *     module so it can reach the bridge without going through the
 *     `Scheduler.cronImmediateRunner` injection slot used by R-AGENT-043
 *     for the agent-tool path).
 *   - Pause / Resume (toggles via `Jobs.pauseJob` / `Jobs.resumeJob`).
 *   - Delete (`Jobs.removeJob`, with confirm dialog).
 *
 * Per R-UI-063 scope decision: this screen does NOT expose a "create" UI.
 * Cron jobs are created via natural-language conversation with the agent
 * (`cronjob(action="create", ...)`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CronJobsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var jobs by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Map<String, Any?>?>(null) }

    LaunchedEffect(refreshTick) {
        jobs = withContext(Dispatchers.IO) { listJobs(includeDisabled = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "定时任务",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "在对话里告诉 AI 「每天 9 点提醒我看新闻」即可创建。本页面只用来查看 / 暂停 / 删除 / 立即触发已有任务。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (jobs.isEmpty()) {
            Text(
                text = "暂无定时任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(jobs, key = { (it["id"] as? String) ?: it.hashCode().toString() }) { job ->
                    CronJobRow(
                        job = job,
                        onTriggerNow = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    CronAgentRunner.run(context, job)
                                }
                                refreshTick++
                            }
                        },
                        onPause = {
                            val id = job["id"] as? String ?: return@CronJobRow
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    pauseJob(id, reason = "user paused via sidebar")
                                }
                                refreshTick++
                            }
                        },
                        onResume = {
                            val id = job["id"] as? String ?: return@CronJobRow
                            scope.launch {
                                withContext(Dispatchers.IO) { resumeJob(id) }
                                refreshTick++
                            }
                        },
                        onDelete = { pendingDelete = job },
                    )
                }
            }
        }
    }

    pendingDelete?.let { job ->
        val name = (job["name"] as? String) ?: (job["id"] as? String) ?: "?"
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除定时任务") },
            text = { Text("确认删除 '$name'？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = job["id"] as? String
                    pendingDelete = null
                    if (id != null) {
                        scope.launch {
                            withContext(Dispatchers.IO) { removeJob(id) }
                            refreshTick++
                        }
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun CronJobRow(
    job: Map<String, Any?>,
    onTriggerNow: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val name = (job["name"] as? String) ?: (job["id"] as? String) ?: "(unnamed)"
    val schedule = (job["schedule_display"] as? String) ?: "?"
    val state = (job["state"] as? String) ?: if ((job["enabled"] as? Boolean) != false) "scheduled" else "paused"
    val nextRun = (job["next_run_at"] as? String) ?: "—"
    val lastRun = (job["last_run_at"] as? String) ?: "—"
    val lastStatus = (job["last_status"] as? String) ?: "—"
    val isPaused = state == "paused"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(text = "时间表: $schedule", style = MaterialTheme.typography.bodySmall)
            Text(text = "状态: $state · 下一次: $nextRun", style = MaterialTheme.typography.bodySmall)
            Text(text = "上一次: $lastRun · 结果: $lastStatus", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTriggerNow) {
                    Icon(Icons.Default.Refresh, contentDescription = "立即触发")
                }
                if (isPaused) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "恢复")
                    }
                } else {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "暂停")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}
