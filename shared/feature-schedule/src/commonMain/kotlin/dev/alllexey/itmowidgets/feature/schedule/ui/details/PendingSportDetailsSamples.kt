package dev.alllexey.itmowidgets.feature.schedule.ui.details

import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import kotlinx.datetime.TimeZone

/**
 * Synthetic pending bookings of the pending sport sheet's previews and host tests, on the schedule references'
 * Monday, 7 September 2026, as `PendingSportBooking.toDetailsArgs` writes them. Only made-up names.
 */
internal object PendingSportDetailsSamples {

    const val TEACHER_ISU = 300002
    const val SECTION = "Современные танцы: тестовая секция с длинным названием"
    const val TEACHER_NAME = "Тестовый преподаватель с очень длинным именем"
    const val ROOM = "Кронверкский проспект, 49, спортивный зал"

    val timeZone = TimeZone.of("Europe/Moscow")

    /** A free queue the student waits in. */
    val waiting = PendingSportDetailsArgs(
        lessonId = 100, sectionName = SECTION, autoSign = false, isPrediction = false,
        start = "2026-09-07T17:00+03:00", end = "2026-09-07T18:30+03:00", teacherFio = TEACHER_NAME,
        roomName = ROOM, teacherIsu = TEACHER_ISU,
    )

    /** An auto-sign queue for a lesson MyITMO has not published yet. */
    val autoQueue = waiting.copy(lessonId = 150, autoSign = true)

    /** The auto-sign prediction from the section's prototype. */
    val predicted = waiting.copy(
        lessonId = 200, autoSign = true, isPrediction = true,
        start = "2026-09-07T18:40+03:00", end = "2026-09-07T20:10+03:00",
    )

    /** No teacher ISU and no room: the teacher stays text, the map leaves. */
    val bare = waiting.copy(teacherIsu = null, roomName = "")

    fun state(booking: PendingSportDetailsArgs) = PendingSportDetailsSheetState(booking, timeZone)
}
