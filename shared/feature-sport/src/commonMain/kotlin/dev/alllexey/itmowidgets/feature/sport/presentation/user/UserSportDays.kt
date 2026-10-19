package dev.alllexey.itmowidgets.feature.sport.presentation.user

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.presentation.common.sportDayLabel
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toLessonUi
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportBookingDay
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportBookingItem
import kotlinx.datetime.toLocalDateTime

/** Another user's bookings by day as lesson cards, as Mine draws the viewer's own: `Сегодня, 7 сент.` over each day. */
fun userSportDays(bookings: List<SportBooking>, time: AcademicTimeProvider): List<SportBookingDay> {
    val today = time.today()
    return bookings.groupBy { it.start.toLocalDateTime(time.timeZone).date }.map { (date, day) ->
        SportBookingDay(sportDayLabel(date, today), day.map { SportBookingItem(it.lessonId, it.toLessonUi(time)) })
    }
}
