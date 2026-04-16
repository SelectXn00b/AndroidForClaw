package com.xiaomo.hermes.agent.tools.device

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/browser/pw-role-snapshot.ts (buildRoleSnapshotFromAriaSnapshot)
 * - ../openclaw/src/browser/pw-tools-core.snapshot.ts
 *
 * Output format matches Playwright's aria snapshot YAML:
 *   - role "name" [ref=eN] [checked] [disabled] [expanded] [level=N] [pressed] [selected]
 *
 * Three output modes:
 * - compact: Playwright YAML format (default, what OpenClaw sends to AI)
 * - interactive: flat list of interactive elements only, with coordinates
 * - tree: full hierarchy with bounds (debug use)
 */

object SnapshotFormatter {

    /** Playwright INTERACTIVE_ROLES — always included with ref. */
    private val INTERACTIVE_ROLES = setOf(
        "button", "checkbox", "combobox", "link", "listbox",
        "menuitem", "menuitemcheckbox", "menuitemradio", "option",
        "radio", "searchbox", "slider", "spinbutton", "switch",
        "tab", "textbox", "treeitem"
    )

    /**
     * Compact format (default) — Playwright aria snapshot YAML.
     *
     * Output example:
     *   - heading "Settings" [ref=e1] [level=1]
     *   - button "Save" [ref=e2]
     *   - textbox "Username" [ref=e3]
     *   - checkbox "Remember me" [ref=e4] [checked]
     */
    fun compact(nodes: List<RefNode>, screenWidth: Int, screenHeight: Int, appName: String?): String {
        val sb = StringBuilder()
        sb.appendLine("[Screen: ${screenWidth}x${screenHeight}${appName?.let { " $it" } ?: ""}]")
        sb.appendLine()

        for (node in nodes) {
            val indent = "  ".repeat(node.depth.coerceAtMost(6))
            sb.appendLine("$indent- ${formatNodePlaywright(node)}")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Interactive format — only interactive elements, Playwright YAML + coordinates.
     *
     * Output example:
     *   - button "Save" [ref=e2] (540, 1200)
     *   - textbox "Username" [ref=e3] (540, 800)
     */
    fun interactive(nodes: List<RefNode>, appName: String?): String {
        val sb = StringBuilder()
        sb.appendLine("[Screen: ${appName ?: "Android"}] Interactive elements:")
        sb.appendLine()

        val interactiveNodes = nodes.filter { it.role in INTERACTIVE_ROLES }
        for (node in interactiveNodes) {
            val cx = node.bounds.centerX()
            val cy = node.bounds.centerY()
            sb.appendLine("- ${formatNodePlaywright(node)} ($cx, $cy)")
        }

        if (interactiveNodes.isEmpty()) {
            sb.appendLine("(no interactive elements)")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Tree format — full hierarchy with bounds (for debugging).
     *
     * Output example:
     *   - group [ref=e1] bounds=(0,0,1080,2400)
     *     - heading "Title" [ref=e2] [level=1] bounds=(0,0,1080,100)
     *     - button "OK" [ref=e3] bounds=(400,200,680,280)
     */
    fun tree(nodes: List<RefNode>, screenWidth: Int, screenHeight: Int, appName: String?): String {
        val sb = StringBuilder()
        sb.appendLine("[Screen: ${screenWidth}x${screenHeight}${appName?.let { " $it" } ?: ""}]")
        sb.appendLine()

        for (node in nodes) {
            val indent = "  ".repeat(node.depth.coerceAtMost(6))
            val b = node.bounds
            sb.appendLine("$indent- ${formatNodePlaywright(node)} bounds=(${b.left},${b.top},${b.right},${b.bottom})")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Format a single node in Playwright's aria snapshot syntax:
     *   role "name" [ref=eN] [checked] [disabled] [expanded] [level=N] [pressed] [selected]
     */
    private fun formatNodePlaywright(node: RefNode): String {
        val sb = StringBuilder()

        // role
        sb.append(node.role)

        // "name" — quoted accessible name
        node.name?.let { sb.append(" \"$it\"") }

        // [ref=eN]
        sb.append(" [ref=${node.ref}]")

        // ARIA state attributes (only when present/true, matching Playwright)
        if (node.checked == true) sb.append(" [checked]")
        if (node.disabled) sb.append(" [disabled]")
        if (node.expanded == true) sb.append(" [expanded]")
        if (node.expanded == false) sb.append(" [expanded=false]")
        node.level?.let { sb.append(" [level=$it]") }
        if (node.pressed == true) sb.append(" [pressed]")
        if (node.selected == true) sb.append(" [selected]")

        // value (for textbox, slider, etc.)
        node.value?.let { sb.append(": $it") }

        return sb.toString()
    }
}
