package com.ai.assistance.operit.ui.features.settings.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-009 (2026-06-14): 守住 [HermesGatewayCredentialsScreen] 已加 Telegram
 * `PlatformCredentialsCard` 卡片。
 *
 * Compose UI 行为测（点击 / 输入 / 持久化）deferred to 手测——app 模块当前没有
 * `androidx.compose.ui.test` 依赖，引入风险大于收益。源码扫描守住 wiring。
 *
 * 对应 TC-GW-009-c（详见 docs/hermes-test-cases.md）。
 */
class TelegramGatewayCredentialsScreenWiringTest {

    private val source: String by lazy { File(screenPath()).readText() }

    /**
     * TC-GW-009-c: 必须含第三张 `PlatformCredentialsCard` 用于 Telegram，
     * 引用 `PLATFORM_TELEGRAM` + `TELEGRAM_FIELDS`。
     */
    @Test
    fun `TC-GW-009-c Telegram card rendered alongside Feishu and Weixin`() {
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 必须含 `PLATFORM_TELEGRAM` 引用 —— " +
                "用于 platformEnabledFlow / savePlatformEnabled 调用。",
            source.contains("PLATFORM_TELEGRAM")
        )
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 必须含 `TELEGRAM_FIELDS` 引用 —— " +
                "Telegram 卡片的字段集（token + allowed_chat_ids），对齐 FEISHU_FIELDS / WEIXIN_FIELDS。",
            source.contains("TELEGRAM_FIELDS")
        )
        // 至少出现 3 次 PlatformCredentialsCard 调用（Feishu + Weixin + Telegram）
        val cardCallCount = Regex("""PlatformCredentialsCard\s*\(""").findAll(source).count()
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 必须含至少 3 处 `PlatformCredentialsCard(` 调用 " +
                "（Feishu + Weixin + Telegram，本轮新加 Telegram）。\n实际：$cardCallCount 处。",
            cardCallCount >= 3
        )
        // Feishu / Weixin 不被误删（红线守卫）
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 必须仍含 `PLATFORM_FEISHU` 引用。",
            source.contains("PLATFORM_FEISHU")
        )
        assertTrue(
            "HermesGatewayCredentialsScreen.kt 必须仍含 `PLATFORM_WEIXIN` 引用。",
            source.contains("PLATFORM_WEIXIN")
        )
    }

    private fun screenPath(): String {
        val candidates = listOf(
            "app/src/main/java/com/ai/assistance/operit/ui/features/settings/screens/HermesGatewayCredentialsScreen.kt",
            "src/main/java/com/ai/assistance/operit/ui/features/settings/screens/HermesGatewayCredentialsScreen.kt"
        )
        return candidates.firstOrNull { File(it).exists() }
            ?: error("Cannot locate HermesGatewayCredentialsScreen.kt — cwd=${File(".").absolutePath}")
    }
}
