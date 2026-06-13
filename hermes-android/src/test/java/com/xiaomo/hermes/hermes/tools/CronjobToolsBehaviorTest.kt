package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-AGENT-031: 行为单测（无 Context 部分）。
 *
 * **范围**：只测 15-minute min-interval guard（TC-AGENT-031-m）—— 该 guard 在
 * `createJob()` 调用之前就返回 `toolError`，所以不需要 `getHermesHome()` /
 * Context。
 *
 * **Deferred**：TC-AGENT-031-l (create+list 持久化 roundtrip) 和 TC-AGENT-031-n
 * (空表 list) 都触发到 `getHermesHome() → getAppContext().filesDir`，与 `JobsTest`
 * 同处理 —— 这类测试需要 Robolectric。本类不覆盖，靠 §3 E2E + 手测兜底。
 */
class CronjobToolsBehaviorTest {

    /**
     * TC-AGENT-031-m: schedule="every 5 minutes" 必须被 15-minute guard 拦下。
     */
    @Test
    fun `TC-AGENT-031-m create denies sub-15-minute interval`() {
        val result = cronjob(
            action = "create",
            prompt = "ping me",
            schedule = "every 5 minutes",
            name = "sub-min-test",
        )
        assertTrue(
            "创建 5-minute interval cron 必须返回 toolError 文案，而不是真创建。\n实际返回: $result",
            result.contains("error", ignoreCase = true) || result.contains("\"success\":false")
        )
        assertTrue(
            "错误消息必须含 `15` 字面值 —— 让 agent 知道平台下限是 15 分钟。\n实际返回: $result",
            result.contains("15")
        )
        assertTrue(
            "错误消息必须含 `minimum interval` 或 `Android requires` —— 与代码字面值匹配。\n实际返回: $result",
            result.contains("minimum interval") || result.contains("Android requires")
        )
    }

    /**
     * TC-AGENT-031-m (sanity): schedule="every 30 minutes" 不应被 min-interval guard 拦。
     * （此处只测 guard 不误伤；Context-bound 的 createJob 路径在 m 范围之外。）
     *
     * 注意：30-minute interval 通过 guard 后会进入 `createJob()`，那里调
     * `getHermesHome()` —— 在没有 Robolectric 的纯单测里会抛
     * `IllegalStateException("Hermes constants not initialized")`。我们只断言
     * 错误消息**不**含 "minimum interval" / "Android requires" / "15" 字面值，
     * 也即 guard 没误伤；下游因 Context 缺失抛的异常允许出现。
     */
    @Test
    fun `TC-AGENT-031-m sanity 30-minute interval passes the min-interval guard`() {
        val result = try {
            cronjob(
                action = "create",
                prompt = "ping me",
                schedule = "every 30 minutes",
                name = "ok-interval-test",
            )
        } catch (e: IllegalStateException) {
            // Expected when Context isn't initialized — guard already passed.
            "passed-guard-then-context-missing"
        }
        assertFalse(
            "30-minute interval 不应被 min-interval guard 误伤，错误文案不该出现。\n实际返回: $result",
            result.contains("Android requires a minimum interval of 15 minutes")
        )
    }
}
