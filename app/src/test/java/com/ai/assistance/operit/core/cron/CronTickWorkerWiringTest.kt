package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-031: CronTickWorker 是 Android cron 链路的执行入口。本测试守它的核心配置：
 *  - 是 CoroutineWorker
 *  - PeriodicWorkRequest 间隔 = 15 分钟（WorkManager 硬下限）
 *  - ExistingPeriodicWorkPolicy.UPDATE（2026-06-18 R-AGENT-031 bugfix：从 KEEP
 *    改成 UPDATE，否则上次安装残留的 broken unique work record 会让新 enqueue 不生效）
 *  - doWork 调 getDueJobs() + advanceNextRun + CronAgentRunner.run
 *
 * 对应 TC-AGENT-031-e, f。
 */
class CronTickWorkerWiringTest {

    private val source: String by lazy { File(workerPath()).readText() }

    /**
     * TC-AGENT-031-e: CronTickWorker 必须存在并是 CoroutineWorker；
     * companion object 必须含 enqueue + 15 分钟周期 + UPDATE policy。
     */
    @Test
    fun `TC-AGENT-031-e CronTickWorker is a CoroutineWorker with 15-minute periodic schedule`() {
        assertTrue(
            "CronTickWorker.kt 必须含 `class CronTickWorker` 声明。",
            Regex("""class\s+CronTickWorker\b""").containsMatchIn(source)
        )
        assertTrue(
            "CronTickWorker 必须继承 `CoroutineWorker` —— 才能在 doWork() 里跑 suspend 函数。",
            source.contains("CoroutineWorker")
        )
        assertTrue(
            "CronTickWorker 必须用 `PeriodicWorkRequestBuilder` —— 这是 WorkManager 的周期 API。",
            source.contains("PeriodicWorkRequestBuilder")
        )
        assertTrue(
            "CronTickWorker 必须有 `INTERVAL_MINUTES = 15` —— WorkManager 硬下限。",
            Regex("""INTERVAL_MINUTES[^=\n]*=\s*15""").containsMatchIn(source)
        )
        assertTrue(
            "CronTickWorker 必须用 `ExistingPeriodicWorkPolicy.UPDATE` —— " +
                "broken state 从上次安装残留时能被替换；KEEP 会保留死状态导致 cron 永久失效（详见 TC-AGENT-031-o）。",
            source.contains("ExistingPeriodicWorkPolicy.UPDATE")
        )
        assertTrue(
            "CronTickWorker 必须含 unique work name 字面值 `hermes_cron_tick`。",
            source.contains("hermes_cron_tick")
        )
    }

    /**
     * TC-AGENT-031-f: doWork() 必须调 getDueJobs() + advanceNextRun + CronAgentRunner.run。
     */
    @Test
    fun `TC-AGENT-031-f doWork iterates due jobs and advances next-run`() {
        assertTrue(
            "CronTickWorker.doWork() 必须调 `getDueJobs()` —— Jobs.kt 数据层 API。",
            source.contains("getDueJobs")
        )
        assertTrue(
            "CronTickWorker.doWork() 必须调 `advanceNextRun(jobId)` —— " +
                "tick 后必须把 next_run 推进，否则下个 tick 会重复触发。",
            source.contains("advanceNextRun")
        )
        assertTrue(
            "CronTickWorker.doWork() 必须调 `CronAgentRunner.run` —— " +
                "agent invocation 落点。",
            source.contains("CronAgentRunner.run")
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun workerPath(): String =
        File(appSrcMainRoot(), "core/cron/CronTickWorker.kt").path
}
