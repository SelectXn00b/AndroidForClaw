package com.ai.assistance.operit.services

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R-UI-062: ChatServiceCore.steerActiveLoop transparently delegates to
 * EnhancedAIService.steerActiveLoop. Source-scan because instantiating
 * ChatServiceCore requires Application Context + ApplicationScope +
 * eight delegates + Room DB — none of which is needed to assert the
 * three structural guarantees we care about:
 *
 *   (1) `fun steerActiveLoop(chatId: String, text: String): Boolean` exists
 *   (2) Body resolves the per-instance EnhancedAIService and forwards `text`
 *   (3) When the service is null, returns false (caller falls back to
 *       cancel-then-resend per R-UI-061)
 */
class ChatServiceCoreSteerLoopTest {

    private val source: String by lazy { File(corePath()).readText() }

    /** Brace-walked body of `fun steerActiveLoop(`. */
    private fun extractSteerActiveLoopBody(): String {
        val anchor = Regex("""fun\s+steerActiveLoop\s*\(""").find(source)?.range?.first
            ?: error("Cannot find steerActiveLoop in ChatServiceCore.kt")
        var i = source.indexOf('{', anchor)
        require(i >= 0) { "Cannot find steerActiveLoop opening brace" }
        val start = i
        var depth = 0
        while (i < source.length) {
            val c = source[i]
            if (c == '{') depth++
            else if (c == '}') {
                depth--
                if (depth == 0) return source.substring(start, i + 1)
            }
            i++
        }
        return source.substring(start)
    }

    // -------- TC-UI-062-d --------
    /**
     * TC-UI-062-d: structural guarantees of ChatServiceCore.steerActiveLoop.
     */
    @Test
    fun `TC-UI-062-d steerActiveLoop delegates`() {
        // (1) Method declared with the expected signature.
        assertTrue(
            "TC-UI-062-d: must declare `fun steerActiveLoop(chatId: String, text: String): Boolean`",
            Regex("""fun\s+steerActiveLoop\s*\(\s*chatId\s*:\s*String\s*,\s*text\s*:\s*String\s*\)\s*:\s*Boolean""")
                .containsMatchIn(source),
        )

        val body = extractSteerActiveLoopBody()

        // (2) Forwards `text` to the per-instance EnhancedAIService.
        assertTrue(
            "TC-UI-062-d: body must reference enhancedAiService backing field",
            body.contains("enhancedAiService"),
        )
        assertTrue(
            "TC-UI-062-d: body must call EnhancedAIService.steerActiveLoop(text)",
            Regex("""\.steerActiveLoop\s*\(\s*text\s*\)""").containsMatchIn(body),
        )

        // (3) Null-service branch must `return false` so caller can fall back.
        assertTrue(
            "TC-UI-062-d: null-service branch must return false",
            Regex("""return\s+false""").containsMatchIn(body),
        )
    }

    private fun corePath(): String {
        val candidates = listOf(
            File("src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt"),
            File("app/src/main/java/com/ai/assistance/operit/services/ChatServiceCore.kt"),
        )
        return candidates.firstOrNull { it.exists() }?.path
            ?: error("Cannot locate ChatServiceCore.kt — cwd=${File(".").absolutePath}")
    }
}
