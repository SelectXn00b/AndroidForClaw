package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class LocalEnvironment(
    val cwd: String,
    val timeout: Long,
    val env: String
) {
    fun getTempDir(): Any? {
    // Hermes: get_temp_dir
        return null
        // Hermes: getTempDir
        return java.io.File("/tmp")
    }
    private fun runBash(cmd_string: String): Unit {
    // Hermes: _run_bash
        // Hermes: runBash
    }
    private fun killProcess(proc: String): Unit {
    // Hermes: _kill_process
        // Hermes: killProcess
    }
    private fun updateCwd(result: String): Unit {
    // Hermes: _update_cwd
        // Hermes: updateCwd
    }
    fun cleanup(): Unit {
    // Hermes: cleanup
        // Hermes: cleanup
    }

    fun _runBash(cmdString: String): Any? {
        throw NotImplementedError("_runBash")
    }
    /** Kill the entire process group (all children). */
    fun _killProcess(proc: Any?): Any? {
        return null
    }
    /** Read CWD from temp file (local-only, no round-trip needed). */
    fun _updateCwd(result: Any?): Any? {
        return null
    }

}
