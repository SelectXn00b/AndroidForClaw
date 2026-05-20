package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.xiaomo.hermes.hermes.agent.MODELS_DEV_SNAPSHOT_ASSET
import com.xiaomo.hermes.hermes.agent.OpenCodeZenCatalog
import com.xiaomo.hermes.hermes.agent.fetchModelsDevWithSnapshot

/**
 * Bridge between hermes-android's OpenCodeZenCatalog and the app's DataStore
 * defaults. Replaces the deleted BuiltInKeyProvider with the OpenCode Zen
 * public-key fallback path (R-AGENT-002).
 *
 * - PUBLIC_API_KEY is the literal "public" string — not a secret.
 * - selectDefaultFreeModel() consults the bundled assets snapshot to pick a
 *   live free+tool-capable model id at first launch; falls back to the
 *   BASELINE_FREE_MODEL when the catalog is unavailable.
 *
 * No Python upstream — Android-only deployment strategy.
 */
object OpenCodeZenDefaults {
    const val PROVIDER_ID: String = OpenCodeZenCatalog.PROVIDER_ID
    const val API_KEY: String = OpenCodeZenCatalog.PUBLIC_API_KEY
    const val ENDPOINT: String = OpenCodeZenCatalog.DEFAULT_ENDPOINT
    const val BASELINE_MODEL: String = OpenCodeZenCatalog.BASELINE_FREE_MODEL

    /**
     * Pick the default free model by consulting:
     *   1. mem/disk catalog (network-free path),
     *   2. assets/models_dev_snapshot.json (bundled at build time),
     *   3. force network refresh,
     * and applying [OpenCodeZenCatalog.selectDefaultFreeModel].
     *
     * Always returns a non-empty string (BASELINE_MODEL on total failure).
     */
    fun selectDefaultFreeModel(context: Context): String {
        val snapshot = runCatching {
            context.assets.open(MODELS_DEV_SNAPSHOT_ASSET)
                .bufferedReader().use { it.readText() }
        }.getOrNull()
        val catalog = fetchModelsDevWithSnapshot(snapshotProvider = { snapshot })
        return OpenCodeZenCatalog.selectDefaultFreeModel(catalog)
    }
}
