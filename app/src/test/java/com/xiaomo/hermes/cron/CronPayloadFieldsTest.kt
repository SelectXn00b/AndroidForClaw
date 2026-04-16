package com.xiaomo.hermes.cron

import org.junit.Assert.*
import org.junit.Test

/**
 * Verify CronPayload.AgentTurn fields are aligned with OpenClaw CronPayloadSchema.
 *
 * OpenClaw source: plugin-sdk/gateway/protocol/schema/cron.d.ts
 * Required fields: message
 * Optional fields: model, fallbacks, thinking, timeoutSeconds, deliver, channel, to,
 *                  bestEffortDeliver, lightContext, allowUnsafeExternalContent
 */
class CronPayloadFieldsTest {

    @Test
    fun `AgentTurn has all OpenClaw fields`() {
        val turn = CronPayload.AgentTurn(
            message = "test",
            model = "anthropic/claude-opus-4-6",
            fallbacks = listOf("openai/gpt-4o"),
            thinking = "high",
            timeoutSeconds = 120,
            deliver = true,
            channel = "telegram",
            to = "user123",
            bestEffortDeliver = true,
            lightContext = false,
            allowUnsafeExternalContent = false
        )

        assertEquals("test", turn.message)
        assertEquals("anthropic/claude-opus-4-6", turn.model)
        assertEquals(listOf("openai/gpt-4o"), turn.fallbacks)
        assertEquals("high", turn.thinking)
        assertEquals(120, turn.timeoutSeconds)
        assertEquals(true, turn.deliver)
        assertEquals("telegram", turn.channel)
        assertEquals("user123", turn.to)
        assertEquals(true, turn.bestEffortDeliver)
        assertEquals(false, turn.lightContext)
        assertEquals(false, turn.allowUnsafeExternalContent)
    }

    @Test
    fun `AgentTurn defaults all optional fields to null`() {
        val turn = CronPayload.AgentTurn(message = "test")

        assertNull(turn.model)
        assertNull(turn.fallbacks)
        assertNull(turn.thinking)
        assertNull(turn.timeoutSeconds)
        assertNull(turn.deliver)
        assertNull(turn.channel)
        assertNull(turn.to)
        assertNull(turn.bestEffortDeliver)
        assertNull(turn.lightContext)
        assertNull(turn.allowUnsafeExternalContent)
    }
}
