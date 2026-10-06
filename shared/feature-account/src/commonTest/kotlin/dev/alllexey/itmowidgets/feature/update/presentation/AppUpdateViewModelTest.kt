package dev.alllexey.itmowidgets.feature.update.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.update.FakeAppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class AppUpdateViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun rendersTheOfferItWasOpenedWith() = runTest(main.dispatcher) {
        val viewModel = createViewModel(note = "Новые виджеты", unsupported = true)

        assertEquals(
            AppUpdateUiState(installed = "2.1", latest = "2.2", note = "Новые виджеты", unsupported = true),
            viewModel.uiState.value
        )
    }

    @Test
    fun storesTheSkippedReleaseBeforeTheScreenIsClosed() = runTest(main.dispatcher) {
        val repository = FakeAppUpdateRepository()
        val viewModel = createViewModel(repository = repository)

        viewModel.skipVersion()
        advanceUntilIdle()

        assertEquals(AppVersionName("2.2"), repository.skippedVersion)
        assertEquals(AppUpdateEvent.Skipped, viewModel.events.first())
    }

    @Test
    fun aSecondSkipTapClosesTheScreenOnce() = runTest(main.dispatcher) {
        val viewModel = createViewModel()
        val events = mutableListOf<AppUpdateEvent>()
        backgroundScope.launch { viewModel.events.toList(events) }

        viewModel.skipVersion()
        viewModel.skipVersion()
        advanceUntilIdle()
        // advanceUntilIdle stops once only background work is left; the collector is background work.
        runCurrent()

        assertEquals<List<AppUpdateEvent>>(listOf(AppUpdateEvent.Skipped), events)
    }

    @Test
    fun aSkipStoredWhileNobodyListensReachesTheNextCollector() = runTest(main.dispatcher) {
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
                AppUpdateArgs.KEY_INSTALLED_VERSION to "2.1",
                AppUpdateArgs.KEY_LATEST_VERSION to "2.2",
                AppUpdateArgs.KEY_NOTE to note,
                AppUpdateArgs.KEY_UNSUPPORTED to unsupported
            )
        )
    )
}
