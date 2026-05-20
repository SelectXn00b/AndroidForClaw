package com.ai.assistance.operit.data.preferences

import com.xiaomo.hermes.hermes.agent.OpenCodeZenCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * App-side constant-bridge smoke test for [OpenCodeZenDefaults].
 *
 * Tied to: R-AGENT-002, TC-AGENT-200-c (constants portion of 新用户 OpenCode Zen 链路).
 *
 * Pure-JVM (no Robolectric) — only validates that the app-side bridge
 * constants forward exactly the hermes-android catalog values. The
 * snapshot-loading half of the path is covered by TC-AGENT-200-d
 * (`ModelsDevSnapshotTest`) inside hermes-android.
 */
class OpenCodeZenDefaultsTest {

    /** TC-AGENT-200-c (constants): bridge values are identical to catalog. */
    @Test
    fun `bridge constants match hermes-android catalog`() {
        assertEquals(
            "PROVIDER_ID matches catalog",
            OpenCodeZenCatalog.PROVIDER_ID, OpenCodeZenDefaults.PROVIDER_ID
        )
        assertEquals(
            "API_KEY matches catalog (literal 'public')",
            OpenCodeZenCatalog.PUBLIC_API_KEY, OpenCodeZenDefaults.API_KEY
        )
        assertEquals("API_KEY is the literal 'public'", "public", OpenCodeZenDefaults.API_KEY)
        assertEquals(
            "ENDPOINT matches catalog",
            OpenCodeZenCatalog.DEFAULT_ENDPOINT, OpenCodeZenDefaults.ENDPOINT
        )
        assertEquals(
            "BASELINE_MODEL matches catalog",
            OpenCodeZenCatalog.BASELINE_FREE_MODEL, OpenCodeZenDefaults.BASELINE_MODEL
        )
    }
}
