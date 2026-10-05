package dev.alllexey.itmowidgets.feature.update.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.update.FakeAppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `renders the offer it was opened with`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(note = "Новые виджеты", unsupported = true)

        assertEquals(
            AppUpdateUiState(installed = "2.1", latest = "2.2", note = "Новые виджеты", unsupported = true),
            viewModel.uiState.value
        )
    }

    @Test
    fun `stores the skipped release before the screen is closed`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAppUpdateRepository()
        val viewModel = createViewModel(repository = repository)

        viewModel.skipVersion()
        advanceUntilIdle()

        assertEquals(AppVersionName("2.2"), repository.skippedVersion)
        assertEquals(AppUpdateEvent.Skipped, viewModel.events.first())
    }

    @Test
    fun `a second skip tap closes the screen once`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val events = mutableListOf<AppUpdateEvent>()
        backgroundScope.launch { viewModel.events.toList(events) }

        viewModel.skipVersion()
        viewModel.skipVersion()
        advanceUntilIdle()
        // advanceUntilIdle stops once only background work is left; the collector is background work.
        runCurrent()

        assertEquals(listOf(AppUpdateEvent.Skipped), events)
    }

    @Test
    fun `a skip stored while nobody listens reaches the next collector`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        val first = backgroundScope.launch { viewModel.events.collect {} }
        runCurrent()
        first.cancel()

        viewModel.skipVersion()
        advanceUntilIdle()

        assertEquals(AppUpdateEvent.Skipped, viewModel.events.first())
    }

    private fun createViewModel(
        repository: FakeAppUpdateRepository = FakeAppUpdateRepository(),
        note: String = "",
        unsupported: Boolean = false
    ) = AppUpdateViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(
            mapOf(
                AppUpdateViewModel.ARG_INSTALLED_VERSION to "2.1",
                AppUpdateViewModel.ARG_LATEST_VERSION to "2.2",
                AppUpdateViewModel.ARG_NOTE to note,
                AppUpdateViewModel.ARG_UNSUPPORTED to unsupported
            )
        )
    )
}
