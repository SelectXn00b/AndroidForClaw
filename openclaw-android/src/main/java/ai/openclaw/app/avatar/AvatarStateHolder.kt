package ai.openclaw.app.avatar

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

object AvatarStateHolder {

    private val _triggers = Channel<String>(Channel.BUFFERED)
    val triggers = _triggers.receiveAsFlow()

    private val _mouthOpen = MutableStateFlow(0f)
    val mouthOpen: StateFlow<Float> = _mouthOpen.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _paramOverrides = MutableStateFlow<Map<String, Float>>(emptyMap())
    val paramOverrides: StateFlow<Map<String, Float>> = _paramOverrides.asStateFlow()

    private val _currentParams = MutableStateFlow<Map<String, Float>>(emptyMap())
    val currentParams: StateFlow<Map<String, Float>> = _currentParams.asStateFlow()

    fun setPaused(paused: Boolean) {
        _paused.value = paused
    }

    fun fireTrigger(name: String) {
        _triggers.trySend(name)
    }

    fun setMouthOpen(value: Float) {
        _mouthOpen.value = value.coerceIn(0f, 1f)
    }

    fun updateCurrentParams(params: Map<String, Float>) {
        _currentParams.value = params
    }

    fun setParamOverrides(params: Map<String, Float>) {
        _paramOverrides.value = params
    }

    fun clearParamOverrides() {
        _paramOverrides.value = emptyMap()
    }
}
