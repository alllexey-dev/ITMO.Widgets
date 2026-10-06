package dev.alllexey.itmowidgets.feature.sport.presentation.common

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

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

    /** The fixture lesson's slot: 8 September, 18:30 in Moscow. */
    private val date = LocalDate(2026, 9, 8)
    private val start = LocalTime(18, 30)

    private fun TestScope.holder() = SportBookingsHolder(
        bookings, data,
        bookingDelegate(bookings, FakeSportScheduleRepository(), data, backgroundScope, actions),
        FixedAcademicTime(),
        applicationScope()
    )

    private suspend fun emitSnapshot(items: List<SportBooking> = listOf(SportCardFixtures.booking(7))) {
        data.attempts.emit(LoadState.Content(SportAttempts(total = 3, used = 1, free = 2, canSignIn = true)))
        data.score.emit(LoadState.Content(SportScore(attendances = 40, other = 10, attendancesData = emptyList())))
        bookings.merged.emit(LoadState.Content(items))
    }

    private fun booking(id: Long, signed: Boolean, section: String) = SportCardFixtures.booking(id).copy(
        signed = signed,
        sectionName = SectionName(section),
        signEntry = if (signed) null else SportCardFixtures.entry()
    )

    @Test
    fun aSlotPrefersTheSignedAndNamedBookingThenSignedThenNamedThenTheFirst() = runTest(main.dispatcher) {
        val holder = holder()
        val signedNamed = booking(1, signed = true, section = "Фитнес")
        val signed = booking(2, signed = true, section = "Плавание")
        val named = booking(3, signed = false, section = "Фитнес (функциональная тренировка)")
        val first = booking(4, signed = false, section = "Йога")
        val otherSlot = booking(5, signed = true, section = "Фитнес").let {
            it.copy(start = it.start + 1.hours, end = it.end + 1.hours)
        }
        val subject = " ФИТНЕС (функциональная тренировка) "

        val cases = listOf(
            listOf(otherSlot, first, named, signed, signedNamed) to signedNamed,
            listOf(otherSlot, first, named, signed) to signed,
            listOf(otherSlot, first, named) to named,
            listOf(otherSlot, first) to first,
            listOf(otherSlot) to null
        )
        cases.forEachIndexed { index, (items, expected) ->
            emitSnapshot(items)
            assertEquals(expected, holder.findSportBookingAt(date, start, subject), "case $index")
        }
        emitSnapshot(listOf(first, signed))
        assertSame(signed, holder.findSportBookingAt(date, start, ""), "a blank subject names nothing")
    }

    @Test
    fun aLookupGivesUpAfterEightSecondsWithoutBookings() = runTest(main.dispatcher) {
        val holder = holder()
        val began = currentTime
        assertNull(holder.findSportBooking(7))
        assertEquals(8_000L, currentTime - began)
    }

    @Test
    fun failedBookingsAnswerNoLookup() = runTest(main.dispatcher) {
        val holder = holder()
        bookings.merged.emit(LoadState.Error(AppError.Network))
        assertNull(holder.findSportBooking(7))
    }

    @Test
    fun aSheetActionThatChangedOrOffersNothingYieldsNoCancelCandidate() = runTest(main.dispatcher) {
        val holder = holder()
        val signed = SportCardFixtures.booking(7)
        val unsigned = SportCardFixtures.booking(8).copy(signed = false)
        val queued = SportCardFixtures.booking(9).copy(signed = false, signEntry = SportCardFixtures.entry())
        emitSnapshot(listOf(signed, unsigned, queued))

        assertEquals(signed, holder.cancelCandidate(7, SportBookingAction.CANCEL.name))
        assertNull(holder.cancelCandidate(7, SportBookingAction.CANCEL_AUTO.name), "changed")
        assertNull(holder.cancelCandidate(7, null), "changed")
        assertNull(holder.cancelCandidate(8, SportBookingAction.NONE.name), "NONE")
        assertEquals(queued, holder.cancelCandidate(9, SportBookingAction.CANCEL_AUTO.name))
        assertNull(holder.cancelCandidate(10, SportBookingAction.CANCEL.name), "missing")
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
    fun aLoadedTabAnswersLookupsWithoutARequest() = runTest(main.dispatcher) {
        val holder = holder()
        emitSnapshot()
        runCurrent()

        assertEquals(7L, holder.findSportBooking(7)?.lessonId)
        holder.ensureDataLoaded()
        runCurrent()
        assertEquals(0, bookings.refreshCount)
    }

    @Test
    fun theFirstLookupLoadsTheTabOnceAndJoinsItsRefresh() = runTest(main.dispatcher) {
        val holder = holder()
        bookings.gate = CompletableDeferred()
        val lookup = async { holder.findSportBooking(7) }
        val second = async { holder.findSportBooking(7) }
        runCurrent()
        assertEquals(1, bookings.refreshCount)
        assertEquals(1, holder.operations.value)
        assertFalse(holder.refreshing.value)

        emitSnapshot()
        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(7L, lookup.await()?.lessonId)
        assertEquals(7L, second.await()?.lessonId)
        assertEquals(0, holder.operations.value)
    }

    @Test
    fun clearedRepositoriesMakeTheNextLookupLoadAgain() = runTest(main.dispatcher) {
        val holder = holder()
        emitSnapshot()
        runCurrent()
        assertEquals(7L, holder.findSportBooking(7)?.lessonId)
        assertEquals(0, bookings.refreshCount)

        bookings.merged.emit(LoadState.Loading)
        runCurrent()
        val lookup = async { holder.findSportBooking(7) }
        runCurrent()
        assertEquals(1, bookings.refreshCount)

        emitSnapshot()
        advanceUntilIdle()
        assertEquals(7L, lookup.await()?.lessonId)
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
