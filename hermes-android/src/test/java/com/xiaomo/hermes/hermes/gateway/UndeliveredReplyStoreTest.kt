package com.xiaomo.hermes.hermes.gateway

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * R-GW-003 bugfix (2026-06-06)：`UndeliveredReplyStore` 的 append/read/clear 契约。
 *
 * Store 用 JSONL 格式（每行一个 JSON），追加写——append-only，原子性靠 append 模式 + 单进程
 * 单 GatewayRunner 实例兜底（gateway controller 是 singleton + mutex）。
 *
 * 对应 TC-GW-173-a/b/c（见 docs/hermes-test-cases.md）。
 */
class UndeliveredReplyStoreTest {

    private lateinit var tmpFile: File

    @Before
    fun setUp() {
        tmpFile = File.createTempFile("undelivered-test-", ".jsonl")
        tmpFile.deleteOnExit()
        // Start clean
        tmpFile.writeText("")
    }

    @After
    fun tearDown() {
        if (tmpFile.exists()) tmpFile.delete()
    }

    /**
     * TC-GW-173-a: 一次 append 后 read 必须能拿回同样的字段。
     */
    @Test
    fun `TC-GW-173-a append then read returns same entry`() {
        val store = com.xiaomo.hermes.hermes.gateway.UndeliveredReplyStore(tmpFile)
        store.append(
            platform = "feishu",
            chatId = "oc_xxx",
            text = "agent's careful reply",
            error = "network timeout"
        )

        val entries = store.read()
        assertEquals("应当读到 1 条 entry", 1, entries.size)
        val entry = entries[0]
        assertEquals("feishu", entry.platform)
        assertEquals("oc_xxx", entry.chatId)
        assertEquals("agent's careful reply", entry.text)
        assertEquals("network timeout", entry.error)
        assertTrue("timestampMs 应该是正数", entry.timestampMs > 0)
    }

    /**
     * TC-GW-173-b: 3 次 append 后 read 必须返回 3 条按时间顺序排列。
     * 文件正好 3 行（不重写、不损坏前面的数据）。
     */
    @Test
    fun `TC-GW-173-b appends are durable and ordered`() {
        val store = com.xiaomo.hermes.hermes.gateway.UndeliveredReplyStore(tmpFile)
        store.append("feishu", "chat1", "msg1", "e1")
        Thread.sleep(2)
        store.append("weixin", "chat2", "msg2", "e2")
        Thread.sleep(2)
        store.append("feishu", "chat3", "msg3", "e3")

        val entries = store.read()
        assertEquals("应当读到 3 条", 3, entries.size)
        assertEquals("msg1", entries[0].text)
        assertEquals("msg2", entries[1].text)
        assertEquals("msg3", entries[2].text)
        // 文件正好 3 行（JSONL）
        val lineCount = tmpFile.readLines().filter { it.isNotBlank() }.size
        assertEquals("JSONL 文件应当正好 3 行", 3, lineCount)
        // 时间戳应当递增（允许相等，最低限度不倒退）
        assertTrue("时间戳不应倒退 (e1 <= e2)", entries[0].timestampMs <= entries[1].timestampMs)
        assertTrue("时间戳不应倒退 (e2 <= e3)", entries[1].timestampMs <= entries[2].timestampMs)
    }

    /**
     * TC-GW-173-c: clear 后 read 必须返回空；文件被清空或删除。
     */
    @Test
    fun `TC-GW-173-c clear truncates store`() {
        val store = com.xiaomo.hermes.hermes.gateway.UndeliveredReplyStore(tmpFile)
        store.append("feishu", "chat1", "msg1", "e1")
        store.append("feishu", "chat2", "msg2", "e2")

        store.clear()

        val entries = store.read()
        assertTrue("clear 后 read 必须返回空 list", entries.isEmpty())
        // 文件要么不存在、要么大小为 0
        val emptyOrGone = !tmpFile.exists() || tmpFile.length() == 0L
        assertTrue("clear 后文件应当被清空或删除", emptyOrGone)
    }
}
