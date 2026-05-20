package com.ai.assistance.operit.data.preferences

import android.content.Context
import com.xiaomo.hermes.hermes.agent.MODELS_DEV_SNAPSHOT_ASSET
import com.xiaomo.hermes.hermes.agent.OpenCodeZenCatalog
import com.xiaomo.hermes.hermes.agent.fetchModelsDevWithSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between hermes-android's OpenCodeZenCatalog and the app's DataStore
 * defaults. Replaces the deleted BuiltInKeyProvider with the OpenCode Zen
 * public-key fallback path (R-AGENT-002).
 *
 * - PUBLIC_API_KEY is the literal "public" string — not a secret.
 * - selectDefaultFreeModel() resolves via three tiers in order:
 *     1. live `https://opencode.ai/zen/v1/models` filtered to `-free` ids
 *        (authoritative — the live endpoint serves a strict subset of
 *        models.dev's `opencode` provider catalog);
 *     2. bundled `assets/models_dev_snapshot.json` filtered through
 *        [OpenCodeZenCatalog.selectDefaultFreeModel];
 *     3. [OpenCodeZenCatalog.BASELINE_FREE_MODEL] literal.
 *
 * No Python upstream — Android-only deployment strategy.
 */
object OpenCodeZenDefaults {
    const val PROVIDER_ID: String = OpenCodeZenCatalog.PROVIDER_ID
    const val API_KEY: String = OpenCodeZenCatalog.PUBLIC_API_KEY
    const val ENDPOINT: String = OpenCodeZenCatalog.DEFAULT_ENDPOINT
    const val BASELINE_MODEL: String = OpenCodeZenCatalog.BASELINE_FREE_MODEL

    /**
     * Pick the default free model. Always returns a non-empty string
     * (BASELINE_MODEL on total failure). See class kdoc for the resolution
     * order.
     *
     * Runs on [Dispatchers.IO] because the live tier performs a synchronous
     * HTTP GET against `opencode.ai/zen/v1/models`; calling this on the main
     * thread triggers ANR / StrictMode violations.
     */
    suspend fun selectDefaultFreeModel(context: Context): String = withContext(Dispatchers.IO) {
        val snapshot = runCatching {
            context.assets.open(MODELS_DEV_SNAPSHOT_ASSET)
                .bufferedReader().use { it.readText() }
        }.getOrNull()
        val catalog = fetchModelsDevWithSnapshot(snapshotProvider = { snapshot })
        OpenCodeZenCatalog.selectDefaultFreeModelLive(catalog)
    }
}
