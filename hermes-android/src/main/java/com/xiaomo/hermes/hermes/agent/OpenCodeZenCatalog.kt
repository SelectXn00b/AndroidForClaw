package com.xiaomo.hermes.hermes.agent

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// 无 Python 上游：OpenCode Zen public-key 兜底属 Android-only 部署策略。
// 借鉴 sst/opencode TS 上游 packages/opencode/src/provider/provider.ts:160-182
// 的 apiKey="public" + cost.input==0 过滤语义。
// deep_align: ignore — Android-only feature, not part of 1:1 Python translation.
//
// Tied to: R-AGENT-002, TC-AGENT-200-{e,f,g,i}

/**
 * OpenCode Zen catalog: public-key fallback path that surfaces only the
 * free, tool-capable models from models.dev's `opencode` provider entry.
 *
 * Wired by app/OpenCodeZenDefaults to seed the default model config when
 * a fresh install has no provider key configured (replaces the deleted
 * BuiltInKeyProvider OpenRouter key path).
 *
 * Resolution order for the seeded model id:
 *   1. Live `https://opencode.ai/zen/v1/models` filtered to `-free` ids
 *      (this is OpenCode Zen's authoritative list — strict subset of
 *      models.dev `opencode` provider; verified empirically that
 *      models.dev includes ids the live endpoint does not serve).
 *   2. models.dev `opencode` provider catalog (offline / snapshot).
 *   3. [BASELINE_FREE_MODEL] literal as last resort.
 */
object OpenCodeZenCatalog {

    /** ApiProviderType enum value (string-by-name in DataStore). */
    const val PROVIDER_ID: String = "opencode-zen"

    /** models.dev provider id (Hermes maps "opencode-zen" → "opencode"). */
    const val MODELS_DEV_PROVIDER_ID: String = "opencode"

    /**
     * Literal public key — this is a public, advertised value.
     * Not a secret; do not encrypt or obfuscate it.
     */
    const val PUBLIC_API_KEY: String = "public"

    /** OpenCode Zen Chat Completions endpoint. */
    const val DEFAULT_ENDPOINT: String = "https://opencode.ai/zen/v1/chat/completions"

    /** OpenCode Zen authoritative models-list endpoint (OpenAI-compatible). */
    const val MODELS_LIST_ENDPOINT: String = "https://opencode.ai/zen/v1/models"

    /**
     * Last-resort fallback model id used when both the live OpenCode Zen
     * /v1/models call and the bundled models.dev catalog are unavailable.
     *
     * Picked from the live `/v1/models` response (verified empirically with
     * `Authorization: Bearer public`): a tool-capable free-tier id that the
     * endpoint actually serves. Note that earlier plan-stage candidates
     * `qwen/qwen3-coder` and `grok-code` are present in models.dev's
     * `opencode` provider but the live endpoint returns 401 ModelError for
     * them (R-AGENT-002 implementation note).
     */
    const val BASELINE_FREE_MODEL: String = "nemotron-3-super-free"

    private val gson = Gson()
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetch the live OpenCode Zen model id list (returns `data[].id` from
     * the OpenAI-compatible `/v1/models` response). Returns null on any
     * failure (network, parse, non-2xx).
     *
     * TC-AGENT-200-i: live fetch returns id list.
     */
    fun fetchLiveModelIds(): List<String>? = try {
        val req = Request.Builder()
            .url(MODELS_LIST_ENDPOINT)
            .header("Authorization", "Bearer $PUBLIC_API_KEY")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null
            else {
                val body = resp.body?.string() ?: return@use null
                @Suppress("UNCHECKED_CAST")
                val parsed = gson.fromJson<Map<String, Any>>(
                    body,
                    object : TypeToken<Map<String, Any>>() {}.type
                )
                val data = parsed["data"] as? List<Map<String, Any?>> ?: return@use null
                data.mapNotNull { it["id"] as? String }.takeIf { it.isNotEmpty() }
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Filter the `opencode` provider's models down to free + tool-capable
     * + non-noise entries, sorted by release_date descending (newest first),
     * then by id for stable ordering.
     *
     * TC-AGENT-200-e: filters by cost.input==0 + tool_call==true + !NOISE_PATTERN
     */
    @Suppress("UNCHECKED_CAST")
    fun listFreeModels(catalog: Map<String, Any>): List<ModelInfo> {
        val provider = catalog[MODELS_DEV_PROVIDER_ID] as? Map<String, Any?> ?: return emptyList()
        val models = provider["models"] as? Map<String, Any?> ?: return emptyList()
        val out = mutableListOf<ModelInfo>()
        for ((id, raw) in models) {
            val m = raw as? Map<String, Any?> ?: continue
            val info = _parseModelInfo(id, m, MODELS_DEV_PROVIDER_ID)
            if (info.costInput != 0.0) continue
            if (!info.toolCall) continue
            if (ModelsDev.NOISE_PATTERN.containsMatchIn(id)) continue
            out += info
        }
        return out.sortedWith(
            compareByDescending<ModelInfo> { it.releaseDate.ifBlank { "0000-00-00" } }
                .thenBy { it.id }
        )
    }

    /**
     * Pick the default free model from a static catalog (unit-test path):
     * latest release_date among free+tool-capable. Falls back to
     * BASELINE_FREE_MODEL when the catalog yields nothing.
     *
     * TC-AGENT-200-f: picks latest by release_date.
     * TC-AGENT-200-g: returns BASELINE_FREE_MODEL when catalog empty.
     */
    fun selectDefaultFreeModel(catalog: Map<String, Any>): String =
        listFreeModels(catalog).firstOrNull()?.id ?: BASELINE_FREE_MODEL

    /**
     * Three-tier resolution: live OpenCode Zen `/v1/models` `-free` ids →
     * models.dev catalog → [BASELINE_FREE_MODEL]. When [liveIds] is non-null,
     * filters to ids ending with `-free` (OpenCode Zen's free-tier naming
     * convention). Within the live `-free` set, [BASELINE_FREE_MODEL] is
     * preferred when present (it is the empirically verified-working tier);
     * otherwise the first `-free` id is returned. Live-fetcher is injectable
     * for unit testing.
     *
     * TC-AGENT-200-i: live ids prefer `-free` suffix.
     */
    fun selectDefaultFreeModelLive(
        catalog: Map<String, Any>,
        liveFetcher: () -> List<String>? = ::fetchLiveModelIds
    ): String {
        val live = liveFetcher()
        if (!live.isNullOrEmpty()) {
            // Prefer the empirically verified BASELINE id when the live tier
            // serves it; other `-free` candidates from `/v1/models` may have
            // their own per-model demo quotas (observed: deepseek-v4-flash-free
            // returning FreeUsageLimitError while nemotron-3-super-free works).
            if (live.contains(BASELINE_FREE_MODEL)) return BASELINE_FREE_MODEL
            val freeFromLive = live.firstOrNull { it.endsWith("-free") }
            if (freeFromLive != null) return freeFromLive
        }
        return selectDefaultFreeModel(catalog)
    }
}
