package com.xiaomo.hermes.hermes.gateway

/**
 * R-GATEWAY-036: command names that bypass the active-session busy gate.
 *
 * Mirrors Python `hermes_cli/commands.py:267-284` (`ACTIVE_SESSION_BYPASS_COMMANDS`).
 * These commands have semantics distinct from "user is sending another turn":
 * they're meta-controls (cancel, queue, steer, status, etc.) that should be
 * routed through dedicated handlers instead of the normal busy path.
 *
 * Only `/steer`, `/queue`, `/stop` are wired to concrete handlers in this R;
 * the rest are recognized for "polite reject" so users see a clear message
 * instead of having their text accidentally interrupt or queue. Future R-IDs
 * can wire individual handlers as needed.
 */
internal val ACTIVE_SESSION_BYPASS_COMMANDS: Set<String> = setOf(
    "agents",
    "approve",
    "background",
    "commands",
    "deny",
    "help",
    "new",
    "profile",
    "queue",
    "restart",
    "status",
    "steer",
    "stop",
    "update",
)

/**
 * R-GATEWAY-036: parse a leading slash command from `text`.
 *
 * Returns `(commandName, argText)` if `text` (after trimming leading whitespace)
 * starts with a `/<token>` whose lowercased token is in
 * [ACTIVE_SESSION_BYPASS_COMMANDS]; returns null otherwise.
 *
 * - `commandName` is lowercased and slash-stripped.
 * - `argText` is the remainder after the first whitespace gap, trimmed.
 * - Empty / non-slash / unrecognized → null (so caller can fall through).
 *
 * Mirrors the resolution semantics of Python `hermes_cli/commands.py:resolve_command`
 * scoped to the active-session bypass set. Mid-turn we never want to interpret
 * arbitrary unknown words as commands — only the explicit allowlist.
 */
internal fun resolveCommand(text: String): Pair<String, String>? {
    val trimmed = text.trimStart()
    if (!trimmed.startsWith("/")) return null
    // Split on first whitespace gap; first token = "/<cmd>", rest = arg text.
    val firstSpace = trimmed.indexOfFirst { it.isWhitespace() }
    val (head, rest) = if (firstSpace < 0) {
        trimmed to ""
    } else {
        trimmed.substring(0, firstSpace) to trimmed.substring(firstSpace).trim()
    }
    val cmd = head.removePrefix("/").trim().lowercase()
    if (cmd.isEmpty()) return null
    if (cmd !in ACTIVE_SESSION_BYPASS_COMMANDS) return null
    return cmd to rest
}
