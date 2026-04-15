package com.xiaomo.androidforclaw.hermes

// TODO: This is a stub file. Implement all classes and methods.

class PreparedModalExec {
    // Hermes: PreparedModalExec
}

class ModalExecStart {
    // Hermes: ModalExecStart
}

class BaseModalExecutionEnvironment {
    // Hermes: BaseModalExecutionEnvironment
    fun execute(command: String, cwd: String): Unit {
    // Hermes: execute
        // Hermes: execute
    }
    private fun beforeExecute(): Unit {
    // Hermes: _before_execute
        // Hermes: beforeExecute
    }
    private fun prepareModalExec(command: String): Unit {
    // Hermes: _prepare_modal_exec
        // Hermes: prepareModalExec
    }
    private fun result(output: String, returncode: String): Unit {
    // Hermes: _result
        // Hermes: result
    }
    private fun errorResult(output: String): Unit {
    // Hermes: _error_result
        // Hermes: errorResult
    }
    private fun timeoutResultForModal(timeout: Long): Unit {
    // Hermes: _timeout_result_for_modal
        // Hermes: timeoutResultForModal
    }
    private fun startModalExec(prepared: String): Unit {
    // Hermes: _start_modal_exec
        // Hermes: startModalExec
    }
    private fun pollModalExec(handle: String): Unit {
    // Hermes: _poll_modal_exec
        // Hermes: pollModalExec
    }
    private fun cancelModalExec(handle: String): Unit {
    // Hermes: _cancel_modal_exec
        // Hermes: cancelModalExec
    }

    /** Hook for backends that need pre-exec sync or validation. */
    fun _beforeExecute(): Unit {
        // TODO: implement _beforeExecute
    }
    fun _prepareModalExec(command: String): PreparedModalExec {
        throw NotImplementedError("_prepareModalExec")
    }
    fun _result(output: String, returncode: Int): Any? {
        return null
    }
    fun _errorResult(output: String): Any? {
        return null
    }
    fun _timeoutResultForModal(timeout: Int): Any? {
        return null
    }
    /** Begin a transport-specific exec. */
    fun _startModalExec(prepared: PreparedModalExec): ModalExecStart {
        throw NotImplementedError("_startModalExec")
    }
    /** Return a final result dict when complete, else ``None``. */
    fun _pollModalExec(handle: Any): Any? {
        return null
    }
    /** Cancel or terminate the active transport exec. */
    fun _cancelModalExec(handle: Any): Unit {
        // TODO: implement _cancelModalExec
    }

}
