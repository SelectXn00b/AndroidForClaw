package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class DockerEnvironment(
    val image: String,
    val cwd: String,
    val timeout: Long,
    val cpu: String,
    val memory: String,
    val disk: String,
    val persistent_filesystem: String,
    val task_id: String,
    val volumes: String,
    val forward_env: String,
    val env: String,
    val network: String,
    val host_cwd: String,
    val auto_mount_cwd: String
) {
    private fun buildInitEnvArgs(): Any? {
    // Hermes: _build_init_env_args
        return null
        // Hermes: buildInitEnvArgs
        return null
    }
    private fun runBash(cmd_string: String): Unit {
    // Hermes: _run_bash
        // Hermes: runBash
    }
    private fun storageOptSupported(): Unit {
    // Hermes: _storage_opt_supported
        // Hermes: storageOptSupported
    }
    fun cleanup(): Unit {
    // Hermes: cleanup
        // Hermes: cleanup
    }

    /** Build -e KEY=VALUE args for injecting host env vars into init_session. */
    fun _buildInitEnvArgs(): List<String> {
        return emptyList()
    }
    /** Spawn a bash process inside the Docker container. */
    fun _runBash(cmdString: String): Any? {
        throw NotImplementedError("_runBash")
    }
    /** Check if Docker's storage driver supports --storage-opt size=. */
    fun _storageOptSupported(): Boolean {
        return false
    }

}
