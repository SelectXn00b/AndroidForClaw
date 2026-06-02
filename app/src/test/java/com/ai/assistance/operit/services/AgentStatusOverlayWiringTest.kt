package com.ai.assistance.operit.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-003 — Gateway agent 运行时悬浮球 wiring 契约。
 *
 * 该 service 在 JVM Robolectric 下完整启动很脆弱（依赖 WindowManager / canDrawOverlays /
 * Compose ViewTreeOwner / startForeground 通知通道等），且核心 bug 是"接入丢失"
 * —— 已经测过的代码反复回归比 Service 行为漂移更危险。所以本测试类用**源码字符串扫描**
 * 把 5 个接入点（manifest + 3 处 wiring + 2 处 bus emit）作为活契约固化下来。
 *
 * 对应 TC-UI-070..073（见 docs/hermes-test-cases.md）。Service 本身的事件循环行为
 * （ProcessingStarted → show / Completed → hide / Failed → flash / setOverlayVisible）
 * 由 [AgentStatusOverlayService] 内部 collect 直接转给 [com.ai.assistance.operit.services.floating.AgentStatusOverlayManager]
 * 的方法调用，类型已被 Kotlin 编译器验证；本契约扫描确保订阅本身没掉。
 */
class AgentStatusOverlayWiringTest {

    /** TC-UI-073-a: AndroidManifest 注册了 AgentStatusOverlayService。 */
    @Test
    fun `TC-UI-073-a manifest registers AgentStatusOverlayService`() {
        val manifest = File(manifestPath()).readText()
        assertTrue(
            "AndroidManifest.xml 缺失 AgentStatusOverlayService 注册（R-UI-003 回归）",
            manifest.contains("android:name=\".services.AgentStatusOverlayService\"")
        )
        assertTrue(
            "AgentStatusOverlayService 必须声明 foregroundServiceType=dataSync",
            Regex(
                "<service[^>]*android:name=\"\\.services\\.AgentStatusOverlayService\"[^>]*android:foregroundServiceType=\"dataSync\"",
                RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(manifest) ||
                // 属性顺序可能颠倒
                Regex(
                    "<service[^>]*android:foregroundServiceType=\"dataSync\"[^>]*android:name=\"\\.services\\.AgentStatusOverlayService\"",
                    RegexOption.DOT_MATCHES_ALL
                ).containsMatchIn(manifest)
        )
        assertTrue(
            "AgentStatusOverlayService 必须 exported=false",
            Regex(
                "<service[^>]*android:name=\"\\.services\\.AgentStatusOverlayService\"[^>]*android:exported=\"false\"",
                RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(manifest) ||
                Regex(
                    "<service[^>]*android:exported=\"false\"[^>]*android:name=\"\\.services\\.AgentStatusOverlayService\"",
                    RegexOption.DOT_MATCHES_ALL
                ).containsMatchIn(manifest)
        )
    }

    /** TC-UI-073-b: GatewayForegroundService 必须在 onCreate/onDestroy 启停 overlay。 */
    @Test
    fun `TC-UI-073-b GatewayForegroundService starts and stops overlay service`() {
        val source = File(gatewayForegroundServicePath()).readText()
        assertTrue(
            "GatewayForegroundService 缺失 AgentStatusOverlayService.start() 联动（R-UI-003 回归）",
            source.contains("AgentStatusOverlayService.start(this)")
        )
        assertTrue(
            "GatewayForegroundService 缺失 AgentStatusOverlayService.stop() 联动（R-UI-003 回归）",
            source.contains("AgentStatusOverlayService.stop(this)")
        )
    }

    /** TC-UI-073-c: EnhancedAIService 必须 emit 到两个 bus，否则小球只能 show/hide 没细粒度状态。 */
    @Test
    fun `TC-UI-073-c EnhancedAIService emits to AgentEventBus and AgentTokenBus`() {
        val source = File(enhancedAIServicePath()).readText()
        assertTrue(
            "EnhancedAIService 缺失 AgentEventBus.emit() — 悬浮球状态文字不会更新（R-UI-003 回归）",
            source.contains("AgentEventBus.emit(")
        )
        assertTrue(
            "EnhancedAIService 缺失 AgentTokenBus.emit() — 悬浮球 token 计数不会更新（R-UI-003 回归）",
            source.contains("AgentTokenBus.emit(")
        )
        // 必须 import 才能调到
        assertTrue(
            "EnhancedAIService 缺失 AgentEventBus 的 import",
            source.contains("import com.ai.assistance.operit.hermes.gateway.AgentEventBus")
        )
        assertTrue(
            "EnhancedAIService 缺失 AgentTokenBus 的 import",
            source.contains("import com.ai.assistance.operit.hermes.gateway.AgentTokenBus")
        )
    }

    /** TC-UI-073-d: HermesAdapter（gateway 路径）必须 emit AgentEventBus。 */
    @Test
    fun `TC-UI-073-d HermesAdapter emits to AgentEventBus`() {
        val source = File(hermesAdapterPath()).readText()
        assertTrue(
            "HermesAdapter 缺失 AgentEventBus.emit(chatId, event) — gateway 链路悬浮球不会更新（R-UI-003 回归）",
            source.contains("AgentEventBus.emit(")
        )
        assertTrue(
            "HermesAdapter 缺失 AgentEventBus 的 import",
            source.contains("import com.ai.assistance.operit.hermes.gateway.AgentEventBus")
        )
    }

    /** TC-UI-073-e: ToolRegistration 在 UI 工具运行期必须临时藏掉小球。 */
    @Test
    fun `TC-UI-073-e ToolRegistration hides overlay during UI tool execution`() {
        val source = File(toolRegistrationPath()).readText()
        assertTrue(
            "ToolRegistration 缺失 AgentStatusOverlayService.getInstance() 引用 — UI 工具运行时小球会挡住 agent 操作（R-UI-003 回归）",
            source.contains("AgentStatusOverlayService.getInstance()")
        )
        assertTrue(
            "ToolRegistration 缺失 setOverlayVisible(false) — 进入 UI 工具前未藏小球（R-UI-003 回归）",
            source.contains("setOverlayVisible(false)")
        )
        assertTrue(
            "ToolRegistration 缺失 setOverlayVisible(true) — UI 工具结束后未复原小球（R-UI-003 回归）",
            source.contains("setOverlayVisible(true)")
        )
    }

    /**
     * TC-UI-070/071/072 的行为契约：AgentStatusOverlayService 必须真订阅了三个 bus，
     * 否则即便 wiring 都对、生命周期也对，运行起来小球不会动。
     *
     * 用源码扫描而不是真启服务的理由：Service onCreate 强依赖 WindowManager + Settings.canDrawOverlays
     * + startForeground 通知通道，Robolectric 下要么 mock 一堆 system service 要么直接 crash —
     * 没收益。这里的契约扫描捕捉同一类回归（"忘了订阅"）。
     */
    @Test
    fun `TC-UI-070-a service subscribes to GatewayChatEventBus`() {
        val source = File(overlayServicePath()).readText()
        assertTrue(
            "AgentStatusOverlayService 必须 collect GatewayChatEventBus.events",
            source.contains("GatewayChatEventBus.events.collect")
        )
        // 三种事件分支都要存在
        assertTrue(
            "AgentStatusOverlayService 必须处理 ProcessingStarted（show 路径）",
            source.contains("GatewayChatEventBus.Event.ProcessingStarted")
        )
        assertTrue(
            "AgentStatusOverlayService 必须处理 ProcessingCompleted（hide 路径）",
            source.contains("GatewayChatEventBus.Event.ProcessingCompleted")
        )
        assertTrue(
            "AgentStatusOverlayService 必须处理 ProcessingFailed（错误闪烁路径）",
            source.contains("GatewayChatEventBus.Event.ProcessingFailed")
        )
    }

    @Test
    fun `TC-UI-072-a service subscribes to AgentEventBus and AgentTokenBus`() {
        val source = File(overlayServicePath()).readText()
        assertTrue(
            "AgentStatusOverlayService 必须 collect AgentEventBus.events（拿 turn / 工具名）",
            source.contains("AgentEventBus.events.collect")
        )
        assertTrue(
            "AgentStatusOverlayService 必须 collect AgentTokenBus.usage（拿 token 累计）",
            source.contains("AgentTokenBus.usage.collect")
        )
    }

    @Test
    fun `TC-UI-071-a service exposes setOverlayVisible for ToolRegistration`() {
        val source = File(overlayServicePath()).readText()
        assertTrue(
            "AgentStatusOverlayService 必须暴露 setOverlayVisible(Boolean) 供 UI 工具临时隐藏",
            source.contains("fun setOverlayVisible(visible: Boolean)")
        )
        assertTrue(
            "AgentStatusOverlayService 必须暴露 isOverlayShowing 供 ToolRegistration 决策",
            source.contains("val isOverlayShowing:")
        )
        assertTrue(
            "AgentStatusOverlayService.getInstance() 必须存在（ToolRegistration 同步访问点）",
            source.contains("fun getInstance(): AgentStatusOverlayService?")
        )
    }

    /** 反向防呆：source 里不应包含已知 typo / 旧 API 残留。 */
    @Test
    fun `regression guard manifest does not double register service`() {
        val manifest = File(manifestPath()).readText()
        val count = Regex(
            "android:name=\"\\.services\\.AgentStatusOverlayService\""
        ).findAll(manifest).count()
        assertTrue(
            "AndroidManifest 中 AgentStatusOverlayService 应只注册一次，实际 $count",
            count == 1
        )
        assertFalse(
            "AgentStatusOverlayService 不应被设置 exported=true（system overlay 仅本应用使用）",
            Regex(
                "<service[^>]*android:name=\"\\.services\\.AgentStatusOverlayService\"[^>]*android:exported=\"true\"",
                RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(manifest)
        )
    }

    // ----- helpers -----

    private fun appSrcMainRoot(): File {
        // Tests run with working dir = module root (HermesApp/app).
        val candidate = File("src/main/java/com/ai/assistance/operit")
        if (candidate.exists()) return candidate
        // Fallback for running from project root.
        val alt = File("app/src/main/java/com/ai/assistance/operit")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main/java/com/ai/assistance/operit — cwd=${File(".").absolutePath}")
    }

    private fun appMainRoot(): File {
        val candidate = File("src/main")
        if (candidate.exists()) return candidate
        val alt = File("app/src/main")
        if (alt.exists()) return alt
        error("Cannot locate app/src/main — cwd=${File(".").absolutePath}")
    }

    private fun manifestPath(): String = File(appMainRoot(), "AndroidManifest.xml").path

    private fun overlayServicePath(): String =
        File(appSrcMainRoot(), "services/AgentStatusOverlayService.kt").path

    private fun gatewayForegroundServicePath(): String =
        File(appSrcMainRoot(), "services/gateway/GatewayForegroundService.kt").path

    private fun enhancedAIServicePath(): String =
        File(appSrcMainRoot(), "api/chat/EnhancedAIService.kt").path

    private fun hermesAdapterPath(): String =
        File(appSrcMainRoot(), "hermes/HermesAdapter.kt").path

    private fun toolRegistrationPath(): String =
        File(appSrcMainRoot(), "core/tools/ToolRegistration.kt").path
}
