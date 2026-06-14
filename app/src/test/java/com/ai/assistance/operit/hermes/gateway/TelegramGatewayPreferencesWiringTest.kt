package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-009 (2026-06-14): 守住 [HermesGatewayPreferences] 已加 Telegram 平台常量与字段集。
 *
 * 用源码扫描而非反射——`PLATFORM_TELEGRAM` / `TELEGRAM_FIELDS` 这种字面值是 wiring 链路的
 * 锚点，比反射 `companion object`（容易因 ProGuard / 字段类型差异飘）更稳。
 *
 * 对应 TC-GW-009-a（详见 docs/hermes-test-cases.md）。
 */
class TelegramGatewayPreferencesWiringTest {

    private val source: String by lazy { File(preferencesPath()).readText() }

    /**
     * TC-GW-009-a: HermesGatewayPreferences.kt 必须含 `PLATFORM_TELEGRAM` 平台常量
     * 与 token / allowed_chat_ids 两个字段名字面值——对齐 Feishu / Weixin 的接线模式。
     */
    @Test
    fun `TC-GW-009-a Telegram platform constants exist`() {
        assertTrue(
            "HermesGatewayPreferences.kt 必须含 `PLATFORM_TELEGRAM` 常量声明 —— " +
                "值为 \"telegram\"，对齐 Python Platform.TELEGRAM.value。",
            Regex("""\bPLATFORM_TELEGRAM\s*=\s*"telegram"""").containsMatchIn(source)
        )
        // Telegram secret 字段：token（必填）
        assertTrue(
            "HermesGatewayPreferences.kt 必须含 `SECRET_TELEGRAM_TOKEN` 或等价的 " +
                "`\"token\"` Telegram 字段名常量 —— 让 ConfigBuilder / Screen 共用同一个 key。",
            source.contains("SECRET_TELEGRAM_TOKEN") ||
                Regex("""\bTELEGRAM[_A-Z]*\s*=\s*"token"""").containsMatchIn(source)
        )
        // Telegram secret 字段：allowed_chat_ids（可选 chat 白名单）
        assertTrue(
            "HermesGatewayPreferences.kt 必须含 `allowed_chat_ids` 字段名字面值 —— " +
                "Telegram chat 白名单，对齐 Python `gateway/run.py` platforms.telegram。",
            source.contains("\"allowed_chat_ids\"")
        )
        // Feishu / Weixin 接线不被误改（红线守卫）
        assertTrue(
            "HermesGatewayPreferences.kt 必须仍含 `PLATFORM_FEISHU` 常量。",
            source.contains("PLATFORM_FEISHU")
        )
        assertTrue(
            "HermesGatewayPreferences.kt 必须仍含 `PLATFORM_WEIXIN` 常量。",
            source.contains("PLATFORM_WEIXIN")
        )
    }

    private fun preferencesPath(): String {
        // 同时支持 cwd=HermesApp 与 cwd=HermesApp/app 两种执行场景。
        val candidates = listOf(
            "app/src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayPreferences.kt",
            "src/main/java/com/ai/assistance/operit/hermes/gateway/HermesGatewayPreferences.kt"
        )
        return candidates.firstOrNull { File(it).exists() }
            ?: error("Cannot locate HermesGatewayPreferences.kt — cwd=${File(".").absolutePath}")
    }
}
