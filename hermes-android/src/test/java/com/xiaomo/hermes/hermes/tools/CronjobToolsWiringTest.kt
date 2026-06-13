package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-031 (2026-06-13)：让 hermes-android 的 cronjob 工具真正落地。
 *
 * **背景**：原 `CronjobTools.kt` 的 `cronjob` 函数体只 `return toolError("cronjob tool
 * is not available on Android")`，`checkCronjobRequirements()` 直接 `return false`。
 * 这意味着 agent 想登记定时任务时永远拿到"工具不可用"错误。R-AGENT-031 解决方案是
 * "数据层放本模块、调度+invoke 放 app 模块"（路径 1+3+4），本文件守 hermes-android 侧
 * 的"数据层不再 stub"。
 *
 * **测试策略**：跟 SystemPromptMemoryMaintenanceWiringTest 同范式 —— 直接对源码字符串扫
 * 关键字面值，不依赖 Android Context / Robolectric。运行时正确性由 §3 E2E 兜底。
 *
 * 对应 TC-AGENT-031-a..c（见 docs/hermes-test-cases.md）。
 */
class CronjobToolsWiringTest {

    private val source: String by lazy { File(cronjobToolsPath()).readText() }

    /**
     * TC-AGENT-031-a: cronjob() 不能再返回 "cronjob tool is not available on Android"。
     * 该字面值是旧 stub 标志，新实现必须删干净。
     */
    @Test
    fun `TC-AGENT-031-a cronjob no longer returns not-available stub`() {
        assertFalse(
            "CronjobTools.kt 不得再含 `cronjob tool is not available on Android` 字面值 —— " +
                "这是旧 stub 文案，R-AGENT-031 必须删除。\n" +
                "新实现应 dispatch 到 Jobs.kt CRUD（create/list/get/update/pause/resume/run/remove）。",
            source.contains("cronjob tool is not available on Android")
        )
    }

    /**
     * TC-AGENT-031-b: cronjob() 必须 dispatch 各 action 分支。
     * 直接断言 when 分支字面值（"create" / "list" / "remove" / "pause" / "resume" / "run" / "update"）。
     */
    @Test
    fun `TC-AGENT-031-b cronjob dispatcher branches all actions`() {
        listOf("\"create\"", "\"list\"", "\"remove\"", "\"pause\"", "\"resume\"", "\"run\"", "\"update\"").forEach { branch ->
            assertTrue(
                "CronjobTools.kt 必须含 when 分支字面值 $branch —— " +
                    "agent 通过 action= 参数走对应 CRUD 路径。",
                source.contains(branch)
            )
        }
        // 必须 import Jobs.kt 的 CRUD API
        listOf("createJob", "listJobs", "getJob", "removeJob", "pauseJob", "resumeJob", "triggerJob", "updateJob").forEach { fn ->
            assertTrue(
                "CronjobTools.kt 必须 import 或调用 `$fn` —— Jobs.kt 数据层。",
                source.contains(fn)
            )
        }
    }

    /**
     * TC-AGENT-031-c: 必须含 15-minute minimum interval guard 文案 +
     * `checkCronjobRequirements()` 函数体含 `return true`。
     */
    @Test
    fun `TC-AGENT-031-c min-interval guard and checkRequirements returns true`() {
        assertTrue(
            "CronjobTools.kt 必须含常量 `ANDROID_CRON_MIN_INTERVAL_MINUTES = 15` —— " +
                "WorkManager PeriodicWorkRequest 不能比 15 分钟更快，必须在 API 边界拦。",
            Regex("""ANDROID_CRON_MIN_INTERVAL_MINUTES\s*=\s*15""").containsMatchIn(source)
        )
        assertTrue(
            "CronjobTools.kt 必须含 `Android requires a minimum interval of` 错误文案 —— " +
                "用户输入子-15-分钟 interval 时 agent 拿到这条错误，知道 Android 平台限制。",
            source.contains("Android requires a minimum interval of")
        )
        // checkCronjobRequirements 必须 `return true`（agent 不会被劝退）
        val pattern = Regex(
            """fun\s+checkCronjobRequirements\s*\(\s*\)\s*:\s*Boolean\s*\{[\s\S]*?return\s+true[\s\S]*?\}""",
            RegexOption.MULTILINE
        )
        assertTrue(
            "checkCronjobRequirements() 必须 `return true` —— 数据层 + WorkManager tick " +
                "在 Android 上一直可用，不需要外部 daemon。",
            pattern.containsMatchIn(source)
        )
    }

    // ----- helpers -----

    private fun hermesAndroidSrcMainRoot(): File {
        // hermes-android 测试 cwd 通常已经是 hermes-android/，所以从 src/main 起。
        val candidate = File("src/main/java/com/xiaomo/hermes")
        if (candidate.exists()) return candidate
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes")
        if (alt.exists()) return alt
        error("Cannot locate hermes-android src/main/java/com/xiaomo/hermes — cwd=${File(".").absolutePath}")
    }

    private fun cronjobToolsPath(): String =
        File(hermesAndroidSrcMainRoot(), "hermes/tools/CronjobTools.kt").path
}
