package com.ai.assistance.operit.integrations.externalchat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-AGENT-045: 在 in-app chat 触发的 agent loop 调用前后，
 * `ExternalChatRequestExecutor.execute()` 必须把 session vars 设成
 * `(platform="app", chatId=<resolved chat id>)`，让 `cronjob(action="create")`
 * 通过 `_originFromEnv()` 读到 `HERMES_SESSION_PLATFORM`/`HERMES_SESSION_CHAT_ID`，
 * 把 in-app chat 的 origin 写进 jobs.json，cron 跑完才能定位回原 chat。
 *
 * 源码扫描测试（不依赖 Robolectric）—— 只校验入口 wrap 的 wiring：
 *  - import setSessionVars / clearSessionVars / setCronAutoDeliverVars / clearCronAutoDeliverVars
 *  - execute() body 含 `setSessionVars(platform = "app", ...)` 调用
 *  - try/finally 配套：`clearSessionVars` + `clearCronAutoDeliverVars` 出现在 finally 块
 *
 * 对应 TC-AGENT-045-a。
 */
class ExternalChatRequestExecutorSessionVarsTest {

    private val source: String by lazy { File(executorPath()).readText() }

    /**
     * TC-AGENT-045-a: execute() 入口 wrap session vars。
     */
    @Test
    fun `TC-AGENT-045-a app-chat sets session vars before agent loop`() {
        // 必须 import session vars API（R-AGENT-033 在 hermes-android.gateway.SessionContext）
        assertTrue(
            "ExternalChatRequestExecutor.kt 必须 import `setSessionVars` —— R-AGENT-033 ThreadLocal API。",
            Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.setSessionVars""")
                .containsMatchIn(source)
        )
        assertTrue(
            "ExternalChatRequestExecutor.kt 必须 import `clearSessionVars` —— finally 清理。",
            Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.clearSessionVars""")
                .containsMatchIn(source)
        )
        assertTrue(
            "ExternalChatRequestExecutor.kt 必须 import `setCronAutoDeliverVars` —— " +
                "让 cronjob(action=create) 也能继承 in-app origin（与 R-AGENT-033 IM 路径对称）。",
            Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.setCronAutoDeliverVars""")
                .containsMatchIn(source)
        )
        assertTrue(
            "ExternalChatRequestExecutor.kt 必须 import `clearCronAutoDeliverVars` —— finally 清理。",
            Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.clearCronAutoDeliverVars""")
                .containsMatchIn(source)
        )

        // execute() 必须含 `setSessionVars(platform = "app", ...)` —— 平台 sentinel 固定 "app"
        assertTrue(
            "execute() 必须调用 `setSessionVars(platform = \"app\", ...)` —— " +
                "in-app chat 的 platform sentinel 是 \"app\"，cron `_originFromEnv()` 通过它识别 origin。",
            Regex("""setSessionVars\s*\(\s*platform\s*=\s*"app"""").containsMatchIn(source)
        )

        // 必须有 setCronAutoDeliverVars 调用（platform="app"）
        assertTrue(
            "execute() 必须调用 `setCronAutoDeliverVars(platform = \"app\", ...)` —— " +
                "对称 R-AGENT-033 IM 路径，让 cron auto-deliver 也能定位回 app chat。",
            Regex("""setCronAutoDeliverVars\s*\(\s*platform\s*=\s*"app"""").containsMatchIn(source)
        )

        // finally 块必须清理 session vars
        // 简单匹配：clearSessionVars() 必须出现，且 clearCronAutoDeliverVars() 必须出现
        assertTrue(
            "execute() 的 finally 块必须调 `clearSessionVars()` —— 否则 ThreadLocal 残留，" +
                "下次复用线程时会污染下一个请求的 origin。",
            source.contains("clearSessionVars()")
        )
        assertTrue(
            "execute() 的 finally 块必须调 `clearCronAutoDeliverVars()` —— 同上。",
            source.contains("clearCronAutoDeliverVars()")
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

    private fun executorPath(): String =
        File(
            appSrcMainRoot(),
            "integrations/externalchat/ExternalChatRequestExecutor.kt"
        ).path
}
