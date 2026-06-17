package com.xiaomo.hermes.hermes.tools

import com.xiaomo.hermes.hermes.gateway.clearSessionVars
import com.xiaomo.hermes.hermes.gateway.setSessionVars
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-AGENT-045: 当 `cronjob(action="create")` 在 in-app chat 触发时，
 * `_originFromEnv()` 必须从 ThreadLocal 读到 `platform="app"` + `chat_id=<chatId>`，
 * 把 origin 写进 jobs.json，让 cron 跑完能定位回原 chat。
 *
 * 这是 R-AGENT-033 ThreadLocal 路径的覆盖延伸 —— `_originFromEnv()` 本身已经
 * 会从 `getSessionEnv("HERMES_SESSION_PLATFORM")` 读，唯一新增需求是 platform
 * sentinel `"app"` 在 R-AGENT-045 wrap 之后会被正确写入 ThreadLocal、
 * 进而被 _originFromEnv 读出 —— 即"打通"。
 *
 * 对应 TC-AGENT-045-f。
 */
class CronjobToolsAppOriginTest {

    @After
    fun tearDown() {
        clearSessionVars()
    }

    /**
     * TC-AGENT-045-f: 当 ThreadLocal 含 `(platform="app", chat_id="chat-123")` 时，
     * `_originFromEnv()` 返回 `mapOf("platform"="app", "chat_id"="chat-123", ...)`。
     */
    @Test
    fun `TC-AGENT-045-f originFromEnv reads app session origin from ThreadLocal`() {
        setSessionVars(
            platform = "app",
            chatId = "chat-123",
            chatName = "My Chat",
            threadId = ""
        )
        val origin = _originFromEnv()
        assertNotNull(
            "_originFromEnv() 必须返回非空 map —— ThreadLocal 已含 platform=app + chat_id=chat-123，" +
                "应被识别为有效 origin。",
            origin
        )
        assertEquals(
            "_originFromEnv() 必须读到 platform=\"app\" —— in-app sentinel 必须被透传。",
            "app", origin!!["platform"]
        )
        assertEquals(
            "_originFromEnv() 必须读到 chat_id=\"chat-123\" —— in-app chat id 必须被透传。",
            "chat-123", origin["chat_id"]
        )
        assertEquals(
            "_originFromEnv() 必须读到 chat_name=\"My Chat\"。",
            "My Chat", origin["chat_name"]
        )
        // thread_id="" 应被识别为 null（_originFromEnv 用 takeIf isNotEmpty）
        assertNull(
            "_originFromEnv() 在 thread_id=\"\" 时必须返回 null（空字符串不是有效 thread_id）。",
            origin["thread_id"]
        )
    }
}
