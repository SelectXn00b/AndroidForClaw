package com.xiaomo.hermes.config

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuConfigAdapterAlignmentTest {
    @Test
    fun adapterSource_passesMentionBypassFields() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/config/FeishuConfigAdapter.kt").readText()
        assertTrue(source.contains("groupCommandMentionBypass = when (channelConfig.groupCommandMentionBypass.lowercase())"))
        assertTrue(source.contains("allowMentionlessInMultiBotGroup = channelConfig.allowMentionlessInMultiBotGroup"))
        assertTrue(source.contains("FeishuConfig.MentionBypass.SINGLE_BOT -> \"single_bot\""))
    }
}
