package com.xiaomo.feishu.messaging

import org.junit.Assert.*
import org.junit.Test

/**
 * FeishuContentParser 单元测试
 * 验证各消息类型解析、富文本转 Markdown、媒体 key 提取
 */
class FeishuContentParserTest {

    // ===== Text =====

    @Test
    fun `text message extracts text field`() {
        val result = FeishuContentParser.parseMessageContent("text", """{"text":"hello world"}""")
        assertEquals("hello world", result.text)
        assertNull(result.mediaKeys)
    }

    @Test
    fun `text message with mention placeholders`() {
        val result = FeishuContentParser.parseMessageContent("text", """{"text":"@_user_1 hello"}""")
        assertEquals("@_user_1 hello", result.text)
    }

    @Test
    fun `text message malformed JSON falls back to raw`() {
        val result = FeishuContentParser.parseMessageContent("text", "not json")
        assertEquals("not json", result.text)
    }

    // ===== Image =====

    @Test
    fun `image message extracts image_key`() {
        val result = FeishuContentParser.parseMessageContent("image", """{"image_key":"img_v2_abc123"}""")
        assertEquals("[图片]", result.text)
        assertNotNull(result.mediaKeys)
        assertEquals("img_v2_abc123", result.mediaKeys!!.imageKey)
        assertEquals("image", result.mediaKeys!!.mediaType)
    }

    // ===== File =====

    @Test
    fun `file message extracts file_key and name`() {
        val result = FeishuContentParser.parseMessageContent("file", """{"file_key":"file_abc","file_name":"report.pdf"}""")
        assertEquals("[文件: report.pdf]", result.text)
        assertNotNull(result.mediaKeys)
        assertEquals("file_abc", result.mediaKeys!!.fileKey)
        assertEquals("report.pdf", result.mediaKeys!!.fileName)
        assertEquals("file", result.mediaKeys!!.mediaType)
    }

    // ===== Audio =====

    @Test
    fun `audio message extracts file_key`() {
        val result = FeishuContentParser.parseMessageContent("audio", """{"file_key":"audio_abc"}""")
        assertEquals("[语音]", result.text)
        assertEquals("audio_abc", result.mediaKeys!!.fileKey)
        assertEquals("audio", result.mediaKeys!!.mediaType)
    }

    // ===== Video =====

    @Test
    fun `video message extracts file_key and image_key`() {
        val result = FeishuContentParser.parseMessageContent("video", """{"file_key":"video_abc","image_key":"thumb_abc"}""")
        assertEquals("[视频]", result.text)
        assertEquals("video_abc", result.mediaKeys!!.fileKey)
        assertEquals("thumb_abc", result.mediaKeys!!.imageKey)
    }

    // ===== Sticker =====

    @Test
    fun `sticker message extracts file_key`() {
        val result = FeishuContentParser.parseMessageContent("sticker", """{"file_key":"sticker_abc"}""")
        assertEquals("[表情]", result.text)
        assertEquals("sticker_abc", result.mediaKeys!!.fileKey)
    }

    // ===== Share =====

    @Test
    fun `share_chat message extracts name`() {
        val result = FeishuContentParser.parseMessageContent("share_chat", """{"chat_name":"工程群","share_chat_id":"oc_123"}""")
        assertEquals("[分享群: 工程群]", result.text)
    }

    @Test
    fun `share_chat without name falls back to id`() {
        val result = FeishuContentParser.parseMessageContent("share_chat", """{"share_chat_id":"oc_123"}""")
        assertEquals("[分享群: oc_123]", result.text)
    }

    // ===== Post (Rich Text) =====

    @Test
    fun `post with title and text paragraphs`() {
        val content = """{
            "title": "Test Title",
            "content": [
                [{"tag":"text","text":"Hello "},{"tag":"text","text":"World","style":{"bold":true}}],
                [{"tag":"a","text":"Click here","href":"https://example.com"}]
            ]
        }"""
        val result = FeishuContentParser.parsePostContent(content)
        assertTrue(result.contains("## Test Title"))
        assertTrue(result.contains("Hello "))
        assertTrue(result.contains("**World**"))
        assertTrue(result.contains("[Click here](https://example.com)"))
    }

    @Test
    fun `post with nested zh_cn locale`() {
        val content = """{
            "post": {
                "zh_cn": {
                    "title": "中文标题",
                    "content": [
                        [{"tag":"text","text":"你好世界"}]
                    ]
                }
            }
        }"""
        val result = FeishuContentParser.parsePostContent(content)
        assertTrue(result.contains("## 中文标题"))
        assertTrue(result.contains("你好世界"))
    }

    @Test
    fun `post with code_block`() {
        val content = """{
            "content": [
                [{"tag":"code_block","language":"python","text":"print('hello')"}]
            ]
        }"""
        val result = FeishuContentParser.parsePostContent(content)
        assertTrue(result.contains("```python"))
        assertTrue(result.contains("print('hello')"))
    }

    @Test
    fun `post with at mention`() {
        val content = """{
            "content": [
                [{"tag":"at","user_name":"张三"},{"tag":"text","text":" 看一下这个"}]
            ]
        }"""
        val result = FeishuContentParser.parsePostContent(content)
        assertTrue(result.contains("@张三"))
        assertTrue(result.contains("看一下这个"))
    }

    @Test
    fun `post with styled text`() {
        val content = """{
            "content": [
                [{"tag":"text","text":"italic","style":{"italic":true}},
                 {"tag":"text","text":"strikethrough","style":{"strikethrough":true}},
                 {"tag":"text","text":"code","style":{"code":true}}]
            ]
        }"""
        val result = FeishuContentParser.parsePostContent(content)
        assertTrue(result.contains("*italic*"))
        assertTrue(result.contains("~~strikethrough~~"))
        assertTrue(result.contains("`code`"))
    }

    // ===== Merge Forward =====

    @Test
    fun `merge forward parses sub-messages`() {
        val content = """[
            {"msg_type":"text","body":{"content":"{\"text\":\"hello\"}"}},
            {"msg_type":"image","body":{"content":"{}"}},
            {"msg_type":"file","body":{"content":"{}"}}
        ]"""
        val result = FeishuContentParser.parseMergeForwardContent(content)
        assertTrue(result.contains("[合并转发消息]"))
        assertTrue(result.contains("hello"))
        assertTrue(result.contains("[图片]"))
        assertTrue(result.contains("[文件]"))
    }

    @Test
    fun `merge forward with messages key`() {
        val content = """{"messages":[
            {"msg_type":"text","body":{"content":"{\"text\":\"test\"}"}}
        ]}"""
        val result = FeishuContentParser.parseMergeForwardContent(content)
        assertTrue(result.contains("test"))
    }

    // ===== Unknown type =====

    @Test
    fun `unknown message type returns raw content`() {
        val result = FeishuContentParser.parseMessageContent("unknown_type", "raw content here")
        assertEquals("raw content here", result.text)
    }
}
