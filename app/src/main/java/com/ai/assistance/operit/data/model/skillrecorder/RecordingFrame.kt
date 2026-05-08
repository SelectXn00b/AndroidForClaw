package com.ai.assistance.operit.data.model.skillrecorder

/**
 * 单帧录制数据：一次用户操作对应一帧
 */
data class RecordingFrame(
    /** 帧序号 */
    val index: Int,
    /** 时间戳 (ms) */
    val timestamp: Long,
    /** 事件类型 */
    val eventType: String,
    /** 事件详情 */
    val eventDetails: EventDetails,
    /** 当前 Activity 名 */
    val activityName: String? = null,
    /** 当前包名 */
    val packageName: String? = null,
    /** 压缩后的 UI 层级摘要文本 (操作后的界面状态) */
    val uiHierarchySummary: String = "",
    /** 操作前的 UI 层级摘要文本 (用于推断点击目标) */
    val previousUiHierarchy: String = ""
)

/**
 * 事件详情
 */
data class EventDetails(
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    /** 元素的资源 ID (如 com.example:id/btn_submit) */
    val resourceId: String? = null,
    /** TEXT_INPUT 事件的输入内容 */
    val inputText: String? = null,
    val additionalData: Map<String, String> = emptyMap()
)
