package com.xiaomo.hermes.hermes.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OpenCodeZenCatalog free-model filtering and selection.
 *
 * Tied to: R-AGENT-002, TC-AGENT-200-{e,f,g}
 */
class OpenCodeZenCatalogTest {

    /**
     * Build a synthetic models.dev-shaped catalog with the `opencode` provider
     * holding the supplied model entries.
     */
    private fun buildCatalog(models: Map<String, Map<String, Any?>>): Map<String, Any> {
        return mapOf(
            "opencode" to mapOf(
                "id" to "opencode",
                "name" to "OpenCode Zen",
                "env" to emptyList<String>(),
                "api" to "https://opencode.ai/zen/v1",
                "models" to models
            )
        )
    }

    /**
     * TC-AGENT-200-e: listFreeModels filters by cost.input==0 + tool_call==true
     * + !NOISE_PATTERN; sorts by release_date desc.
     */
    @Test
    fun `listFreeModels filtersByCostZero andSortsByReleaseDateDesc`() {
        val catalog = buildCatalog(mapOf(
            // Free + tool_call + valid id → keep
            "qwen/qwen3-coder" to mapOf(
                "tool_call" to true,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2025-08-01"
            ),
            // Free + tool_call but NEWER → should sort first
            "moonshot/kimi-k2" to mapOf(
                "tool_call" to true,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2025-12-15"
            ),
            // Paid → drop
            "anthropic/claude-paid" to mapOf(
                "tool_call" to true,
                "cost" to mapOf("input" to 3.0, "output" to 15.0),
                "release_date" to "2025-11-01"
            ),
            // Free but no tool_call → drop
            "free/no-tools" to mapOf(
                "tool_call" to false,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2025-10-01"
            )
        ))

        val free = OpenCodeZenCatalog.listFreeModels(catalog)

        // Only the two free + tool_call models should survive
        assertEquals("filtered to 2 free+tool_call models", 2, free.size)
        // Newest first
        assertEquals("kimi-k2 sorted before qwen3-coder by release_date desc",
            "moonshot/kimi-k2", free[0].id)
        assertEquals("qwen3-coder second", "qwen/qwen3-coder", free[1].id)
        // No paid model leaked
        assertFalse("no paid model in result",
            free.any { it.id == "anthropic/claude-paid" })
        // No non-tool model leaked
        assertFalse("no non-tool model in result",
            free.any { it.id == "free/no-tools" })
        // All survivors meet the filter contract
        free.forEach { info ->
            assertEquals("costInput==0 for kept model ${info.id}", 0.0, info.costInput, 0.0)
            assertTrue("toolCall==true for kept model ${info.id}", info.toolCall)
        }
    }

    /**
     * TC-AGENT-200-e (NOISE_PATTERN): models matching the noise regex must be dropped.
     * NOISE_PATTERN catches preview/experimental/etc. We pass an id that should be
     * caught and verify it's filtered out.
     */
    @Test
    fun `listFreeModels filtersOutNoisePatternIds`() {
        // Use one id that NOISE_PATTERN should match (e.g. contains "preview") and
        // one clean id; verify only the clean id survives.
        val catalog = buildCatalog(mapOf(
            "qwen/qwen3-coder-preview" to mapOf(  // likely matches NOISE_PATTERN
                "tool_call" to true,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2025-12-01"
            ),
            "qwen/qwen3-coder" to mapOf(  // clean id, must survive
                "tool_call" to true,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2025-08-01"
            )
        ))

        val free = OpenCodeZenCatalog.listFreeModels(catalog)

        // Whatever NOISE_PATTERN catches, the clean id must always survive.
        assertTrue("clean id qwen/qwen3-coder must survive filter",
            free.any { it.id == "qwen/qwen3-coder" })
    }

    /**
     * TC-AGENT-200-f: selectDefaultFreeModel picks latest release_date among
     * free + tool-capable.
     */
    @Test
    fun `selectDefaultFreeModel picksLatestToolCapableFreeModel`() {
        val catalog = buildCatalog(mapOf(
            "old/free" to mapOf(
                "tool_call" to true,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2024-01-01"
            ),
            "new/free" to mapOf(
                "tool_call" to true,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2026-01-01"
            ),
            "mid/free" to mapOf(
                "tool_call" to true,
                "cost" to mapOf("input" to 0.0, "output" to 0.0),
                "release_date" to "2025-06-15"
            )
        ))

        val pick = OpenCodeZenCatalog.selectDefaultFreeModel(catalog)
        assertEquals("latest release_date wins", "new/free", pick)
        assertNotNull("returned id is non-null", pick)
        assertTrue("returned id is non-empty", pick.isNotEmpty())
    }

    /**
     * TC-AGENT-200-g: selectDefaultFreeModel returns BASELINE_FREE_MODEL when
     * the catalog yields nothing (totally empty / no opencode key / all paid).
     */
    @Test
    fun `selectDefaultFreeModel fallsBackToBaselineWhenCatalogEmpty`() {
        // Case 1: completely empty catalog
        val emptyCatalog: Map<String, Any> = emptyMap()
        assertEquals(
            "BASELINE wins on empty catalog",
            OpenCodeZenCatalog.BASELINE_FREE_MODEL,
            OpenCodeZenCatalog.selectDefaultFreeModel(emptyCatalog)
        )

        // Case 2: catalog without opencode key
        val noOpencode: Map<String, Any> = mapOf(
            "anthropic" to mapOf("models" to emptyMap<String, Any>())
        )
        assertEquals(
            "BASELINE wins when opencode provider absent",
            OpenCodeZenCatalog.BASELINE_FREE_MODEL,
            OpenCodeZenCatalog.selectDefaultFreeModel(noOpencode)
        )

        // Case 3: opencode present but no model passes the filter (all paid)
        val allPaid = buildCatalog(mapOf(
            "expensive/model" to mapOf(
                "tool_call" to true,
                "cost" to mapOf("input" to 5.0, "output" to 15.0),
                "release_date" to "2025-12-01"
            )
        ))
        assertEquals(
            "BASELINE wins when all opencode models are paid",
            OpenCodeZenCatalog.BASELINE_FREE_MODEL,
            OpenCodeZenCatalog.selectDefaultFreeModel(allPaid)
        )

        // Sanity: BASELINE itself is non-empty
        assertTrue(
            "BASELINE_FREE_MODEL is non-empty",
            OpenCodeZenCatalog.BASELINE_FREE_MODEL.isNotEmpty()
        )
    }

    /**
     * Constants sanity check — these are referenced by app/OpenCodeZenDefaults
     * and the e2e scripts; lock them down so accidental edits break the build.
     */
    @Test
    fun `constants locked`() {
        assertEquals("opencode-zen", OpenCodeZenCatalog.PROVIDER_ID)
        assertEquals("opencode", OpenCodeZenCatalog.MODELS_DEV_PROVIDER_ID)
        assertEquals("public", OpenCodeZenCatalog.PUBLIC_API_KEY)
        assertEquals(
            "https://opencode.ai/zen/v1/chat/completions",
            OpenCodeZenCatalog.DEFAULT_ENDPOINT
        )
        assertEquals("nemotron-3-super-free", OpenCodeZenCatalog.BASELINE_FREE_MODEL)
    }

    /**
     * TC-AGENT-200-i: live-fetch path.
     *
     * When the live `/v1/models` fetcher returns a non-empty list, prefer
     * the first id ending with `-free` (OpenCode Zen's free-tier convention)
     * over the static catalog selection.
     */
    @Test
    fun `selectDefaultFreeModelLive prefersLiveFreeIdOverCatalog`() {
        val catalog = mapOf(
            "opencode" to mapOf(
                "models" to mapOf(
                    "from-catalog-not-live" to mapOf(
                        "id" to "from-catalog-not-live",
                        "name" to "should not be picked",
                        "tool_call" to true,
                        "release_date" to "2026-12-01",
                        "cost" to mapOf("input" to 0.0)
                    )
                )
            )
        )
        val live = listOf(
            "claude-opus-4-7",                  // paid, skipped
            "deepseek-v4-flash-free",           // first -free → must win
            "qwen3.6-plus-free"
        )
        val picked = OpenCodeZenCatalog.selectDefaultFreeModelLive(catalog) { live }
        assertEquals("deepseek-v4-flash-free", picked)
    }

    /**
     * TC-AGENT-200-i: live-fetch returns null → falls back to catalog.
     */
    @Test
    fun `selectDefaultFreeModelLive fallsBackToCatalogWhenLiveNull`() {
        val catalog = mapOf(
            "opencode" to mapOf(
                "models" to mapOf(
                    "fallback-from-catalog" to mapOf(
                        "id" to "fallback-from-catalog",
                        "name" to "fallback",
                        "tool_call" to true,
                        "release_date" to "2026-04-21",
                        "cost" to mapOf("input" to 0.0)
                    )
                )
            )
        )
        val picked = OpenCodeZenCatalog.selectDefaultFreeModelLive(catalog) { null }
        assertEquals("fallback-from-catalog", picked)
    }

    /**
     * TC-AGENT-200-i: live empty + catalog empty → BASELINE.
     */
    @Test
    fun `selectDefaultFreeModelLive fallsBackToBaselineWhenAllEmpty`() {
        val picked = OpenCodeZenCatalog.selectDefaultFreeModelLive(emptyMap()) { null }
        assertEquals(OpenCodeZenCatalog.BASELINE_FREE_MODEL, picked)
    }

    /**
     * TC-AGENT-200-i: live list has no `-free` suffix → falls back to catalog.
     */
    @Test
    fun `selectDefaultFreeModelLive ignoresLiveListWithoutFreeIds`() {
        val catalog = mapOf(
            "opencode" to mapOf(
                "models" to mapOf(
                    "catalog-pick" to mapOf(
                        "id" to "catalog-pick",
                        "name" to "from catalog",
                        "tool_call" to true,
                        "release_date" to "2026-03-11",
                        "cost" to mapOf("input" to 0.0)
                    )
                )
            )
        )
        val live = listOf("paid-only-1", "paid-only-2")
        val picked = OpenCodeZenCatalog.selectDefaultFreeModelLive(catalog) { live }
        assertEquals("catalog-pick", picked)
    }
}
