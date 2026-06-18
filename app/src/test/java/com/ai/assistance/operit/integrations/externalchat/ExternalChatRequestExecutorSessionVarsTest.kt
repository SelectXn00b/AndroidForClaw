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

    /**
     * TC-AGENT-045-g: createNewChat 后必须再 setSessionVars 一次拿 resolved chat_id。
     *
     * 修的 bug：execute() 顶部 setSessionVars 时 request.chatId 可能为空（典型场景：
     * `create_new_chat=true` 没传 chat_id），ThreadLocal 写的是 chatId=""，
     * `_originFromEnv()` 因 `isNotEmpty()` 检查失败返回 null —— jobs.json origin
     * 字段就是 null，cron 跑完无法精确定位回这个新 chat。
     *
     * 修法：prepareRequest() 内部 createNewChat() 调用之后立刻 listChats() 拿
     * currentChatId，re-set ThreadLocal，让本回合内 agent 调 cronjob(create) 时
     * _originFromEnv 能读到 platform="app" + chat_id=<新 id>。
     *
     * 同样 `!createNewChat && chatId.isNullOrBlank() && !createIfNone` 路径下
     * 用 listChats 拿到 currentChatId 时也要 re-set ThreadLocal。
     */
    @Test
    fun `TC-AGENT-045-g re-sets session vars after createNewChat resolves chat id`() {
        // prepareRequest 必须含 createNewChat 调用
        val createNewChatIdx = Regex("""chatTool\.createNewChat\s*\(""")
            .find(source)?.range?.first ?: -1
        assertTrue(
            "prepareRequest() 必须调用 `chatTool.createNewChat(...)` —— create_new_chat=true 路径",
            createNewChatIdx >= 0
        )

        // createNewChat 之后必须再有一次 listChats —— 用来拿 resolved currentChatId
        val afterCreate = source.substring(createNewChatIdx)
        assertTrue(
            "prepareRequest() 在 `createNewChat(...)` 之后必须再调 `listChats(...)` —— " +
                "拿到新建 chat 的 chat_id 用来 re-set ThreadLocal，否则 origin 字段为 null。",
            Regex("""chatTool\.listChats\s*\(""").containsMatchIn(afterCreate)
        )

        // createNewChat 之后必须再调一次 setSessionVars —— 用 resolved chat_id 覆盖空值
        assertTrue(
            "prepareRequest() 在 `createNewChat(...)` 之后必须再调 `setSessionVars(platform = \"app\", ...)` —— " +
                "用 listChats 返回的 currentChatId 覆盖 execute() 顶部写入的空 chat_id。",
            Regex("""setSessionVars\s*\(\s*platform\s*=\s*"app"""").containsMatchIn(afterCreate)
        )
        assertTrue(
            "prepareRequest() 在 `createNewChat(...)` 之后必须再调 `setCronAutoDeliverVars(platform = \"app\", ...)` —— " +
                "对称同步 cron auto-deliver ThreadLocal。",
            Regex("""setCronAutoDeliverVars\s*\(\s*platform\s*=\s*"app"""").containsMatchIn(afterCreate)
        )

        // 同样 !createNewChat && chatId.isNullOrBlank() 路径下，listChats 返回的
        // currentChatId 也必须 re-set ThreadLocal —— 即 source 中 setSessionVars
        // 至少出现 2 次（execute 顶部 1 次 + prepareRequest 内部 1 次）
        val setSessionCount = Regex("""setSessionVars\s*\(""").findAll(source).count()
        assertTrue(
            "ExternalChatRequestExecutor.kt 中 `setSessionVars(...)` 至少要出现 2 次：" +
                "execute() 顶部用 request.chatId（可能为空）写一次 + prepareRequest() 用 resolved chat_id 再写一次。" +
                "实际出现 $setSessionCount 次。",
            setSessionCount >= 2
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
