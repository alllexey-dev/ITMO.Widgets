package dev.alllexey.itmowidgets.feature.sport.domain.repository

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking

/** Sport activity of another user as Backend allows the viewer to see it. */
data class UserSportBookings(
    /** Confirmed current and upcoming lessons; details come from the sport catalog. */
    val confirmedLessonIds: List<Long>,
    /** Waiting free-sign and auto-sign entries, already shaped as bookings. */
    val pending: List<SportBooking>
)

interface UserSportRepository {

    suspend fun getUserBookings(isu: Int): AppResult<UserSportBookings>
}
