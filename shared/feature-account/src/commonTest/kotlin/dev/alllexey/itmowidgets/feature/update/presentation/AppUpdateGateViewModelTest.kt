package dev.alllexey.itmowidgets.feature.update.presentation

import dev.alllexey.itmowidgets.feature.update.FakeAppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.domain.PendingAppUpdate
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AppUpdateGateViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun offersThePendingUpdateOncePerProcess() = runTest(main.dispatcher) {
        val repository = FakeAppUpdateRepository(update())
        val viewModel = createViewModel(repository)

        // The activity re-renders its session state on every resume; the check must not follow it.
        viewModel.checkForUpdate()
        viewModel.checkForUpdate()
        advanceUntilIdle()

        assertEquals(update(), viewModel.events.first())
        assertEquals(1, repository.loads)
    }

    private fun createViewModel(repository: AppUpdateRepository) = AppUpdateGateViewModel(
        PendingAppUpdate(repository, object : Clock {
            override fun now(): Instant = Instant.parse("2026-09-15T10:00:00Z")
        })
    )

    private fun update() = AppUpdate(
        installed = AppVersionName("2.1"),
        latest = AppVersionName("2.2"),
        note = "",
        unsupported = false
    )
}
