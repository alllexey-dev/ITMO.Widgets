package dev.alllexey.itmowidgets.feature.sport.presentation.user

import dev.alllexey.itmowidgets.feature.sport.presentation.common.sportDayLabel
import dev.alllexey.itmowidgets.feature.sport.ui.preview.SportSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class UserSportDaysTest {

    @Test
    fun bookingsOfOneDayShareItsLabelInTheirOrder() {
        val bookings = SportSamples.friendBookings

        val days = userSportDays(bookings, SportSamples.time)

        assertEquals(listOf(sportDayLabel(LocalDate(2026, 9, 10), SportSamples.time.today())), days.map { it.title })
        assertEquals(listOf(bookings.map { it.lessonId }), days.map { day -> day.bookings.map { it.lessonId } })
    }
}
