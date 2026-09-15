package dev.alllexey.itmowidgets.feature.update.presentation

import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateReminder
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `offers the pending update once per process`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAppUpdateRepository(update())
        val viewModel = createViewModel(repository)

        // The activity re-renders its session state on every resume; the check must not follow it.
        viewModel.checkForUpdate()
        viewModel.checkForUpdate()
        advanceUntilIdle()

        assertEquals(update(), viewModel.offers.first())
        assertEquals(1, repository.loads)
    }

    private fun createViewModel(repository: AppUpdateRepository) = AppUpdateGateViewModel(
        PendingAppUpdate(repository, Clock.fixed(Instant.parse("2026-09-15T10:00:00Z"), ZoneOffset.UTC))
    )

    private fun update() = AppUpdate(
        installed = AppVersionName("2.1"),
        latest = AppVersionName("2.2"),
        note = "",
        unsupported = false
    )

    private class FakeAppUpdateRepository(private val update: AppUpdate?) : AppUpdateRepository {
        var loads = 0
            private set

        override suspend fun loadUpdate(): AppUpdate? {
            loads += 1
            return update
        }

        override suspend fun reminder() = AppUpdateReminder(AppVersionName("2.1"), Instant.EPOCH)

        override suspend fun markNotified() = Unit

        override suspend fun skip(version: AppVersionName) = Unit
    }
}
