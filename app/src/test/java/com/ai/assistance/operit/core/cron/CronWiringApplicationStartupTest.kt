package com.ai.assistance.operit.core.cron

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-031: OperitApplication.onCreate 必须 enqueue CronTickWorker，否则 Android
 * 重启 / 进程被 kill 后 cron 链路就静默死亡。
 *
 * 对应 TC-AGENT-031-j。
 */
class CronWiringApplicationStartupTest {

    private val source: String by lazy { File(applicationPath()).readText() }

    /**
     * TC-AGENT-031-j: OperitApplication 必须 import CronTickWorker 并在 onCreate 调
     * `CronTickWorker.enqueue(this)`。
     */
    @Test
    fun `TC-AGENT-031-j OperitApplication enqueues CronTickWorker on startup`() {
        assertTrue(
            "OperitApplication.kt 必须 import `CronTickWorker` —— " +
                "进程启动时入队，进程重启后仍能定时触发。",
            source.contains("import com.ai.assistance.operit.core.cron.CronTickWorker")
        )
        assertTrue(
            "OperitApplication.onCreate 必须调 `CronTickWorker.enqueue(this)` —— " +
                "WorkManager unique periodic 入队（KEEP policy）。",
            source.contains("CronTickWorker.enqueue(this)")
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

    private fun applicationPath(): String =
        File(appSrcMainRoot(), "core/application/OperitApplication.kt").path
}
