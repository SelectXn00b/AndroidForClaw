package com.xiaomo.hermes.hermes.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-031: Scheduler.kt header 必须文档化"Android 实际执行链路在 app 模块"。
 *
 * 1:1 对齐保留了 `runJob` / `deliverResult` 的 Python 端口逻辑（避免 deep_align 报回归），
 * 但 Android 真正的循环 (`CronTickWorker` + `CronAgentRunner`) 在 app 模块。本测试守
 * 文件头 KDoc 含"app module" / "WorkManager"字面值，让以后阅读源码的 Claude 能立刻定位。
 *
 * 对应 TC-AGENT-031-d。
 */
class CronWiringSchedulerStubTest {

    private val source: String by lazy { File(schedulerPath()).readText() }

    /**
     * TC-AGENT-031-d: Scheduler.kt 文件头必须含 `app module` 和 `WorkManager` 关键字面值，
     * 指向 app 模块的真实执行链路。
     */
    @Test
    fun `TC-AGENT-031-d scheduler file header documents app-module override`() {
        // 取 package 声明前的 header（KDoc 块）
        val packageIdx = source.indexOf("package ")
        assertTrue(
            "Scheduler.kt 必须含 `package ` 声明（基础合规性）。",
            packageIdx > 0
        )
        val header = source.substring(0, packageIdx)
        assertTrue(
            "Scheduler.kt 文件头 KDoc 必须含 `app module` 字面值 —— " +
                "R-AGENT-031 把执行链路落到 app 模块（CronTickWorker + CronAgentRunner），" +
                "header 必须文档化这件事，否则下次 Claude 不知道找去哪。\n" +
                "实际 header（前 1500 chars）:\n${header.take(1500)}",
            header.contains("app module")
        )
        assertTrue(
            "Scheduler.kt 文件头 KDoc 必须含 `WorkManager` 字面值 —— " +
                "Android 的 PeriodicWork tick 是 WorkManager 提供的，文档要点出来。",
            header.contains("WorkManager")
        )
        assertTrue(
            "Scheduler.kt 文件头 KDoc 必须 mention `CronTickWorker` —— " +
                "落点类的具体名字。",
            header.contains("CronTickWorker")
        )
    }

    // ----- helpers -----

    private fun hermesAndroidSrcMainRoot(): File {
        val candidate = File("src/main/java/com/xiaomo/hermes")
        if (candidate.exists()) return candidate
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes")
        if (alt.exists()) return alt
        error("Cannot locate hermes-android src/main/java/com/xiaomo/hermes — cwd=${File(".").absolutePath}")
    }

    private fun schedulerPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/cron/Scheduler.kt").path
}
