package dev.alllexey.itmowidgets.feature.sport.ui.home.preview

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Synthetic sport home cards for the previews and host tests, the debug host's `HomeFixture` on Monday 7 September
 * 2026 in Moscow time. No real person or pass.
 */
internal object SportHomePreviewSamples {

    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    private val date = LocalDate(2026, 9, 7)

    /** The default feed's card: a score and two queue entries, one a prediction. */
    fun card(): HomeCard.Sport = HomeCard.Sport(
        SportScoreSummary(attendances = 50, bonus = 22),
        listOf(booking(1, 16), booking(2, 18, prediction = true)),
    )

    /** A long section name and more queue entries than the card shows. */
    fun longNameCard(): HomeCard.Sport = HomeCard.Sport(
        SportScoreSummary(attendances = 30, bonus = 5),
        listOf(
            booking(1, 10).copy(sectionName = "Оздоровительная физическая культура для начинающих"),
            booking(2, 12, prediction = true),
            booking(3, 14),
            booking(4, 16),
            booking(5, 18),
        ),
    )

    /** No score yet, one predicted entry. */
    fun noScoreCard(): HomeCard.Sport = HomeCard.Sport(score = null, queue = listOf(booking(1, 16, prediction = true)))

    private fun booking(id: Long, hour: Int, prediction: Boolean = false) = PendingSportBooking(
        queueId = id, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100 + id, sectionName = "Плавание",
        start = LocalDateTime(date, LocalTime(hour, 0)).toInstant(zone),
        end = LocalDateTime(date, LocalTime(hour + 1, 30)).toInstant(zone),
        teacherFio = "Тренер Тестовый", roomName = "Бассейн", isPrediction = prediction,
    )
}
