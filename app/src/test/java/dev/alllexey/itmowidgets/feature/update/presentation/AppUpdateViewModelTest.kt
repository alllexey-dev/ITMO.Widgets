package dev.alllexey.itmowidgets.feature.update.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
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
            viewModel.uiState
        )
    }

    @Test
    fun `stores the skipped release before the screen is closed`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAppUpdateRepository()
        val viewModel = createViewModel(repository = repository)

        viewModel.skipVersion()
        advanceUntilIdle()

        assertEquals(AppVersionName("2.2"), repository.skippedVersion)
        assertEquals(Unit, viewModel.skipped.first())
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

    private class FakeAppUpdateRepository : AppUpdateRepository {
        var skippedVersion: AppVersionName? = null
            private set

        override suspend fun loadUpdate(): AppUpdate? = null

        override suspend fun reminder() = AppUpdateReminder(AppVersionName("2.1"), Instant.EPOCH)

        override suspend fun markNotified() = Unit

        override suspend fun skip(version: AppVersionName) {
            skippedVersion = version
        }
    }
}
