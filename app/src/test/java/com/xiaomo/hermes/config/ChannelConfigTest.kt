package com.xiaomo.hermes.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Channel Config Persistence Tests
 *
 * Validates the channel config data classes (Slack, Telegram, WhatsApp, Signal)
 * have correct defaults, nullable behaviour in ChannelsConfig, and data class
 * copy (round-trip) semantics.
 */
class ChannelConfigTest {

    // ===== SlackChannelConfig defaults =====

    @Test
    fun slackConfig_defaults() {
        val config = SlackChannelConfig()
        assertFalse(config.enabled)
        assertEquals("", config.botToken)
        assertNull(config.appToken)
        assertNull(config.signingSecret)
        assertEquals("socket", config.mode)
        assertEquals("open", config.dmPolicy)
        assertEquals("open", config.groupPolicy)
        assertTrue(config.requireMention)
        assertNull(config.historyLimit)
        assertEquals("partial", config.streaming)
        assertNull(config.model)
    }

    @Test
    fun slackConfig_socketMode() {
        val config = SlackChannelConfig(
            enabled = true,
            botToken = "xoxb-test-token",
            appToken = "xapp-test",
            mode = "socket",
            dmPolicy = "allowlist",
            groupPolicy = "open",
            requireMention = false,
            historyLimit = 50,
            streaming = "off",
            model = "openrouter/claude-3-5-sonnet"
        )
        assertTrue(config.enabled)
        assertEquals("xoxb-test-token", config.botToken)
        assertEquals("xapp-test", config.appToken)
        assertEquals("socket", config.mode)
        assertEquals("allowlist", config.dmPolicy)
        assertFalse(config.requireMention)
        assertEquals(50, config.historyLimit)
        assertEquals("off", config.streaming)
        assertEquals("openrouter/claude-3-5-sonnet", config.model)
    }

    @Test
    fun slackConfig_httpMode() {
        val config = SlackChannelConfig(
            botToken = "xoxb-http",
            signingSecret = "secret123",
            mode = "http"
        )
        assertEquals("http", config.mode)
        assertEquals("secret123", config.signingSecret)
        assertNull(config.appToken)
    }

    // ===== TelegramChannelConfig defaults =====

    @Test
    fun telegramConfig_defaults() {
        val config = TelegramChannelConfig()
        assertFalse(config.enabled)
        assertEquals("", config.botToken)
        assertEquals("open", config.dmPolicy)
        assertEquals("open", config.groupPolicy)
        assertTrue(config.requireMention)
        assertNull(config.historyLimit)
        assertEquals("partial", config.streaming)
        assertNull(config.webhookUrl)
        assertNull(config.model)
    }

    @Test
    fun telegramConfig_customValues() {
        val config = TelegramChannelConfig(
            enabled = true,
            botToken = "bot123456:ABC-DEF",
            dmPolicy = "pairing",
            groupPolicy = "allowlist",
            requireMention = false,
            historyLimit = 100,
            streaming = "block",
            webhookUrl = "https://example.com/webhook",
            model = "anthropic/claude-3-5-sonnet-20241022"
        )
        assertTrue(config.enabled)
        assertEquals("bot123456:ABC-DEF", config.botToken)
        assertEquals("pairing", config.dmPolicy)
        assertEquals("allowlist", config.groupPolicy)
        assertFalse(config.requireMention)
        assertEquals(100, config.historyLimit)
        assertEquals("block", config.streaming)
        assertEquals("https://example.com/webhook", config.webhookUrl)
        assertEquals("anthropic/claude-3-5-sonnet-20241022", config.model)
    }

    // ===== WhatsAppChannelConfig defaults =====

    @Test
    fun whatsappConfig_defaults() {
        val config = WhatsAppChannelConfig()
        assertFalse(config.enabled)
        assertEquals("", config.phoneNumber)
        assertEquals("open", config.dmPolicy)
        assertEquals("open", config.groupPolicy)
        assertTrue(config.requireMention)
        assertNull(config.historyLimit)
        assertNull(config.model)
    }

    @Test
    fun whatsappConfig_customValues() {
        val config = WhatsAppChannelConfig(
            enabled = true,
            phoneNumber = "+8613800138000",
            dmPolicy = "pairing",
            groupPolicy = "open",
            requireMention = false,
            historyLimit = 30,
            model = "openai/gpt-4o"
        )
        assertTrue(config.enabled)
        assertEquals("+8613800138000", config.phoneNumber)
        assertEquals(30, config.historyLimit)
        assertEquals("openai/gpt-4o", config.model)
    }

    // ===== SignalChannelConfig defaults =====

    @Test
    fun signalConfig_defaults() {
        val config = SignalChannelConfig()
        assertFalse(config.enabled)
        assertEquals("", config.phoneNumber)
        assertNull(config.httpUrl)
        assertEquals(8080, config.httpPort)
        assertEquals("open", config.dmPolicy)
        assertEquals("open", config.groupPolicy)
        assertTrue(config.requireMention)
        assertNull(config.historyLimit)
        assertNull(config.model)
    }

    @Test
    fun signalConfig_customValues() {
        val config = SignalChannelConfig(
            enabled = true,
            phoneNumber = "+8613800138001",
            httpUrl = "http://127.0.0.1:8080",
            httpPort = 8080,
            dmPolicy = "allowlist",
            groupPolicy = "open",
            requireMention = true,
            historyLimit = 20,
            model = "deepseek/deepseek-chat"
        )
        assertTrue(config.enabled)
        assertEquals("+8613800138001", config.phoneNumber)
        assertEquals("http://127.0.0.1:8080", config.httpUrl)
        assertEquals(8080, config.httpPort)
        assertEquals(20, config.historyLimit)
        assertEquals("deepseek/deepseek-chat", config.model)
    }

    // ===== ChannelsConfig nullable fields =====

    @Test
    fun channelsConfig_nullableByDefault() {
        val channels = ChannelsConfig()
        assertNull(channels.slack)
        assertNull(channels.telegram)
        assertNull(channels.whatsapp)
        assertNull(channels.signal)
        assertNotNull(channels.feishu)
        assertNull(channels.discord)
    }

    @Test
    fun channelsConfig_includesAllChannels() {
        val channels = ChannelsConfig(
            slack = SlackChannelConfig(enabled = true, botToken = "xoxb-test"),
            telegram = TelegramChannelConfig(enabled = true, botToken = "bot123"),
            whatsapp = WhatsAppChannelConfig(enabled = true, phoneNumber = "+1234567890"),
            signal = SignalChannelConfig(enabled = true, phoneNumber = "+0987654321")
        )
        assertNotNull(channels.slack)
        assertTrue(channels.slack!!.enabled)
        assertEquals("xoxb-test", channels.slack!!.botToken)

        assertNotNull(channels.telegram)
        assertTrue(channels.telegram!!.enabled)
        assertEquals("bot123", channels.telegram!!.botToken)

        assertNotNull(channels.whatsapp)
        assertTrue(channels.whatsapp!!.enabled)
        assertEquals("+1234567890", channels.whatsapp!!.phoneNumber)

        assertNotNull(channels.signal)
        assertTrue(channels.signal!!.enabled)
        assertEquals("+0987654321", channels.signal!!.phoneNumber)
    }

    // ===== Data class copy round-trip =====

    @Test
    fun slackConfig_copyRoundTrip() {
        val original = SlackChannelConfig(enabled = true, botToken = "xoxb-abc")
        val copied = original.copy(dmPolicy = "allowlist")
        assertEquals("xoxb-abc", copied.botToken)
        assertTrue(copied.enabled)
        assertEquals("allowlist", copied.dmPolicy)
        assertEquals("open", original.dmPolicy)
    }

    @Test
    fun channelsConfig_copyRoundTrip() {
        val original = ChannelsConfig(
            slack = SlackChannelConfig(enabled = true, botToken = "xoxb-orig")
        )
        val updated = original.copy(
            telegram = TelegramChannelConfig(enabled = true, botToken = "bot-new")
        )
        assertEquals("xoxb-orig", updated.slack!!.botToken)
        assertEquals("bot-new", updated.telegram!!.botToken)
        assertNull(original.telegram)
    }

    @Test
    fun openClawConfig_channelsDefault() {
        val config = OpenClawConfig()
        assertNull(config.channels.slack)
        assertNull(config.channels.telegram)
        assertNull(config.channels.whatsapp)
        assertNull(config.channels.signal)
    }

    // ===== Data class equality =====

    @Test
    fun slackConfig_equality() {
        val a = SlackChannelConfig(enabled = true, botToken = "tok")
        val b = SlackChannelConfig(enabled = true, botToken = "tok")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun telegramConfig_equality() {
        val a = TelegramChannelConfig(enabled = false, botToken = "bot1")
        val b = TelegramChannelConfig(enabled = false, botToken = "bot1")
        assertEquals(a, b)
    }

    @Test
    fun whatsappConfig_inequality() {
        val a = WhatsAppChannelConfig(phoneNumber = "+111")
        val b = WhatsAppChannelConfig(phoneNumber = "+222")
        assertNotEquals(a, b)
    }
}
