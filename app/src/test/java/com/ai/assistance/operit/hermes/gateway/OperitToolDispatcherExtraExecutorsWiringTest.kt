package com.ai.assistance.operit.hermes.gateway

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-GW-STREAMING-002 source-scan wiring test for
 * `OperitToolDispatcher.extraExecutors` (`app/.../hermes/OperitToolDispatcher.kt`).
 *
 * Covers TC-GW-STREAMING-002-d: dispatcher routes `extraExecutors` before
 * built-in tools.
 *
 * `extraExecutors` is the channel through which the gateway-only
 * `send_message` tool's executor is plumbed into the `HermesAgentLoop`
 * tool-dispatch path WITHOUT touching the global OperitTool registry (which
 * would leak the gateway-only tool into APP-UI paths).
 *
 * Required surface:
 *   - constructor (or builder) parameter named `extraExecutors`
 *   - typed as a nullable `Map<String, suspend (Map<String, Any?>) -> String>`
 *     (or compatible — name `extraExecutors`, value-type suspending function
 *     returning String)
 *   - dispatch path consults `extraExecutors[toolName]` BEFORE looking up
 *     the built-in OperitTool registry; if hit, the extra executor wins
 */
class OperitToolDispatcherExtraExecutorsWiringTest {

    private val source: String by lazy {
        stripKotlinComments(File(dispatcherPath()).readText())
    }

    // ---------------------------------------------------------------------
    // TC-GW-STREAMING-002-d: dispatcher routes extraExecutors before built-in tools
    // ---------------------------------------------------------------------
    @Test
    fun `TC-GW-STREAMING-002-d dispatcher routes extraExecutors before built-in tools`() {
        // (1) `extraExecutors` literal exists in source.
        assertTrue(
            "TC-GW-STREAMING-002-d: `OperitToolDispatcher.kt` must declare an " +
                "`extraExecutors` field/parameter — the channel through which " +
                "the gateway-only `send_message` executor is plumbed in.",
            source.contains("extraExecutors")
        )

        // (2) Type signature must include `Map<String,` (the map keyed by
        //     tool name). We can't tightly assert the full functional type
        //     in literal form (could span lines), so we accept either of
        //     the two common forms.
        val extraIdx = source.indexOf("extraExecutors")
        val mapIdx = source.indexOf("Map<String,", extraIdx.coerceAtLeast(0))
        assertTrue(
            "TC-GW-STREAMING-002-d: `extraExecutors` must be typed as " +
                "`Map<String, ...>` so it's keyed by tool name. The first " +
                "`Map<String,` after `extraExecutors` (idx=$mapIdx, vs " +
                "extraExecutors idx=$extraIdx) must appear within 400 chars " +
                "of the `extraExecutors` declaration.",
            mapIdx in extraIdx..(extraIdx + 400)
        )

        // (3) Dispatch hot-path consults extraExecutors. Two acceptable
        //     literal forms: `extraExecutors[name]` or `extraExecutors?.get(`
        //     or `extraExecutors.containsKey(`.  Any one is enough.
        val consultsExtras =
            source.contains("extraExecutors[") ||
                source.contains("extraExecutors?.get(") ||
                source.contains("extraExecutors.get(") ||
                source.contains("extraExecutors.containsKey(") ||
                source.contains("extraExecutors?.containsKey(")
        assertTrue(
            "TC-GW-STREAMING-002-d: dispatch hot-path must consult " +
                "`extraExecutors` via `[name]` / `?.get(` / `.containsKey(` " +
                "form. Without this lookup, the gateway-only `send_message` " +
                "tool would never be invoked even though it appears in the " +
                "schema.",
            consultsExtras
        )
    }

    /**
     * Strip Kotlin `/* ... */` block comments and `// ...` line comments while
     * preserving newlines.
     */
    private fun stripKotlinComments(text: String): String {
        val noBlock = Regex("""/\*[\s\S]*?\*/""").replace(text) { m ->
            m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
        return Regex("""//[^\n]*""").replace(noBlock) { m ->
            " ".repeat(m.value.length)
        }
    }

    private fun dispatcherPath(): String {
        val candidate = File("src/main/java/com/ai/assistance/operit/hermes/OperitToolDispatcher.kt")
        if (candidate.parentFile?.exists() == true) return candidate.path
        val alt = File("app/src/main/java/com/ai/assistance/operit/hermes/OperitToolDispatcher.kt")
        return alt.path
    }
}
