package dev.alllexey.itmowidgets.feature.sport.ui.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.home_pending_predicted
import dev.alllexey.itmowidgets.shared.core.home_pending_waiting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class SportHomeCardFormatterTest {
    private val moscow = TimeZone.of("Europe/Moscow")
    private val formatter = SportHomeCardFormatter(moscow)

    @Test
    fun theSportCardShowsThreeQueueRowsAndCountsTheRest() {
        val queue = (1..5).map { booking(id = it.toLong()) }
        val card = formatter.format(HomeCard.Sport(SportScoreSummary(attendances = 50, bonus = 50), queue))

        assertEquals(HomeSportScoreUi(total = 90, remaining = 10), card.score)
        assertEquals(3, card.queue.size)
        assertEquals(2, card.more)
        assertEquals("пн, 7 сент. \u00B7 16:00\u201317:30", card.queue.first().subtitle)
        assertEquals(UiText.Res(Res.string.home_pending_waiting), card.queue.first().badge)
        assertEquals(queue.first().toDetailsArgs(moscow), card.queue.first().args)

        val noScore = formatter.format(HomeCard.Sport(null, queue.take(1)))
        assertNull(noScore.score)
        assertEquals(0, noScore.more)
    }

    @Test
    fun aPredictedEntryIsMarkedAsAPrediction() {
        val card = formatter.format(HomeCard.Sport(null, listOf(booking(id = 1).copy(isPrediction = true))))

        assertEquals(UiText.Res(Res.string.home_pending_predicted), card.queue.single().badge)
    }

    private fun booking(id: Long) = PendingSportBooking(
        queueId = id, queueKind = PendingSportBooking.QueueKind.FREE, lessonId = 100 + id, sectionName = "Плавание",
        start = Instant.parse("2026-09-07T13:00:00Z"), end = Instant.parse("2026-09-07T14:30:00Z"),
        teacherFio = "Тренер", roomName = "Бассейн", isPrediction = false,
    )
}
