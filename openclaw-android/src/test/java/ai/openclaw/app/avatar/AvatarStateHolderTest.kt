package ai.openclaw.app.avatar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)

class AvatarStateHolderTest {

    @Before
    fun reset() {
        AvatarStateHolder.setMouthOpen(0f)
        AvatarStateHolder.setPaused(false)
        AvatarStateHolder.clearParamOverrides()
    }

    // ════════ MouthOpen ════════

    @Test
    fun `initial mouthOpen is 0`() {
        assertEquals(0f, AvatarStateHolder.mouthOpen.value, 0.001f)
    }

    @Test
    fun `setMouthOpen updates flow`() {
        AvatarStateHolder.setMouthOpen(0.5f)
        assertEquals(0.5f, AvatarStateHolder.mouthOpen.value, 0.001f)
    }

    @Test
    fun `setMouthOpen clamps to 0-1 range`() {
        AvatarStateHolder.setMouthOpen(-0.5f)
        assertEquals(0f, AvatarStateHolder.mouthOpen.value, 0.001f)

        AvatarStateHolder.setMouthOpen(2.5f)
        assertEquals(1f, AvatarStateHolder.mouthOpen.value, 0.001f)
    }

    // ════════ Paused ════════

    @Test
    fun `initial paused is false`() {
        assertEquals(false, AvatarStateHolder.paused.value)
    }

    @Test
    fun `setPaused updates flow`() {
        AvatarStateHolder.setPaused(true)
        assertEquals(true, AvatarStateHolder.paused.value)
    }

    // ════════ ParamOverrides ════════

    @Test
    fun `initial paramOverrides is empty`() {
        assertEquals(emptyMap<String, Float>(), AvatarStateHolder.paramOverrides.value)
    }

    @Test
    fun `setParamOverrides updates flow`() {
        val params = mapOf("ParamAngleX" to 15f, "ParamCheek" to 1f)
        AvatarStateHolder.setParamOverrides(params)
        assertEquals(params, AvatarStateHolder.paramOverrides.value)
    }

    @Test
    fun `clearParamOverrides resets to empty`() {
        AvatarStateHolder.setParamOverrides(mapOf("ParamAngleX" to 15f))
        AvatarStateHolder.clearParamOverrides()
        assertEquals(emptyMap<String, Float>(), AvatarStateHolder.paramOverrides.value)
    }

    // ════════ Triggers ════════

    @Test
    fun `fireTrigger sends to channel`() = runTest {
        val received = mutableListOf<String>()
        val job = launch {
            AvatarStateHolder.triggers.collect { received.add(it) }
        }

        AvatarStateHolder.fireTrigger("smile")
        AvatarStateHolder.fireTrigger("wave")
        AvatarStateHolder.fireTrigger("nod")

        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("smile", "wave", "nod"), received)
    }

    @Test
    fun `fireTrigger does not block on buffered channel`() {
        AvatarStateHolder.fireTrigger("smile")
        AvatarStateHolder.fireTrigger("wave")
        AvatarStateHolder.fireTrigger("surprise")
        AvatarStateHolder.fireTrigger("celebrate")
    }
}
