package com.xiaomo.androidforclaw.interactive

/**
 * OpenClaw module: interactive
 * Source: OpenClaw/src/interactive/payload.ts
 *
 * Defines and normalizes interactive reply payloads (buttons, select menus,
 * text blocks) for rich chat responses across channels.
 */

enum class InteractiveButtonStyle { PRIMARY, SECONDARY, SUCCESS, DANGER }

data class InteractiveReplyButton(
    val label: String,
    val value: String,
    val style: InteractiveButtonStyle? = null
)

data class InteractiveReplyOption(
    val label: String,
    val value: String,
    val description: String? = null
)

sealed class InteractiveReplyBlock {
    data class Text(val text: String) : InteractiveReplyBlock()
    data class Buttons(val buttons: List<InteractiveReplyButton>) : InteractiveReplyBlock()
    data class Select(
        val placeholder: String? = null,
        val options: List<InteractiveReplyOption>,
        val maxValues: Int? = null
    ) : InteractiveReplyBlock()
}

data class InteractiveReply(
    val blocks: List<InteractiveReplyBlock> = emptyList()
)

fun normalizeInteractiveReply(raw: Any?): InteractiveReply? {
    if (raw == null) return null
    if (raw is InteractiveReply) return raw
    // TODO: Parse from Map/JSON
    return null
}

fun hasInteractiveReplyBlocks(reply: InteractiveReply?): Boolean =
    reply != null && reply.blocks.isNotEmpty()

fun resolveInteractiveTextFallback(
    text: String?,
    interactive: InteractiveReply?
): String? {
    if (!text.isNullOrBlank()) return text
    if (interactive == null) return null
    val textBlocks = interactive.blocks.filterIsInstance<InteractiveReplyBlock.Text>()
    return textBlocks.joinToString("\n") { it.text }.ifBlank { null }
}
