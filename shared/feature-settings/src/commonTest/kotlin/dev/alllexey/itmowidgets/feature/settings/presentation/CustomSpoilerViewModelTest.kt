package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.testing.FakeCustomSpoilerRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CustomSpoilerViewModelTest {
    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun saveUpdatesStateAndWritesExactlyOnceEvenWithRepeatedTaps() = runTest(main.dispatcher) {
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
    fun failedReplacementPreservesConfiguredImage() = runTest(main.dispatcher) {
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
    fun resetClearsCustomImage() = runTest(main.dispatcher) {
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
