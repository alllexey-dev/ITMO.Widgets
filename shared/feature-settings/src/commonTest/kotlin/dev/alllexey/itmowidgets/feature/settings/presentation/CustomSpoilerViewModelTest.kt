package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomSpoilerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `save updates state and writes exactly once even with repeated taps`() = runTest(main.dispatcher) {
        val repository = FakeCustomSpoilerRepository()
        val vm = CustomSpoilerViewModel(repository)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.configured)
        vm.saveImage("content://test/image")
        vm.saveImage("content://test/duplicate")
        vm.resetImage()
        runCurrent()
        assertTrue(vm.uiState.value.busy)
        assertEquals(1, repository.saved.size)
        repository.result.complete(true)
        advanceUntilIdle()
        assertEquals(CustomSpoilerUiState(configured = true), vm.uiState.value)
        assertEquals(CustomSpoilerEvent.SAVED, vm.events.first())
    }

    @Test
    fun `failed replacement preserves configured image`() = runTest(main.dispatcher) {
        val repository = FakeCustomSpoilerRepository(hasImage = true)
        val vm = CustomSpoilerViewModel(repository)
        advanceUntilIdle()
        vm.saveImage("content://test/invalid")
        repository.result.complete(false)
        advanceUntilIdle()
        assertEquals(CustomSpoilerUiState(configured = true), vm.uiState.value)
        assertEquals(CustomSpoilerEvent.FAILED, vm.events.first())
    }

    @Test
    fun `reset clears custom image`() = runTest(main.dispatcher) {
        val repository = FakeCustomSpoilerRepository(hasImage = true)
        val vm = CustomSpoilerViewModel(repository)
        advanceUntilIdle()
        vm.resetImage()
        repository.result.complete(true)
        advanceUntilIdle()
        assertEquals(CustomSpoilerUiState(configured = false), vm.uiState.value)
        assertEquals(CustomSpoilerEvent.RESET, vm.events.first())
    }
}
