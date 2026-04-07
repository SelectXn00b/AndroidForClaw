package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/types.ts
 */

enum class TaskStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

enum class TaskRuntime { INLINE, BACKGROUND, SCHEDULED }

data class TaskRecord(
    val id: String,
    val name: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val runtime: TaskRuntime = TaskRuntime.INLINE,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

data class TaskProgress(
    val taskId: String,
    val current: Int,
    val total: Int,
    val label: String? = null
)

data class TaskResult(
    val taskId: String,
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
    val durationMs: Long? = null
)
