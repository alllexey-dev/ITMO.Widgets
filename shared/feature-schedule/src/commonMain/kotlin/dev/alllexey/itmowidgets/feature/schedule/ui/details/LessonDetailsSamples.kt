package dev.alllexey.itmowidgets.feature.schedule.ui.details

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Synthetic lessons, friends and changes of the lesson sheet's previews and host tests, on the schedule references'
 * Monday, 7 September 2026. Only made-up names and the `.invalid` domain.
 */
internal object LessonDetailsSamples {

    const val TEACHER_ISU = 300001
    const val FRIEND_ISU = 300100
    const val TEACHER_NAME = "Тестовый преподаватель с очень длинным именем"
    const val FLOW = "ФИЗ ПИИКТ 3.2"
    const val LONG_FLOW = "Тестовый поток с очень длинным названием, которое не помещается в одну строку экрана 12345"

    val date = LocalDate(2026, 9, 7)

    /** Everything a lesson can carry: teacher with a profile, flow, room, link with a password and a note. */
    val full = LessonDetailsArgs(
        pairId = 2, date = date.toString(), subjectName = "Физика", typeId = 3, format = "Очный",
        start = "10:00", end = "11:30", teacherFio = TEACHER_NAME, teacherIsu = TEACHER_ISU.toLong(),
        room = "2202", building = "ул. Ломоносова, 9", buildingId = 13, mainBuildingId = 13,
        note = "Организационная информация о занятии", zoomUrl = "https://example.invalid/meeting",
        zoomPassword = "1234", zoomInfo = null, flowName = FLOW,
    )

    /** A lab without a teacher ISU, link or note. */
    val plain = LessonDetailsArgs(
        pairId = 3, date = date.toString(), subjectName = "Программирование", typeId = 2, format = "Очный",
        start = "11:40", end = "13:10", teacherFio = TEACHER_NAME, teacherIsu = null, room = "1506",
        building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13, note = null, zoomUrl = null,
        zoomPassword = null, zoomInfo = null, flowName = FLOW,
    )

    /** No teacher, room or link and an unknown subject: the sheet keeps only the time. */
    val minimal = LessonDetailsArgs(
        pairId = 7, date = date.toString(), subjectName = "", typeId = 5, format = "", start = "10:00",
        end = "11:30", teacherFio = null, teacherIsu = null, room = null, building = null, buildingId = null,
        mainBuildingId = null, note = null, zoomUrl = null, zoomPassword = null, zoomInfo = null,
    )

    /** The room of [full] moved today. */
    val roomChange = change(setOf(ScheduleChangeField.PLACE), after = slot(LocalTime(10, 0), "2202", "ул. Ломоносова, 9"))

    /** The time and the room moved at once: two lines. */
    val timeAndRoomChange = change(
        setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE),
        after = slot(LocalTime(10, 0), "2202", "ул. Ломоносова, 9"),
    )

    /** One with a group and a long name, one without a name (the ISU placeholder). */
    val friends = listOf(
        UserSummary(
            isu = FRIEND_ISU, name = "Тестовая подруга Константинопольская-Преображенская", pictureUrl = null,
            groups = listOf(UserGroup("P3212", course = 2, facultyShortName = "ФПИиКТ")),
            sharing = UserSharing(sport = true, schedule = true),
        ),
        UserSummary(isu = 300101, name = "", pictureUrl = null, groups = emptyList(),
            sharing = UserSharing(sport = false, schedule = true)),
    )

    private fun change(fields: Set<ScheduleChangeField>, after: LessonSlot) = ScheduleChange(
        id = "room", detectedAt = Instant.parse("2026-09-07T06:00:00Z"), kind = ScheduleChangeKind.UPDATED,
        fields = fields, subjectName = "Физика", typeId = 3, flowName = "Тестовый поток",
        before = slot(LocalTime(8, 20), "1506", "Кронверкский проспект, 49"), after = after, read = false,
        notified = true,
    )

    private fun slot(start: LocalTime, room: String, building: String) = LessonSlot(
        2, date, start, LocalTime.fromSecondOfDay(start.toSecondOfDay() + LESSON_SECONDS), room, building, 1, "Очный",
        TEACHER_ISU.toLong(), TEACHER_NAME,
    )

    private const val LESSON_SECONDS = 90 * 60
}
