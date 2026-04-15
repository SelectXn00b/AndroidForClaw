package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class SingularityEnvironment(
    val image: String,
    val cwd: String,
    val timeout: Long,
    val cpu: String,
    val memory: String,
    val disk: String,
    val persistent_filesystem: String,
    val task_id: String
) {
    private fun startInstance(): Unit {
    // Hermes: _start_instance
        // Hermes: startInstance
    }
    private fun runBash(cmd_string: String): Unit {
    // Hermes: _run_bash
        // Hermes: runBash
    }
    fun cleanup(): Unit {
    // Hermes: cleanup
        // Hermes: cleanup
    }

    fun _startInstance(): Any? {
        return null
    }
    /** Spawn a bash process inside the Singularity instance. */
    fun _runBash(cmdString: String): Any? {
        throw NotImplementedError("_runBash")
    }

}
