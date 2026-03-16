package com.xiaomo.androidforclaw.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuRequireMentionAlignmentTest {
    @Test
    fun myApplicationSource_obeysFeishuConfigRequireMention() {
        val source = File("src/main/java/com/xiaomo/androidforclaw/core/MyApplication.kt").readText()
        assertTrue(source.contains("val requireMention = feishuConfig.requireMention"))
        assertFalse(source.contains("val requireMention = true"))
    }
}
