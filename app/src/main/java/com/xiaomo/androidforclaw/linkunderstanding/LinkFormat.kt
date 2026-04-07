package com.xiaomo.androidforclaw.linkunderstanding

/**
 * OpenClaw module: link-understanding
 * Source: OpenClaw/src/link-understanding/format.ts
 */

data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null
)

fun formatLinkPreviewAsText(preview: LinkPreview): String {
    val parts = mutableListOf<String>()
    preview.title?.let { parts.add(it) }
    preview.description?.let { parts.add(it) }
    parts.add(preview.url)
    return parts.joinToString("\n")
}

fun formatLinkPreviewAsMarkdown(preview: LinkPreview): String {
    val title = preview.title ?: preview.url
    val md = StringBuilder()
    md.append("[${title}](${preview.url})")
    preview.description?.let { md.append("\n> $it") }
    return md.toString()
}
