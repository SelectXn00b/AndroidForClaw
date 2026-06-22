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

        // createNewChat 之后必须用其返回值（ChatCreationResultData.chatId）拿到 resolved chat_id。
        //
        // 历史教训（why 改 TC）：原本要求 createNewChat 之后再 `listChats()` 拿
        // currentChatId，但 e2e 实测 `listChats` 偶发性返回 currentChatId=null —
        // chatHistoryManager.currentChatIdFlow 还没 emit 新值就被读了。
        // createNewChat 自己的 ToolResult 直接含 newChatId，更可靠。
        val afterCreate = source.substring(createNewChatIdx)
        assertTrue(
            "prepareRequest() 在 `createNewChat(...)` 之后必须用其返回值 `ChatCreationResultData.chatId` " +
                "解析新 chat_id —— listChats 偶发性返回 null（chatHistoryManager.currentChatIdFlow " +
                "未 emit 新值），createNewChat 自己的 ToolResult 是 source of truth。",
            Regex("""ChatCreationResultData""").containsMatchIn(afterCreate)
        )

        // createNewChat 之后必须再调一次 setSessionVars —— 用 resolved chat_id 覆盖空值
        assertTrue(
            "prepareRequest() 在 `createNewChat(...)` 之后必须再调 `setSessionVars(platform = \"app\", ...)` —— " +
                "用 createNewChat 返回的 chatId 覆盖 execute() 顶部写入的空 chat_id。",
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

    /**
     * TC-AGENT-045-h-2: `sendMessageToAI` 必须被 `withContext(sessionContextElement(`
     * 包裹——否则当 `EnhancedAIService.sendMessage` 内部 `withContext(Dispatchers.IO)`
     * 切到 IO 线程池时，源线程上 setSessionVars 写入的 ThreadLocal 不会
     * 跟过去，`_originFromEnv()` 读到空 → null → jobs.json origin = null
     * （e2e Stage C `"origin": null` 故障的根因）。
     *
     * 守 wiring 不被回归砍掉。
     */
    @Test
    fun `TC-AGENT-045-h-2 wraps sendMessageToAI in sessionContextElement`() {
        // 1) 必须 import sessionContextElement
        assertTrue(
            "ExternalChatRequestExecutor.kt 必须 import `sessionContextElement` —— " +
                "R-AGENT-045 跨线程 origin 传播 helper（等价 Python copy_context().run）",
            Regex("""import\s+com\.xiaomo\.hermes\.hermes\.gateway\.sessionContextElement""")
                .containsMatchIn(source)
        )
        // 2) 必须 import kotlinx.coroutines.withContext
        assertTrue(
            "ExternalChatRequestExecutor.kt 必须 import `kotlinx.coroutines.withContext`。",
            Regex("""import\s+kotlinx\.coroutines\.withContext""").containsMatchIn(source)
        )
        // 3) 必须有 `withContext(sessionContextElement(` 模式
        //    （宽松：允许 `sessionContextElement()` 后面紧跟 `)` 或参数）
        val wrapPattern = Regex("""withContext\s*\(\s*sessionContextElement\s*\(""")
        assertTrue(
            "execute() 必须用 `withContext(sessionContextElement()) { ... }` 包裹 " +
                "`sendMessageToAI(...)` —— sendMessageToAI 内部会跨 Dispatchers.IO，" +
                "不包裹 → ThreadLocal 不传播 → in-app cron origin 在 jobs.json 落盘时为 null。",
            wrapPattern.containsMatchIn(source)
        )

        // 4) 至少要包裹 `sendMessageToAI(` —— 直接搜两者出现的相对顺序：
        //    任意 `withContext(sessionContextElement(` 必须在某个 `sendMessageToAI(`
        //    之前出现，且距离不远（同一个 try 块）
        val wrapMatches = wrapPattern.findAll(source).map { it.range.first }.toList()
        val sendMatches = Regex("""\.sendMessageToAI\s*\(""").findAll(source)
            .map { it.range.first }.toList()
        assertTrue(
            "execute() 必须实际调用 `sendMessageToAI(` —— 否则没有 agent dispatch。",
            sendMatches.isNotEmpty()
        )
        assertTrue(
            "至少有一个 `sendMessageToAI(` 调用必须在 `withContext(sessionContextElement(` 块内 " +
                "（即源码里 wrap 的位置在它之前不超过 400 字符）—— 否则 wrap 可能挂在别处。",
            sendMatches.any { sendIdx ->
                wrapMatches.any { wrapIdx -> wrapIdx < sendIdx && sendIdx - wrapIdx < 400 }
            }
        )
    }

    // ----- helpers -----

    /**
     * TC-AGENT-045-i-4: prepareRequest() 必须把 origin 作为**显式参数**写进
     * `sendTool.parameters` —— 即 `__origin_platform="app"` + `__origin_chat_id=<resolved>`
     * 两个 `ToolParameter` 注入到 `sendParams`。
     *
     * 为什么必须这样：
     *
     * `sessionContextElement()` 路径只能管"同一协程"内嵌的 `withContext`。但
     * caller 链路里 `MessageCoordinationDelegate.sendUserMessage`（line ~292）
     * 在 `chatIdOverride.isNullOrBlank()` 分支会调 `coroutineScope.launch { ... }`
     * 派生新协程，**不继承** caller 的 `CoroutineContext.Element`（含我们包的
     * ThreadLocal 快照）—— 这是架构层的洞，靠 `withContext(sessionContextElement())`
     * 包不掉。修法是 C-route：把 origin 作为**显式参数**穿过 5 层接口
     * （StandardChatManagerTool → ChatServiceCore → MessageCoordinationDelegate
     * → MessageProcessingDelegate），到达 service-scope launch 边界另一侧后
     * 立刻**重新写入** ThreadLocal（见 TC-AGENT-045-i-7）。
     *
     * 本 TC 守 C-route 起点：`sendTool.parameters` 必须含这两个键。
     */
    @Test
    fun `TC-AGENT-045-i-4 injects origin params into sendTool`() {
        // 1) sendParams 构造点必须 append `__origin_platform=app` ToolParameter
        assertTrue(
            "prepareRequest() 必须给 sendParams 注入 `__origin_platform=\"app\"` ToolParameter —— " +
                "C-route 起点，绕过 service-scope launch 砍 CoroutineContext.Element 的架构洞。",
            Regex(
                """ToolParameter\s*\(\s*name\s*=\s*"__origin_platform"\s*,\s*value\s*=\s*"app"\s*\)"""
            ).containsMatchIn(source)
        )

        // 2) sendParams 构造点必须 append `__origin_chat_id=<resolved id>` ToolParameter
        //    宽松匹配 value 形式（变量 / 表达式都可），只校验键名。
        assertTrue(
            "prepareRequest() 必须给 sendParams 注入 `__origin_chat_id` ToolParameter（value 由 prepareRequest " +
                "内部解析的 currentChatId / request.chatId 提供）。",
            Regex("""ToolParameter\s*\(\s*name\s*=\s*"__origin_chat_id"""")
                .containsMatchIn(source)
        )

        // 3) 注入位置约束：必须在 `PreparationResult.Ready(...)` 之前 —— 即 sendParams
        //    最终被打包进 sendTool 之前注入。
        val readyIdx = Regex("""PreparationResult\.Ready\s*\(""").find(source)?.range?.first ?: -1
        val originPlatformIdx = Regex(
            """ToolParameter\s*\(\s*name\s*=\s*"__origin_platform""""
        ).find(source)?.range?.first ?: -1
        assertTrue(
            "`__origin_platform` ToolParameter 注入必须出现在 `PreparationResult.Ready(...)` 之前 —— " +
                "否则没进 sendTool。readyIdx=$readyIdx, originPlatformIdx=$originPlatformIdx",
            originPlatformIdx in 0 until readyIdx
        )
    }

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
