package com.ai.assistance.operit.ui.features.memory.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ai.assistance.operit.data.model.CloudEmbeddingConfig
import com.ai.assistance.operit.data.model.EmbeddingDimensionUsage
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R-UI-002 持久化指令手动 toggle 单测。
 *
 * Repository 层（TC-UI-060/061）依赖真实 ObjectBox BoxStore，留 androidTest。
 * 此处只覆盖 ViewModel 层（TC-UI-062/063）—— mock MemoryRepository，
 * 验证 togglePersistentInstruction 在 on / off / memory 缺失三种路径下
 * 调用了正确的 repository 方法、且不动其它 mutate 方法。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MemoryViewModelPersistentToggleTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var repository: MemoryRepository
    private lateinit var viewModel: MemoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = mock {
            onBlocking { getMemoryGraph() }.thenReturn(Graph(emptyList(), emptyList()))
            onBlocking { getAllFolderPaths() }.thenReturn(emptyList())
            onBlocking { getEmbeddingDimensionUsage() }.thenReturn(EmbeddingDimensionUsage())
            on { loadCloudEmbeddingConfig() }.thenReturn(CloudEmbeddingConfig())
        }
        viewModel = MemoryViewModel(repository, context, profileId = "default")
        // 让 init 块里的协程全跑完
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** TC-UI-062-a: toggle on → addTagToMemory 被调一次；removeTagFromMemory 零调用。 */
    @Test
    fun `toggle on adds tag`() = runTest(dispatcher) {
        val memory = Memory().apply { id = 42L; title = "rule"; content = "always be polite" }
        whenever(repository.findMemoryById(42L)).thenReturn(memory)

        viewModel.togglePersistentInstruction(42L, enabled = true)
        advanceUntilIdle()

        verify(repository).addTagToMemory(memory, MemoryViewModel.PERSISTENT_INSTRUCTION_TAG)
        verify(repository, never()).removeTagFromMemory(any(), any())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    /** TC-UI-062-b: toggle off → removeTagFromMemory 被调一次；addTagToMemory 零调用。 */
    @Test
    fun `toggle off removes tag`() = runTest(dispatcher) {
        val memory = Memory().apply { id = 43L; title = "rule"; content = "speak in plain words" }
        whenever(repository.findMemoryById(43L)).thenReturn(memory)

        viewModel.togglePersistentInstruction(43L, enabled = false)
        advanceUntilIdle()

        verify(repository).removeTagFromMemory(memory, MemoryViewModel.PERSISTENT_INSTRUCTION_TAG)
        verify(repository, never()).addTagToMemory(any(), any())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    /** TC-UI-063-a: toggle 路径只该碰 tag CRUD 与查询；不该调任何其它 mutate 方法。 */
    @Test
    fun `toggle does not call other mutators`() = runTest(dispatcher) {
        val memory = Memory().apply { id = 44L; title = "rule"; content = "x" }
        whenever(repository.findMemoryById(44L)).thenReturn(memory)

        viewModel.togglePersistentInstruction(44L, enabled = true)
        advanceUntilIdle()

        verify(repository, never()).saveMemory(any())
        verify(repository, never()).updateMemory(
            any(), any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull()
        )
        verify(repository, never()).deleteMemory(any())
        verify(repository, never()).linkMemories(any(), any(), any(), any(), any())
    }

    /** TC-UI-063-b: findMemoryById 返回 null 时安全 no-op，isLoading 复位，不调 add/remove。 */
    @Test
    fun `toggle noop when memory missing`() = runTest(dispatcher) {
        whenever(repository.findMemoryById(eq(99L))).thenReturn(null)

        viewModel.togglePersistentInstruction(99L, enabled = true)
        advanceUntilIdle()

        verify(repository, never()).addTagToMemory(any(), any())
        verify(repository, never()).removeTagFromMemory(any(), any())
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }
}
