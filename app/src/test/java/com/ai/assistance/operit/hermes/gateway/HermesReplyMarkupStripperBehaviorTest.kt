package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TC-CRON-SANITIZE-b + c (R-AGENT-031 / R-AGENT-035): behavior tests for
 * `HermesReplyMarkupStripper.strip(...)`.  Pure JVM unit test (no
 * Android Context, no Robolectric) — invokes the function directly.
 *
 * Reflection-invocation pattern: this test must COMPILE even before the
 * SUT class exists (red-phase TDD).  Once SUT is created, behavior is
 * verified.  Locks the public API contract: Kotlin `object` with a
 * `strip(String): String` method (JVM-side: `INSTANCE` static field +
 * `strip` instance method).  See MEMORY-WIP-RED-TESTS.md for why we
 * don't import the SUT directly (compile poison anti-pattern).
 *
 * Covers:
 *  - (b) closed/unclosed `<think>` / `<thinking>` tags
 *  - (c) `<tool>` / `<tool_result>` / `<status>` (open + self-closing)
 */
class HermesReplyMarkupStripperBehaviorTest {

    /** Reflection invoke: `HermesReplyMarkupStripper.strip(input)`. */
    private fun strip(input: String): String {
        val cls = Class.forName(
            "com.ai.assistance.operit.hermes.gateway.HermesReplyMarkupStripper"
        )
        val instanceField = cls.getDeclaredField("INSTANCE")
        val instance = instanceField.get(null)
        val method = cls.getDeclaredMethod("strip", String::class.java)
        return method.invoke(instance, input) as String
    }

    @Test
    fun `TC-CRON-SANITIZE-b strips closed think tag and keeps reply`() {
        val output = strip("<think>foo</think>bar")
        assertEquals(
            "TC-CRON-SANITIZE-b: closed `<think>foo</think>bar` must yield `bar`.",
            "bar",
            output.trim()
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-b strips unclosed think tag to empty`() {
        // The model sometimes emits `<think>...` without closing tag (stream cutoff).
        // UNCLOSED_THINK_REGEX sweeps `<think...>` to end-of-string.
        val output = strip("<think>foo bar baz no close tag here")
        assertEquals(
            "TC-CRON-SANITIZE-b: unclosed `<think>...` must strip to empty string.",
            "",
            output.trim()
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-b strips thinking tag variant`() {
        val output = strip("<thinking>reason here</thinking>final answer")
        assertEquals(
            "TC-CRON-SANITIZE-b: `<thinking>...</thinking>` must also be stripped.",
            "final answer",
            output.trim()
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-b strips think prefix and preserves reply`() {
        // Matches the actual bug seen in production cron→Weixin delivery.
        val output = strip(
            "<think>The cron job triggered a reminder to drink water...</think>💧 喝水时间到！"
        )
        assertEquals(
            "TC-CRON-SANITIZE-b: real-world bug scenario must yield only user-visible reply.",
            "💧 喝水时间到！",
            output.trim()
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-c strips tool block`() {
        val output = strip("""<tool name="set_alarm" id="abc">{"hour": 9}</tool>reply text""")
        assertFalse(
            "TC-CRON-SANITIZE-c: `<tool ...>...</tool>` must be removed from output.\nActual: $output",
            output.contains("<tool")
        )
        assertTrue(
            "TC-CRON-SANITIZE-c: reply text must be preserved.",
            output.contains("reply text")
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-c strips tool_result block`() {
        val output = strip("""<tool_result name="x">{"ok":true}</tool_result>final""")
        assertFalse(
            "TC-CRON-SANITIZE-c: `<tool_result>...</tool_result>` must be removed.\nActual: $output",
            output.contains("<tool_result") || output.contains("</tool_result>")
        )
        assertTrue(
            "TC-CRON-SANITIZE-c: trailing reply must remain.",
            output.contains("final")
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-c strips status open tag`() {
        val output = strip("reply<status type=\"complete\">done</status>")
        assertFalse(
            "TC-CRON-SANITIZE-c: `<status type=\"complete\">...</status>` must be removed.\nActual: $output",
            output.contains("<status")
        )
        assertTrue(
            "TC-CRON-SANITIZE-c: leading reply must remain.",
            output.contains("reply")
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-c strips status self-closing tag`() {
        val output = strip("reply<status type=\"complete\"/>")
        assertFalse(
            "TC-CRON-SANITIZE-c: self-closing `<status .../>` must be removed.\nActual: $output",
            output.contains("<status")
        )
        assertTrue(
            "TC-CRON-SANITIZE-c: leading reply must remain.",
            output.contains("reply")
        )
    }

    @Test
    fun `TC-CRON-SANITIZE-c strips mixed think+tool+tool_result+status sequence`() {
        // Realistic interleaved markup mimicking cron headless stream output.
        val input = "<think>let me think</think>" +
            "<tool name=\"x\">args</tool>" +
            "<tool_result>res</tool_result>" +
            "user-visible reply" +
            "<status type=\"complete\"/>"
        val output = strip(input).trim()
        assertEquals(
            "TC-CRON-SANITIZE-c: mixed-markup input must yield only `user-visible reply`.\n" +
                "Got: <<<$output>>>",
            "user-visible reply",
            output
        )
    }
}
