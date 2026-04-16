package com.xiaomo.hermes.gateway.protocol

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HelloOkFrameAlignmentTest {

    // Resolve project root from Gradle's working directory (app/)
    private val projectRoot = File(System.getProperty("user.dir")!!).let {
        if (it.name == "app") it.parentFile else it
    }
    private val sourceFile = File(
        projectRoot, "app/src/main/java/com/xiaomo/androidforclaw/gateway/protocol/ProtocolTypes.kt"
    )

    @Test
    fun helloOkFrame_includes_optional_canvasHostUrl_and_auth_fields() {
        val src = sourceFile.readText()
        val section = src.substringAfter("data class HelloOkFrame(").substringBefore(") : Frame()")
        assertTrue(section.contains("val canvasHostUrl: String? = null"))
        assertTrue(section.contains("val auth: HelloAuth? = null"))
    }

    @Test
    fun helloAuth_matches_openclaw_shape() {
        val src = sourceFile.readText()
        val section = src.substringAfter("data class HelloAuth(").substringBefore(")")
        assertTrue(section.contains("val deviceToken: String"))
        assertTrue(section.contains("val role: String"))
        assertTrue(section.contains("val scopes: List<String>"))
        assertTrue(section.contains("val issuedAtMs: Long? = null"))
    }
}
