package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.settings.domain.CustomSpoilerRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import kotlinx.coroutines.CompletableDeferred
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
    fun `save updates state and refreshes widgets exactly once even with repeated taps`() = runTest(main.dispatcher) {
        val repository = FakeRepository()
        val refresh = Refresh()
        val vm = CustomSpoilerViewModel(repository, refresh)
        advanceUntilIdle()
        assertEquals(false, vm.state.value.configured)
        vm.saveImage("content://test/image")
        vm.saveImage("content://test/duplicate")
        vm.resetImage()
        runCurrent()
        assertTrue(vm.state.value.busy)
        assertEquals(1, repository.saveCount)
        repository.result.complete(true)
        advanceUntilIdle()
        assertEquals(CustomSpoilerUiState(configured = true), vm.state.value)
        assertEquals(1, refresh.count)
        assertEquals(CustomSpoilerEvent.SAVED, vm.events.first())
    }

    @Test
    fun `failed replacement preserves configured image and does not refresh widgets`() = runTest(main.dispatcher) {
        val repository = FakeRepository(hasImage = true)
        val refresh = Refresh()
        val vm = CustomSpoilerViewModel(repository, refresh)
        advanceUntilIdle()
        vm.saveImage("content://test/invalid")
        repository.result.complete(false)
        advanceUntilIdle()
        assertEquals(CustomSpoilerUiState(configured = true), vm.state.value)
        assertEquals(0, refresh.count)
        assertEquals(CustomSpoilerEvent.FAILED, vm.events.first())
    }

    @Test
    fun `reset clears custom image and refreshes widgets`() = runTest(main.dispatcher) {
        val repository = FakeRepository(hasImage = true)
        val refresh = Refresh()
        val vm = CustomSpoilerViewModel(repository, refresh)
        advanceUntilIdle()
        vm.resetImage()
        repository.result.complete(true)
        advanceUntilIdle()
        assertEquals(CustomSpoilerUiState(configured = false), vm.state.value)
        assertEquals(CustomSpoilerEvent.RESET, vm.events.first())
        assertEquals(1, refresh.count)
    }

    private class FakeRepository(val hasImage: Boolean = false) : CustomSpoilerRepository {
        val result = CompletableDeferred<Boolean>()
        var saveCount = 0
        override suspend fun hasImage() = hasImage
        override suspend fun saveImage(sourceUri: String): Boolean {
            saveCount++
            return result.await()
        }
        override suspend fun resetImage() = result.await()
    }

    private class Refresh : WidgetRefreshRequester {
        var count = 0
        override fun refreshAll() { count++ }
    }
}
