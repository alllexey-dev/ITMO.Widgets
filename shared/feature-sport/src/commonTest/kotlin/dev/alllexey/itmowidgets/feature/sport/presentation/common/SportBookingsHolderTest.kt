package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportActionRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.FakeSportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.applicationScope
import dev.alllexey.itmowidgets.feature.sport.presentation.bookingDelegate
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyEvent
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyViewModel
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SportBookingsHolderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    private val bookings = FakeSportBookingRepository()
    private val data = FakeSportDataRepository()
    private val actions = FakeSportActionRepository()

    private fun TestScope.holder() = SportBookingsHolder(
        bookings, data,
        bookingDelegate(bookings, FakeSportScheduleRepository(), data, backgroundScope, actions),
        applicationScope()
    )

    private suspend fun emitSnapshot(items: List<SportBooking> = listOf(SportCardFixtures.booking(7))) {
        data.attempts.emit(LoadState.Content(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(items))
    }

    @Test
    fun aCancellationErrorFromTheActivityReachesTheTabThatOpensLater() = runTest(main.dispatcher) {
        val holder = holder()
        emitSnapshot()
        actions.result = AppResult.Failure(AppError.Network)

        holder.cancel(SportCardFixtures.booking(7))
        advanceUntilIdle()

        val tab = SportMyViewModel(bookings, data, holder)
        assertEquals(SportMyEvent.ShowError(AppError.Network), tab.events.first())
    }

    @Test
    fun aFailureOfAClearedSessionDoesNotKeepTheNextEntryFromLoading() = runTest(main.dispatcher) {
        val holder = holder()
        data.attempts.emit(LoadState.Error(AppError.Unauthorized))
        data.score.emit(LoadState.Error(AppError.Unauthorized))
        bookings.merged.emit(LoadState.Content(emptyList()))
        runCurrent()
        holder.ensureDataLoaded()
        runCurrent()
        assertEquals(0, bookings.refreshCount, "a failure stays until a retry")

        data.attempts.emit(LoadState.Loading)
        data.score.emit(LoadState.Loading)
        runCurrent()
        holder.ensureDataLoaded()
        runCurrent()
        assertEquals(1, bookings.refreshCount)
    }
}
