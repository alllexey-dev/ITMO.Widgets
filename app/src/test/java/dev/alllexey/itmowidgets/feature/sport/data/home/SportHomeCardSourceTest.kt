package dev.alllexey.itmowidgets.feature.sport.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.CustomDataState
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SportHomeCardSourceTest {
    private val sportData = FakeSportData()
    private val pending = FakePending()
    private val source = SportHomeCardSource(sportData, pending, Today)

    @Test
    fun `progress and future queues make the card`() = runTest {
        sportData.score.value = DataState.Success(SportScore(50, 22, emptyList()))
        pending.values.value = DataState.Success(listOf(booking(2, hour = 18), booking(1, hour = 16), booking(1, hour = 16), booking(3, hour = 9)))

        val card = source.observe().first().single() as HomeCard.Sport

        assertEquals(72, card.score?.total)
        assertEquals(listOf(1L, 2L), card.queue.map { it.queueId })
    }

    @Test
    fun `a scored semester without queues needs no card`() = runTest {
        sportData.score.value = DataState.Success(SportScore(80, 40, emptyList()))

        assertTrue(source.observe().first().isEmpty())

        pending.values.value = DataState.Success(listOf(booking(1, hour = 16)))
        assertEquals(1, (source.observe().first().single() as HomeCard.Sport).queue.size)
    }

    @Test
    fun `queues alone keep the card without a score`() = runTest {
        pending.values.value = DataState.Success(listOf(booking(1, hour = 16)))

        val card = source.observe().first().single() as HomeCard.Sport

        assertNull(card.score)
        assertTrue(source.observe().first().isNotEmpty())
    }

    @Test
    fun `refresh reports the score error after asking both sources`() = runTest {
        sportData.score.value = DataState.Error(AppError.Network)

        assertEquals(AppResult.Failure(AppError.Network), source.refresh())
        assertEquals(1, sportData.scoreRefreshes)
        assertEquals(1, pending.refreshes)

        sportData.score.value = DataState.Success(SportScore(1, 0, emptyList()))
        assertEquals(AppResult.Success(Unit), source.refresh())
    }

    private fun booking(id: Long, hour: Int) = PendingSportBooking(
        queueId = id, queueKind = PendingSportBooking.QueueKind.FREE, lessonId = 100 + id, sectionName = "Бассейн",
        start = Today.today().atTime(hour, 0).atZone(Today.zoneId).toOffsetDateTime(),
        end = Today.today().atTime(hour + 1, 30).atZone(Today.zoneId).toOffsetDateTime(),
        teacherFio = "Тренер", roomName = "Бассейн", isPrediction = false
    )

    private object Today : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
        override fun today(): LocalDate = LocalDate.of(2026, 9, 7)
        override fun now() = today().atTime(12, 0).atZone(zoneId).toOffsetDateTime()
    }

    private class FakePending : PendingSportBookingsRepository {
        val values = MutableStateFlow<DataState<List<PendingSportBooking>>>(DataState.Success(emptyList()))
        var refreshes = 0
        override fun observePendingBookings() = values
        override suspend fun refresh() { refreshes++ }
    }

    private class FakeSportData : SportDataRepository {
        val score = MutableStateFlow<DataState<SportScore>>(DataState.Error(AppError.Network))
        var scoreRefreshes = 0
        override fun observeSportScore(): Flow<DataState<SportScore>> = score
        override suspend fun refreshSportScore() { scoreRefreshes++ }
        override fun observeSportAttempts(): Flow<DataState<SportAttempts>> = flowOf(DataState.Error(AppError.Unknown()))
        override suspend fun refreshSportAttempts() = Unit
        override fun observeSportAutoSignLimits(): Flow<CustomDataState<SportAutoSignLimits>> = flowOf(CustomDataState.Disabled)
        override suspend fun refreshSportAutoSignLimits() = Unit
        override fun observeSportQueueEntries(): Flow<CustomDataState<List<SportQueueEntry>>> = flowOf(CustomDataState.Disabled)
        override suspend fun refreshSportQueueEntries() = Unit
        override fun observeSportQueues(): Flow<CustomDataState<List<SportQueue>>> = flowOf(CustomDataState.Disabled)
        override suspend fun refreshSportQueues() = Unit
        override fun observeFriendsBookings(): Flow<CustomDataState<List<FriendSportBooking>>> = flowOf(CustomDataState.Disabled)
        override suspend fun refreshFriendsBookings() = Unit
    }
}
