package com.xiaomo.androidforclaw.tasks

/**
 * OpenClaw module: tasks
 * Source: OpenClaw/src/tasks/registry.ts
 */

typealias TaskFactory = suspend (params: Map<String, Any?>) -> TaskResult

object TaskRegistry {

    private val factories = mutableMapOf<String, TaskFactory>()

    fun registerTask(name: String, factory: TaskFactory) {
        factories[name] = factory
    }

    fun getTaskFactory(name: String): TaskFactory? = factories[name]

    fun listRegisteredTasks(): List<String> = factories.keys.toList()

    suspend fun runTask(name: String, params: Map<String, Any?> = emptyMap()): TaskResult {
        val factory = factories[name]
            ?: return TaskResult(taskId = "", success = false, error = "Task not found: $name")
        return factory(params)
    }

    fun clear() {
        factories.clear()
    }
}
