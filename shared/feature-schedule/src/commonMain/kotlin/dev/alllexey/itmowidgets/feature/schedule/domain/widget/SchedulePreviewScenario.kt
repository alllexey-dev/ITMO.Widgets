package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** The subjects of the sample day, from the platform's catalog (`widget_preview_*`). */
data class SchedulePreviewLabels(
    val math: String,
    val physics: String,
    val programming: String,
    val english: String,
    val swimming: String,
    val algorithms: String,
    val history: String,
    val teacher: String,
)

/**
 * When a sample is seen: during the third lesson (12:22, the gallery's moment), before the first (08:05), in the gap
 * (13:40) or after the last one (19:40).
 */
enum class SchedulePreviewMoment(val hour: Int, val minute: Int) { LESSON(12, 22), MORNING(8, 5), GAP(13, 40), EVENING(19, 40) }

/**
 * The fixed sample day of the widgets gallery, the launcher previews and the screenshot tests (the design lab's
 * Monday, 7 September): never a user's schedule or the current academic date.
 */
class SchedulePreviewScenario(private val selector: ScheduleWidgetSelector) {

    fun snapshot(
        settings: ScheduleWidgetSettings,
        moment: SchedulePreviewMoment,
        labels: SchedulePreviewLabels,
    ): ScheduleWidgetSnapshot = selector.select(days(labels), instant(moment), ZONE, ScheduleWidgetPreferences(settings)).snapshot

    fun instant(moment: SchedulePreviewMoment): Instant = DATE.atTime(moment.hour, moment.minute).toInstant(ZONE)

    /** The sample from [moment] to the end of the Tuesday, the timeline iOS widgets would read then. */
    fun timeline(
        settings: ScheduleWidgetSettings,
        moment: SchedulePreviewMoment,
        labels: SchedulePreviewLabels,
    ): ScheduleWidgetTimeline = selector.timeline(
        days(labels),
        instant(moment),
        TOMORROW.plus(1, DateTimeUnit.DAY).atStartOfDayIn(ZONE),
        ZONE,
        ScheduleWidgetPreferences(settings),
    )

    fun days(labels: SchedulePreviewLabels): List<LessonDay> = listOf(
        LessonDay(
            DATE,
            listOf(
                lesson(1, "08:20", "09:50", labels.math, LECTURE, "1506", KRONVA),
                lesson(2, "10:00", "11:30", labels.physics, PRACTICE, "2202", LOMO, moved = true),
                lesson(3, "11:40", "13:10", labels.programming, LAB, "1506", KRONVA),
                lesson(4, "15:20", "16:50", labels.english, PRACTICE, null, null, online = true),
                lesson(5, "17:00", "18:30", labels.swimming, SPORT, SWIMMING_POOL, LOMO, sport = SportMark.BOOKED),
            ),
            cachedAt = null,
        ),
        LessonDay(
            TOMORROW,
            listOf(
                lesson(6, "10:00", "11:30", labels.algorithms, LECTURE, "1404", BIRZHA),
                lesson(7, "11:40", "13:10", labels.history, PRACTICE, "311", CHAIKA),
                lesson(8, "15:20", "16:50", labels.swimming, SPORT, SWIMMING_POOL, LOMO, sport = SportMark.QUEUED),
            ),
            cachedAt = null,
        ),
    ).map { day -> day.copy(lessons = day.lessons.map { it.copy(teacher = labels.teacher) }) }

    private fun lesson(
        id: Long,
        start: String,
        end: String,
        subject: String,
        typeId: Int,
        room: String?,
        building: String?,
        online: Boolean = false,
        moved: Boolean = false,
        sport: SportMark? = null,
    ) = DayLesson(
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        subject = subject,
        typeId = typeId,
        teacher = null,
        room = room,
        building = building,
        online = online,
        target = LessonTarget.Lesson(
            LessonDetailsArgs(
                pairId = id, date = DATE.toString(), subjectName = subject, typeId = typeId, format = "", start = start,
                end = end, teacherFio = null, teacherIsu = null, room = room, building = building, buildingId = null,
                mainBuildingId = null, note = null, zoomUrl = null, zoomPassword = null, zoomInfo = null,
            ),
        ),
        change = if (moved) LessonChange(DATE.atTime(9, 0), previousRoom = "2304") else null,
        sport = sport,
    )

    private companion object {
        val DATE: LocalDate = LocalDate(2026, 9, 7)
        val TOMORROW: LocalDate = DATE.plus(1, DateTimeUnit.DAY)
        val ZONE: TimeZone = UtcOffset(hours = 3).asTimeZone()
        const val LECTURE = 1
        const val LAB = 2
        const val PRACTICE = 3
        const val SPORT = 11
        const val KRONVA = "Кронверкский проспект, 49"
        const val LOMO = "улица Ломоносова, 9"
        const val BIRZHA = "Биржевая линия, 14"
        const val CHAIKA = "улица Чайковского, 11"
        const val SWIMMING_POOL = "бассейн"
    }
}
