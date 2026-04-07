package com.xiaomo.androidforclaw.linkunderstanding

import com.xiaomo.androidforclaw.config.OpenClawConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenClaw module: link-understanding
 * Source: OpenClaw/src/link-understanding/runtime.ts
 *
 * Orchestrates link detection -> fetch -> preview generation.
 */
object LinkUnderstandingRuntime {

    private val OG_TITLE = Regex("""<meta\s+property\s*=\s*["']og:title["']\s+content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val OG_DESC = Regex("""<meta\s+property\s*=\s*["']og:description["']\s+content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val OG_IMAGE = Regex("""<meta\s+property\s*=\s*["']og:image["']\s+content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val OG_SITE = Regex("""<meta\s+property\s*=\s*["']og:site_name["']\s+content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val TITLE_TAG = Regex("""<title[^>]*>([^<]*)</title>""", RegexOption.IGNORE_CASE)

    suspend fun fetchLinkPreview(url: String, config: OpenClawConfig): LinkPreview? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "OpenClaw/1.0 LinkPreview")
            connection.instanceFollowRedirects = true

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val ogTitle = OG_TITLE.find(html)?.groupValues?.get(1)
            val ogDesc = OG_DESC.find(html)?.groupValues?.get(1)
            val ogImage = OG_IMAGE.find(html)?.groupValues?.get(1)
            val ogSite = OG_SITE.find(html)?.groupValues?.get(1)
            val fallbackTitle = TITLE_TAG.find(html)?.groupValues?.get(1)

            LinkPreview(
                url = url,
                title = ogTitle ?: fallbackTitle,
                description = ogDesc,
                imageUrl = ogImage,
                siteName = ogSite
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun processMessageLinks(
        text: String,
        config: OpenClawConfig,
        maxLinks: Int = 3
    ): List<LinkPreview> = coroutineScope {
        val links = extractLinksFromMessage(text).take(maxLinks)
        links.map { detected ->
            async { fetchLinkPreview(detected.url, config) }
        }.awaitAll().filterNotNull()
    }

    fun isLinkUnderstandingEnabled(config: OpenClawConfig): Boolean {
        // Link understanding is enabled by default; disabled only when tools config
        // or skills explicitly disable it. Since OpenClawConfig has no explicit field,
        // default to enabled.
        return true
    }
}
