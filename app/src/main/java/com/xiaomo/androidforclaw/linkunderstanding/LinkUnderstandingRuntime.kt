package com.xiaomo.androidforclaw.linkunderstanding

import com.xiaomo.androidforclaw.config.OpenClawConfig

/**
 * OpenClaw module: link-understanding
 * Source: OpenClaw/src/link-understanding/runtime.ts
 *
 * Orchestrates link detection → fetch → preview generation.
 */
object LinkUnderstandingRuntime {

    suspend fun fetchLinkPreview(url: String, config: OpenClawConfig): LinkPreview? {
        TODO("HTTP-fetch the URL, parse og: / meta tags, return LinkPreview")
    }

    suspend fun processMessageLinks(
        text: String,
        config: OpenClawConfig,
        maxLinks: Int = 3
    ): List<LinkPreview> {
        TODO("Extract links, fetch previews in parallel, return up to maxLinks results")
    }

    fun isLinkUnderstandingEnabled(config: OpenClawConfig): Boolean {
        TODO("Check config flag for link understanding feature")
    }
}
