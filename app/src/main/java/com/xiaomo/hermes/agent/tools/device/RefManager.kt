package com.xiaomo.hermes.agent.tools.device

/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/browser/pw-role-snapshot.ts
 * - ../openclaw/src/browser/cdp.ts (AriaSnapshotNode)
 * - Playwright aria snapshot format: role "name" [ref=eN] [attribute=value]
 *
 * Field naming aligned with Playwright's accessibility snapshot:
 * - role: ARIA role (button, textbox, heading, link, checkbox, etc.)
 * - name: accessible name (visible text, content description, or aria-label)
 * - ref: unique reference ID in "eN" format
 * - value: current value for inputs/sliders
 * - description: additional accessible description
 * - checked/disabled/expanded/pressed/selected/level: ARIA state attributes
 * - depth: tree depth (rendered as indentation in YAML output)
 *
 * Android-specific extension fields (not in Playwright, prefixed for clarity):
 * - bounds: screen bounds for coordinate resolution
 */

import android.graphics.Rect
import com.xiaomo.hermes.logging.Log

data class RefNode(
    // ── Playwright-aligned fields ──
    val ref: String,              // "e1", "e2", ... — unique element reference
    val role: String,             // ARIA role: button, textbox, heading, link, checkbox, etc.
    val name: String? = null,     // Accessible name (text / contentDescription / aria-label)
    val value: String? = null,    // Current value (for input, slider, etc.)
    val description: String? = null, // Accessible description
    val checked: Boolean? = null, // null = not checkable, true/false = state
    val disabled: Boolean = false,
    val expanded: Boolean? = null, // null = not expandable
    val level: Int? = null,       // Heading level, tree item level
    val pressed: Boolean? = null, // null = not pressable (toggle button state)
    val selected: Boolean? = null, // null = not selectable
    val depth: Int = 0,           // Tree depth for indentation

    // ── Android-specific extensions ──
    val bounds: Rect,             // Screen bounds for tap coordinate resolution
)

class RefManager {
    companion object {
        private const val TAG = "RefManager"
    }

    private val refMap = mutableMapOf<String, RefNode>()
    private var lastSnapshotTime = 0L

    fun updateRefs(nodes: List<RefNode>) {
        refMap.clear()
        nodes.forEach { refMap[it.ref] = it }
        lastSnapshotTime = System.currentTimeMillis()
        Log.d(TAG, "Updated ${nodes.size} refs")
    }

    fun resolveRef(ref: String): Pair<Int, Int>? {
        val node = refMap[ref] ?: return null
        return Pair(node.bounds.centerX(), node.bounds.centerY())
    }

    fun getRefNode(ref: String): RefNode? = refMap[ref]

    fun isStale(maxAgeMs: Long = 10_000): Boolean {
        return System.currentTimeMillis() - lastSnapshotTime > maxAgeMs
    }

    fun getRefCount(): Int = refMap.size

    fun clear() {
        refMap.clear()
        lastSnapshotTime = 0
    }
}
