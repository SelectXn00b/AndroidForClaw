package com.ai.assistance.operit.ui.features.chat.components.style.input.agent

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-062 / TC-UI-062-f: AgentChatInputSection must expose an `onInsertMessage`
 * callback parameter and render an insert button (Icons.Default.Edit) in the
 * trailing action area whenever the agent is processing
 * (`showCancelAction || showQueueAction`). The button click body must invoke
 * `onInsertMessage()`, and its `contentDescription` must come from the new
 * `R.string.chat_insert_message` resource.
 *
 * Source-scan because driving the real Composable would require a full Compose
 * runtime + parent screen wiring + chat-service stub.
 */
class AgentChatInputSectionInsertButtonTest {

    private val source: String by lazy { File(sectionPath()).readText() }

    @Test
    fun `TC-UI-062-f Composable parameter onInsertMessage exists`() {
        assertTrue(
            "TC-UI-062-f: AgentChatInputSection must declare an onInsertMessage Composable parameter",
            Regex("""onInsertMessage\s*:\s*\(\s*\)\s*->\s*Unit""").containsMatchIn(source),
        )
    }

    @Test
    fun `TC-UI-062-f imports Icons_Default_Edit`() {
        assertTrue(
            "TC-UI-062-f: file must import androidx.compose.material.icons.filled.Edit for the insert icon",
            source.contains("import androidx.compose.material.icons.filled.Edit"),
        )
    }

    @Test
    fun `TC-UI-062-f renders Icons_Default_Edit with chat_insert_message description`() {
        // Locate at least one Icon block that uses Icons.Default.Edit AND
        // pulls its contentDescription from R.string.chat_insert_message.
        // We allow the contentDescription to be retrieved via either
        // `stringResource(R.string.chat_insert_message)` or
        // `context.getString(R.string.chat_insert_message)` — the latter is
        // what the current implementation uses inside non-Composable lambdas.
        assertTrue(
            "TC-UI-062-f: an Icons.Default.Edit usage must be present",
            source.contains("Icons.Default.Edit"),
        )
        assertTrue(
            "TC-UI-062-f: contentDescription must reference R.string.chat_insert_message",
            Regex("""R\.string\.chat_insert_message""").containsMatchIn(source),
        )

        // Confirm at least one Icon(...) block contains BOTH Icons.Default.Edit
        // and the string-resource reference within ~600 chars window.
        val editIdx = source.indexOf("Icons.Default.Edit")
        require(editIdx >= 0)
        val window = source.substring(
            (editIdx - 400).coerceAtLeast(0),
            (editIdx + 600).coerceAtMost(source.length),
        )
        assertTrue(
            "TC-UI-062-f: Icons.Default.Edit usage must be paired with chat_insert_message contentDescription within the same Icon block",
            window.contains("R.string.chat_insert_message"),
        )
    }

    @Test
    fun `TC-UI-062-f click body invokes onInsertMessage`() {
        // The insert button must call onInsertMessage() in its onClick body.
        // We accept either onClick = { onInsertMessage() } or
        // onClick = onInsertMessage style.
        assertTrue(
            "TC-UI-062-f: insert button click body must invoke onInsertMessage()",
            Regex("""onClick\s*=\s*\{\s*onInsertMessage\s*\(\s*\)\s*\}""").containsMatchIn(source) ||
                Regex("""onClick\s*=\s*onInsertMessage\b""").containsMatchIn(source),
        )
    }

    @Test
    fun `TC-UI-062-f visibility gated by showCancelAction or showQueueAction`() {
        // The insert button block must be guarded by `showCancelAction || showQueueAction`
        // so it only appears while the agent is processing. We assert that
        // EVERY Icons.Default.Edit occurrence has such a gate within the same
        // enclosing block (~1500 chars upward window — the surrounding Box +
        // modifier chain can run a bit long).
        var idx = source.indexOf("Icons.Default.Edit")
        require(idx >= 0) { "TC-UI-062-f: Icons.Default.Edit must exist" }
        var occurrences = 0
        while (idx >= 0) {
            occurrences++
            val before = source.substring(
                (idx - 1500).coerceAtLeast(0),
                idx,
            )
            assertTrue(
                "TC-UI-062-f: Icons.Default.Edit occurrence #$occurrences (charIdx=$idx) " +
                    "must be gated by `showCancelAction || showQueueAction` within the same enclosing block",
                Regex("""showCancelAction\s*\|\|\s*showQueueAction""").containsMatchIn(before),
            )
            idx = source.indexOf("Icons.Default.Edit", idx + 1)
        }
        assertTrue(
            "TC-UI-062-f: must find at least one Icons.Default.Edit",
            occurrences >= 1,
        )
    }

    private fun sectionPath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/agent/AgentChatInputSection.kt"),
            File("app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/agent/AgentChatInputSection.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate AgentChatInputSection.kt — cwd=${File(".").absolutePath}")
    }
}
