package com.xiaomo.hermes.agent.tools.device

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/browser/pw-role-snapshot.ts
 * - ../openclaw/src/browser/snapshot-roles.ts (INTERACTIVE_ROLES, CONTENT_ROLES, STRUCTURAL_ROLES)
 *
 * Builds RefNode list from Android ViewNode, mapping Android widget classes
 * to Playwright ARIA roles. Output format matches Playwright's aria snapshot:
 *   - role "name" [ref=eN] [checked] [disabled] [expanded] [level=N] [pressed] [selected]
 */

import android.graphics.Rect
import com.xiaomo.hermes.logging.Log
import com.xiaomo.hermes.accessibility.service.ViewNode

object SnapshotBuilder {
    private const val TAG = "SnapshotBuilder"

    /**
     * Playwright INTERACTIVE_ROLES — elements that always get a ref.
     * Aligned with ../openclaw/src/browser/snapshot-roles.ts
     */
    private val INTERACTIVE_ROLES = setOf(
        "button", "checkbox", "combobox", "link", "listbox",
        "menuitem", "menuitemcheckbox", "menuitemradio", "option",
        "radio", "searchbox", "slider", "spinbutton", "switch",
        "tab", "textbox", "treeitem"
    )

    /**
     * Playwright CONTENT_ROLES — get a ref when named.
     */
    private val CONTENT_ROLES = setOf(
        "article", "cell", "columnheader", "gridcell", "heading",
        "listitem", "main", "navigation", "region", "rowheader"
    )

    /**
     * Playwright STRUCTURAL_ROLES — containers, typically skipped in compact mode.
     */
    private val STRUCTURAL_ROLES = setOf(
        "application", "directory", "document", "generic", "grid",
        "group", "ignored", "list", "menu", "menubar", "none",
        "presentation", "row", "rowgroup", "table", "tablist",
        "toolbar", "tree", "treegrid"
    )

    /**
     * Build Playwright-aligned ref nodes from ViewNode list.
     *
     * Filtering logic matches Playwright: interactive elements always get a ref,
     * content elements get a ref when named, structural elements are included
     * but don't get refs.
     */
    fun buildFromViewNodes(nodes: List<ViewNode>): List<RefNode> {
        val refNodes = mutableListOf<RefNode>()
        var refCounter = 1

        for (viewNode in nodes) {
            // Accessible name: text > contentDescription (matches Playwright's "name")
            val name = viewNode.text?.takeIf { it.isNotBlank() }
                ?: viewNode.contentDesc?.takeIf { it.isNotBlank() }

            val isInteractive = viewNode.clickable || viewNode.focusable || viewNode.scrollable
            val hasName = !name.isNullOrBlank()

            // Skip nodes with no name and no interactivity
            if (!isInteractive && !hasName) continue

            val shortClass = viewNode.className?.substringAfterLast('.') ?: "View"
            val role = mapToRole(shortClass, viewNode)

            // Playwright ref assignment: interactive always, content when named
            val shouldHaveRef = role in INTERACTIVE_ROLES ||
                (role in CONTENT_ROLES && hasName)

            val ref = if (shouldHaveRef) "e${refCounter++}" else "e${refCounter++}"

            refNodes.add(RefNode(
                ref = ref,
                role = role,
                name = name?.take(100),
                value = extractValue(shortClass, viewNode),
                checked = extractChecked(shortClass, viewNode),
                disabled = !viewNode.enabled,
                expanded = null,  // Android accessibility doesn't expose this on ViewNode
                level = null,     // No heading level in ViewNode
                pressed = null,   // Toggle state not in ViewNode
                selected = extractSelected(viewNode),
                depth = 0,        // ViewNode doesn't carry depth
                bounds = Rect(viewNode.left, viewNode.top, viewNode.right, viewNode.bottom),
            ))
        }

        Log.d(TAG, "Built ${refNodes.size} ref nodes from ${nodes.size} view nodes")
        return refNodes
    }

    /**
     * Map Android class names to Playwright ARIA roles.
     *
     * Playwright roles reference:
     * https://www.w3.org/TR/wai-aria-1.2/#role_definitions
     */
    private fun mapToRole(className: String, node: ViewNode): String {
        return when {
            // Input → textbox (Playwright uses "textbox", not "input")
            className.contains("EditText", ignoreCase = true) -> "textbox"
            className.contains("SearchView", ignoreCase = true) -> "searchbox"

            // Buttons
            className.contains("ImageButton", ignoreCase = true) -> "button"
            className.contains("Button", ignoreCase = true) -> "button"

            // Text
            className.contains("TextView", ignoreCase = true) -> {
                if (node.clickable) "link" else "text"
            }

            // Images
            className.contains("ImageView", ignoreCase = true) -> "img"

            // Checkable
            className.contains("CheckBox", ignoreCase = true) -> "checkbox"
            className.contains("RadioButton", ignoreCase = true) -> "radio"
            className.contains("Switch", ignoreCase = true) -> "switch"
            className.contains("ToggleButton", ignoreCase = true) -> "switch"

            // Range
            className.contains("SeekBar", ignoreCase = true) -> "slider"
            className.contains("ProgressBar", ignoreCase = true) -> "progressbar"
            className.contains("RatingBar", ignoreCase = true) -> "slider"

            // Selection
            className.contains("Spinner", ignoreCase = true) -> "combobox"

            // Lists
            className.contains("RecyclerView", ignoreCase = true) -> "list"
            className.contains("ListView", ignoreCase = true) -> "list"
            className.contains("GridView", ignoreCase = true) -> "grid"

            // Tabs
            className.contains("TabLayout", ignoreCase = true) -> "tablist"
            className.contains("TabView", ignoreCase = true) -> "tab"

            // Containers
            className.contains("ScrollView", ignoreCase = true) -> "group"
            className.contains("ViewPager", ignoreCase = true) -> "group"
            className.contains("CardView", ignoreCase = true) -> "group"
            className.contains("FrameLayout", ignoreCase = true) -> "generic"
            className.contains("LinearLayout", ignoreCase = true) -> "generic"
            className.contains("RelativeLayout", ignoreCase = true) -> "generic"
            className.contains("ConstraintLayout", ignoreCase = true) -> "generic"

            // WebView
            className.contains("WebView", ignoreCase = true) -> "document"

            // Toolbar / Navigation
            className.contains("Toolbar", ignoreCase = true) -> "toolbar"
            className.contains("NavigationView", ignoreCase = true) -> "navigation"
            className.contains("BottomNavigation", ignoreCase = true) -> "navigation"

            // Fallback by behavior
            node.scrollable -> "group"
            node.clickable -> "button"
            node.focusable -> "textbox"
            else -> "generic"
        }
    }

    /**
     * Extract value for input-type elements (Playwright "value" field).
     */
    private fun extractValue(className: String, node: ViewNode): String? {
        if (className.contains("EditText", ignoreCase = true) ||
            className.contains("SearchView", ignoreCase = true)
        ) {
            return node.text?.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * Extract checked state (Playwright "checked" attribute).
     * Returns null if element is not checkable.
     */
    private fun extractChecked(className: String, node: ViewNode): Boolean? {
        val isCheckable = className.contains("CheckBox", ignoreCase = true) ||
            className.contains("RadioButton", ignoreCase = true) ||
            className.contains("Switch", ignoreCase = true) ||
            className.contains("ToggleButton", ignoreCase = true)

        // ViewNode doesn't have checked field directly,
        // but text often contains state info. Return null if not checkable.
        return if (isCheckable) false else null
    }

    /**
     * Extract selected state (Playwright "selected" attribute).
     * Returns null if element is not selectable.
     */
    private fun extractSelected(node: ViewNode): Boolean? {
        // ViewNode doesn't expose selected state directly
        return null
    }
}
