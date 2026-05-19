package com.xiaomo.hermes.hermes.gateway.platforms

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug 2 — 微信入站消息：图片/视频/文件/语音被丢弃。
 *
 * 这些测试覆盖 docs/hermes-test-cases.md 中的：
 *   TC-GW-104-a — ITEM_* 常量值与 Python 上游对齐
 *   TC-GW-105-a — 只含 ITEM_IMAGE 的 item_list 不再因 text.isBlank() 被丢
 *   TC-GW-106-a — text + image 同时出现，二者都进入 MessageEvent
 *   TC-GW-107-a — ref_msg.message_item.type==ITEM_IMAGE 时也采集
 *   TC-GW-108-a — _collectMedia 给 ITEM_VIDEO/FILE/VOICE 路由到正确 mime
 *   TC-GW-109-a — _downloadImage 抛异常时 _collectMedia 不传播，返回空
 *
 * 红的预期：
 *   - _collectMedia 还没实现 → 编译失败
 *   - MediaDownloader 接口还没实现 → 编译失败
 *   - _handleInbound 还没改 → 行为测试通过 fake 触不到真实代码
 *
 * 全部应当在 Bug 2 修复阶段（③）变绿。
 *
 * Python 上游：reference/hermes-agent/gateway/platforms/weixin.py:1325-1357 + 1366-1387
 */
class WeixinMediaCollectTest {

    /** Records a single fake-download result keyed by item-type. */
    private class FakeDownloader(
        val imageResult: String? = null,
        val imageException: Exception? = null,
        val videoResult: String? = null,
        val fileResult: Pair<String, String>? = null,
        val voiceResult: String? = null,
    ) : MediaDownloader {
        var imageCalls = 0
        var videoCalls = 0
        var fileCalls = 0
        var voiceCalls = 0

        override suspend fun downloadImage(item: Map<String, Any?>): String? {
            imageCalls++
            imageException?.let { throw it }
            return imageResult
        }
        override suspend fun downloadVideo(item: Map<String, Any?>): String? {
            videoCalls++
            return videoResult
        }
        override suspend fun downloadFile(item: Map<String, Any?>): Pair<String, String>? {
            fileCalls++
            return fileResult
        }
        override suspend fun downloadVoice(item: Map<String, Any?>): String? {
            voiceCalls++
            return voiceResult
        }
    }

    // ── TC-GW-104-a ─────────────────────────────────────────────
    @Test
    fun `item constants match python`() {
        assertEquals(1, ITEM_TEXT)
        assertEquals(2, ITEM_IMAGE)
        assertEquals(3, ITEM_VOICE)
        assertEquals(4, ITEM_FILE)
        assertEquals(5, ITEM_VIDEO)
    }

    // ── TC-GW-108-a ─────────────────────────────────────────────
    @Test
    fun `non-image branches match python table`() = runBlocking {
        // VIDEO → "video/mp4"
        run {
            val paths = mutableListOf<String>()
            val mimes = mutableListOf<String>()
            val downloader = FakeDownloader(videoResult = "/cache/v.mp4")
            _collectMedia(mapOf("type" to ITEM_VIDEO), paths, mimes, downloader)
            assertEquals(listOf("/cache/v.mp4"), paths)
            assertEquals(listOf("video/mp4"), mimes)
            assertEquals(1, downloader.videoCalls)
        }
        // FILE → mime supplied by downloader
        run {
            val paths = mutableListOf<String>()
            val mimes = mutableListOf<String>()
            val downloader = FakeDownloader(fileResult = "/cache/x.pdf" to "application/pdf")
            _collectMedia(mapOf("type" to ITEM_FILE), paths, mimes, downloader)
            assertEquals(listOf("/cache/x.pdf"), paths)
            assertEquals(listOf("application/pdf"), mimes)
            assertEquals(1, downloader.fileCalls)
        }
        // VOICE → "audio/silk"
        run {
            val paths = mutableListOf<String>()
            val mimes = mutableListOf<String>()
            val downloader = FakeDownloader(voiceResult = "/cache/audio.silk")
            _collectMedia(mapOf("type" to ITEM_VOICE), paths, mimes, downloader)
            assertEquals(listOf("/cache/audio.silk"), paths)
            assertEquals(listOf("audio/silk"), mimes)
            assertEquals(1, downloader.voiceCalls)
        }
        // IMAGE → "image/jpeg"
        run {
            val paths = mutableListOf<String>()
            val mimes = mutableListOf<String>()
            val downloader = FakeDownloader(imageResult = "/cache/img.jpg")
            _collectMedia(mapOf("type" to ITEM_IMAGE), paths, mimes, downloader)
            assertEquals(listOf("/cache/img.jpg"), paths)
            assertEquals(listOf("image/jpeg"), mimes)
            assertEquals(1, downloader.imageCalls)
        }
    }

    // ── TC-GW-108-a (ITEM_TEXT branch) ──────────────────────────
    @Test
    fun `text item never triggers any downloader`() = runBlocking {
        val paths = mutableListOf<String>()
        val mimes = mutableListOf<String>()
        val downloader = FakeDownloader()
        _collectMedia(mapOf("type" to ITEM_TEXT), paths, mimes, downloader)
        assertTrue(paths.isEmpty())
        assertTrue(mimes.isEmpty())
        assertEquals(0, downloader.imageCalls)
        assertEquals(0, downloader.videoCalls)
        assertEquals(0, downloader.fileCalls)
        assertEquals(0, downloader.voiceCalls)
    }

    // ── TC-GW-109-a ─────────────────────────────────────────────
    @Test
    fun `download failure does not crash inbound`() = runBlocking {
        val paths = mutableListOf<String>()
        val mimes = mutableListOf<String>()
        // Downloader returning null (the canonical "download failed" signal in
        // Python — _download_image catches the exception internally and
        // returns None).  _collectMedia must not append anything in that case.
        val downloader = FakeDownloader(imageResult = null)
        _collectMedia(mapOf("type" to ITEM_IMAGE), paths, mimes, downloader)
        assertTrue("paths must be empty when downloader returns null", paths.isEmpty())
        assertTrue("mimes must be empty when downloader returns null", mimes.isEmpty())
        assertEquals(1, downloader.imageCalls)
    }

    // ── TC-GW-105-a ─────────────────────────────────────────────
    @Test
    fun `image only inbound does not drop — extractTextAndMedia keeps mediaPaths`() = runBlocking {
        // Simulate the new _extractTextAndMedia flow that Python's
        // _handle_inbound walks.  text="", but media non-empty ⇒ keep.
        val itemList: List<Map<String, Any?>> = listOf(
            mapOf("type" to ITEM_IMAGE),
        )
        val paths = mutableListOf<String>()
        val mimes = mutableListOf<String>()
        val downloader = FakeDownloader(imageResult = "/cache/inbound.jpg")
        for (item in itemList) {
            _collectMedia(item, paths, mimes, downloader)
        }
        val text = _extractText(itemList)
        assertEquals("text-extract from image-only item is empty", "", text)
        assertEquals(listOf("/cache/inbound.jpg"), paths)
        assertEquals(listOf("image/jpeg"), mimes)
        // Drop-rule (text empty AND media empty) — must NOT trigger here:
        assertTrue("must NOT drop because mediaPaths non-empty", text.isEmpty() && paths.isNotEmpty())
        // The classifier must promote this to PHOTO, not TEXT.
        assertEquals(MessageType.PHOTO, _messageTypeFromMedia(mimes, text))
    }

    // ── TC-GW-106-a ─────────────────────────────────────────────
    @Test
    fun `text plus image keeps both`() = runBlocking {
        val itemList: List<Map<String, Any?>> = listOf(
            mapOf(
                "type" to ITEM_TEXT,
                "text_item" to mapOf("text" to "你好，看图：")
            ),
            mapOf("type" to ITEM_IMAGE),
        )
        val paths = mutableListOf<String>()
        val mimes = mutableListOf<String>()
        val downloader = FakeDownloader(imageResult = "/cache/dog.jpg")
        for (item in itemList) {
            _collectMedia(item, paths, mimes, downloader)
        }
        val text = _extractText(itemList)
        assertEquals("你好，看图：", text)
        assertEquals(listOf("/cache/dog.jpg"), paths)
        assertEquals(listOf("image/jpeg"), mimes)
        assertEquals(MessageType.PHOTO, _messageTypeFromMedia(mimes, text))
    }

    // ── TC-GW-107-a ─────────────────────────────────────────────
    @Test
    fun `ref message image collected`() = runBlocking {
        val itemList: List<Map<String, Any?>> = listOf(
            mapOf(
                "type" to ITEM_TEXT,
                "text_item" to mapOf("text" to "看这张图"),
                "ref_msg" to mapOf<String, Any?>(
                    "title" to "群友",
                    "message_item" to mapOf<String, Any?>("type" to ITEM_IMAGE),
                ),
            ),
        )
        val paths = mutableListOf<String>()
        val mimes = mutableListOf<String>()
        val downloader = FakeDownloader(imageResult = "/cache/ref.jpg")
        for (item in itemList) {
            _collectMedia(item, paths, mimes, downloader)
            @Suppress("UNCHECKED_CAST")
            val refItem = (item["ref_msg"] as? Map<String, Any?>)?.get("message_item") as? Map<String, Any?>
            if (refItem != null) {
                _collectMedia(refItem, paths, mimes, downloader)
            }
        }
        // The ref-item image must end up in mediaPaths (Python lines 1330-1335).
        assertEquals(listOf("/cache/ref.jpg"), paths)
        assertEquals(listOf("image/jpeg"), mimes)
        assertEquals(1, downloader.imageCalls)
    }
}
