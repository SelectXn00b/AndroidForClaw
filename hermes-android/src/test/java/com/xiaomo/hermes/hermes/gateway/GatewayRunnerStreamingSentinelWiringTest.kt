package com.xiaomo.hermes.hermes.gateway

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-STREAMING-001 source-scan wiring tests for the `Run.kt`
 * (`GatewayRunner`) side of the streaming-sentinel mechanism.
 *
 * Each `@Test` corresponds to a TC-GW-STREAMING-001-x row in
 * `docs/hermes-test-cases.md`:
 *  - TC-GW-STREAMING-001-f → `STREAMING_DELIVERED_SENTINEL` const present
 *  - TC-GW-STREAMING-001-g → `_handleMessage` skips `deliveryRouter.deliverText`
 *    when the sentinel is returned
 *
 * **Source-scan rationale**: `_handleMessage` orchestrates session
 * acquisition, hook pipeline, agent runner, and delivery — fully wiring an
 * end-to-end behavioral test would require mocking the full Android
 * platform-adapter / session-store / hook-pipeline stack. Literal-level
 * source assertions are sufficient to lock in the proven structure.
 */
class GatewayRunnerStreamingSentinelWiringTest {

    private val source: String by lazy { stripKotlinComments(File(runPath()).readText()) }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-f: STREAMING_DELIVERED_SENTINEL companion const
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-f GatewayRunner declares STREAMING_DELIVERED_SENTINEL`() {
        assertTrue(
            "TC-GW-STREAMING-001-f: `GatewayRunner` companion must declare " +
                "`const val STREAMING_DELIVERED_SENTINEL` so callers (Android app's " +
                "HermesGatewayController) and `_handleMessage` itself agree on the " +
                "skip-delivery sentinel marker.",
            source.contains("STREAMING_DELIVERED_SENTINEL")
        )
        // Must live alongside INTERRUPTED_SENTINEL (same companion, same shape) —
        // a separate top-level / non-const declaration would not be reachable
        // from `GatewayRunner.STREAMING_DELIVERED_SENTINEL` callsites in app code.
        assertTrue(
            "TC-GW-STREAMING-001-f: `STREAMING_DELIVERED_SENTINEL` must be a `const val` " +
                "(allows referencing as `GatewayRunner.STREAMING_DELIVERED_SENTINEL` from " +
                "the Android app side without an instance).",
            source.contains("const val STREAMING_DELIVERED_SENTINEL")
        )
        // Distinct from INTERRUPTED — confuse the two and we leak the wrong skip
        // semantics across paths.
        assertTrue(
            "TC-GW-STREAMING-001-f: sentinel value must be the literal " +
                "`\\u0000__STREAMING_DELIVERED__` so it cannot collide with any real " +
                "agent reply text.",
            source.contains("__STREAMING_DELIVERED__")
        )
        assertFalse(
            "TC-GW-STREAMING-001-f: must NOT reuse the INTERRUPTED sentinel's literal " +
                "`__INTERRUPTED__` for the streaming-delivered sentinel.",
            source.contains("STREAMING_DELIVERED_SENTINEL = \"\\u0000__INTERRUPTED__\"")
        )
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-001-g: _handleMessage skips deliverText on sentinel
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-001-g _handleMessage skips deliverText when sentinel returned`() {
        // The skip-delivery guard must reference both sentinels. We're not
        // asserting on exact form (`!= INTERRUPTED && != STREAMING_DELIVERED`
        // vs. `responseText !in setOf(...)`) — just that BOTH sentinels gate
        // the delivery block.
        assertTrue(
            "TC-GW-STREAMING-001-g: `_handleMessage` must reference `INTERRUPTED_SENTINEL` " +
                "in its delivery-guard condition.",
            source.contains("INTERRUPTED_SENTINEL")
        )
        assertTrue(
            "TC-GW-STREAMING-001-g: `_handleMessage` must reference " +
                "`STREAMING_DELIVERED_SENTINEL` in its delivery-guard condition so the " +
                "fallback `deliveryRouter.deliverText(...)` is skipped when the sidecar " +
                "has already streamed the reply paragraph-by-paragraph.",
            source.contains("STREAMING_DELIVERED_SENTINEL")
        )

        // The sentinel must appear BEFORE the deliverText call site (i.e. the
        // sentinel guards the call, not the other way around).
        val streamingIdx = source.indexOf("STREAMING_DELIVERED_SENTINEL")
        val deliverIdx = source.indexOf("deliveryRouter.deliverText")
        assertTrue(
            "TC-GW-STREAMING-001-g: `STREAMING_DELIVERED_SENTINEL` (idx=$streamingIdx) " +
                "must appear in source BEFORE the first `deliveryRouter.deliverText` call " +
                "(idx=$deliverIdx). Otherwise it cannot be guarding the call.",
            streamingIdx in 0 until deliverIdx
        )

        // The companion const declaration + a second usage inside the guard —
        // we expect at least 2 occurrences (declaration + at least one usage).
        val count = Regex("STREAMING_DELIVERED_SENTINEL").findAll(source).count()
        assertTrue(
            "TC-GW-STREAMING-001-g: `STREAMING_DELIVERED_SENTINEL` must appear at least " +
                "twice in `Run.kt` — once for the `const val` declaration in the companion " +
                "object and at least once in `_handleMessage`'s delivery guard. Found $count.",
            count >= 2
        )
    }

    // =====================================================================
    // helpers — mirror AgentStreamingSidecarShapeWiringTest
    // =====================================================================

    /**
     * Strip Kotlin `/* ... */` block comments and `// ...` line comments while
     * preserving newlines so failure messages stay meaningful and string
     * literals inside docstrings don't pollute literal-content checks.
     */
    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun runPath(): String {
        val candidate = File("src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("hermes-android/src/main/java/com/xiaomo/hermes/hermes/gateway/Run.kt")
        return alt.path
    }
}
