package com.xiaomo.hermes.hermes.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TC-CRON-EXACT-h: `_parseIsoToEpoch` round-trips `Jobs.kt::formatIsoDate` output.
 *
 * **Bug under test (2026-06-23 second cron-exact bugfix)**: 67eecf1a's
 * `_maybeScheduleShortDelayAlarm` early-returned for every real cron job
 * because `_parseIsoToEpoch` could not parse the timestamps that
 * `Jobs.kt::formatIsoDate` writes to disk. Root cause:
 *
 * - `Jobs.kt::formatIsoDate` uses `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")`,
 *   which renders zone offsets in RFC822 form (`+0800`, no colon).
 * - The original `_parseIsoToEpoch` delegated to `java.time.Instant.parse`,
 *   which is strict ISO-8601 and **rejects** `+0800` (only accepts `Z` or
 *   `+08:00` with colon).
 *
 * Net effect: every `_parseIsoToEpoch(next_run_at)` returned `null` →
 * `_maybeScheduleShortDelayAlarm` early-returned → AlarmManager exact-alarm
 * route was never armed → cron jobs fell back to the 15-min PeriodicWork
 * tick, exactly the latency we shipped 67eecf1a to fix.
 *
 * Fix: try `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")` first (matches
 * Jobs.kt's own writer), then fall back to `Instant.parse` for ISO-8601
 * strict inputs (tests / upstream-style `Z` suffix).
 *
 * The test exercises the parser through the module-internal
 * [parseIsoToEpochInternal] alias — `_parseIsoToEpoch` itself is file-private.
 */
class CronjobToolsParseIsoEpochTest {

    /**
     * TC-CRON-EXACT-h primary: a string produced by the **exact** SimpleDateFormat
     * pattern Jobs.kt uses must round-trip back to the same epoch millis.
     *
     * This is the case that was failing before the fix: real `next_run_at`
     * values look like `2026-06-23T00:55:30.123+0800` and the old parser
     * returned null for them.
     */
    @Test
    fun `TC-CRON-EXACT-h _parseIsoToEpoch round-trips formatIsoDate output`() {
        // Mirror Jobs.kt::formatIsoDate exactly so we test the same surface.
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val originalMs = 1_734_900_930_123L // arbitrary fixed epoch ms
        val original = Date(originalMs)
        val iso = sdf.format(original)

        // Defensive: confirm the producer really emits the no-colon form
        // we're worried about. If this assert fails the test premise is
        // wrong, not the parser.
        assertNotNull(
            "Test premise: SimpleDateFormat('...SSSZ') should produce a string " +
                "with a numeric offset (e.g. +0800). Got: $iso",
            Regex("""[+-]\d{4}$""").find(iso)
        )

        val parsed = parseIsoToEpochInternal(iso)
        assertNotNull(
            "TC-CRON-EXACT-h: _parseIsoToEpoch must NOT return null for the " +
                "format Jobs.kt::formatIsoDate produces ($iso). This is the bug " +
                "67eecf1a missed — Instant.parse rejects +0800 offsets.",
            parsed
        )
        assertEquals(
            "TC-CRON-EXACT-h: round-tripped epoch must equal the original epoch.",
            originalMs,
            parsed
        )
    }

    /**
     * TC-CRON-EXACT-h fallback: ISO-8601 strict inputs (e.g. `...Z` suffix
     * from upstream-style writers, or test fixtures) must still parse via
     * the `Instant.parse` fallback path.
     */
    @Test
    fun `TC-CRON-EXACT-h _parseIsoToEpoch accepts ISO-8601 strict Z suffix`() {
        // 2024-12-22T20:55:30.123Z = 1_734_900_930_123 ms epoch
        val iso = "2024-12-22T20:55:30.123Z"
        val parsed = parseIsoToEpochInternal(iso)
        assertNotNull(
            "TC-CRON-EXACT-h: _parseIsoToEpoch must accept ISO-8601 strict 'Z' " +
                "suffix via the Instant.parse fallback.",
            parsed
        )
        assertEquals(
            "TC-CRON-EXACT-h: parsed epoch must match the canonical Z-suffix value.",
            1_734_900_930_123L,
            parsed
        )
    }

    /**
     * TC-CRON-EXACT-h null/blank inputs must keep returning null (defensive
     * behaviour from R-AGENT-044 — health snapshot scanners rely on this).
     */
    @Test
    fun `TC-CRON-EXACT-h _parseIsoToEpoch returns null for null or blank input`() {
        assertNull(
            "null input must round-trip to null (R-AGENT-044 health-scan invariant).",
            parseIsoToEpochInternal(null)
        )
        assertNull(
            "blank input must round-trip to null.",
            parseIsoToEpochInternal("")
        )
        assertNull(
            "whitespace-only input must round-trip to null.",
            parseIsoToEpochInternal("   ")
        )
    }

    /**
     * TC-CRON-EXACT-h garbage inputs must still return null rather than throw —
     * the AlarmManager bypass would otherwise crash the cron-create tool call.
     */
    @Test
    fun `TC-CRON-EXACT-h _parseIsoToEpoch returns null for malformed input`() {
        assertNull(
            "totally non-ISO input → null, no exception.",
            parseIsoToEpochInternal("not a timestamp")
        )
        assertNull(
            "almost-ISO but missing offset → null (not parseable by either path).",
            parseIsoToEpochInternal("2024-12-22T20:55:30")
        )
    }
}
