package dev.alllexey.itmowidgets.feature.schedule.ui.changes.preview

import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.RelativeDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangeDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangeRow
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The history LS-0's references show (`ScheduleReferenceFixtures.history()`), grouped as the ViewModel groups it. */
internal object ScheduleChangesPreviewData {

    private const val TEACHER_NAME = "Тестовый преподаватель с очень длинным именем"

    val history: List<ScheduleChangeDay> = listOf(
        ScheduleChangeDay(
            LocalDate(2026, 9, 7), RelativeDay.TODAY,
            listOf(
                ScheduleChangeRow(
                    change("added", ScheduleChangeKind.ADDED, emptySet(), "2026-09-07T08:00:00Z",
                        before = null, after = slot(1, LocalDate(2026, 9, 9), LocalTime(10, 0))),
                    isNew = true,
                ),
                ScheduleChangeRow(
                    change("cancelled", ScheduleChangeKind.CANCELLED, emptySet(), "2026-09-07T06:00:00Z",
                        subject = "Физика", typeId = 3, flowName = "ФИЗ ПИИКТ 3.2",
                        before = slot(2, LocalDate(2026, 9, 8)), after = null),
                    isNew = false,
                ),
            ),
        ),
        ScheduleChangeDay(
            LocalDate(2026, 9, 6), RelativeDay.YESTERDAY,
            listOf(
                ScheduleChangeRow(
                    change("same-day", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TIME),
                        "2026-09-06T12:00:00Z", subject = "Программирование", typeId = 2,
                        before = slot(3, LocalDate(2026, 9, 10)),
                        after = slot(3, LocalDate(2026, 9, 10), LocalTime(10, 0))),
                    isNew = true,
                ),
                ScheduleChangeRow(
                    change("moved", ScheduleChangeKind.UPDATED,
                        setOf(ScheduleChangeField.TIME, ScheduleChangeField.PLACE), "2026-09-06T09:00:00Z",
                        subject = "Физика", typeId = 1,
                        before = slot(4, LocalDate(2026, 9, 8), LocalTime(13, 30)),
                        after = slot(4, LocalDate(2026, 9, 11), LocalTime(15, 20), room = "2202",
                            building = "ул. Ломоносова, 9")),
                    isNew = true,
                ),
            ),
        ),
        ScheduleChangeDay(
            LocalDate(2026, 9, 3), RelativeDay.OTHER,
            listOf(
                ScheduleChangeRow(
                    change("format", ScheduleChangeKind.UPDATED,
                        setOf(ScheduleChangeField.FORMAT, ScheduleChangeField.PLACE), "2026-09-03T10:00:00Z",
                        subject = "Английский язык", typeId = 3, flowName = null,
                        before = slot(5, LocalDate(2026, 9, 9), LocalTime(11, 40)),
                        after = slot(5, LocalDate(2026, 9, 9), LocalTime(11, 40), room = null, building = null,
                            formatId = 3, format = "Дистанционный")),
                    isNew = false,
                ),
                ScheduleChangeRow(
                    change("teacher", ScheduleChangeKind.UPDATED, setOf(ScheduleChangeField.TEACHER),
                        "2026-09-03T09:00:00Z", subject = "Математический анализ", typeId = 3,
                        before = slot(6, LocalDate(2026, 9, 14)),
                        after = slot(6, LocalDate(2026, 9, 14), teacherName = "Новый преподаватель")),
                    isNew = true,
                ),
            ),
        ),
    )

    private fun change(
        id: String,
        kind: ScheduleChangeKind,
        fields: Set<ScheduleChangeField>,
        detectedAt: String,
        subject: String = "Математический анализ",
        typeId: Int = 1,
        flowName: String? = "Тестовый поток",
        before: LessonSlot?,
        after: LessonSlot?,
    ) = ScheduleChange(
        id = id, detectedAt = Instant.parse(detectedAt), kind = kind, fields = fields, subjectName = subject,
        typeId = typeId, flowName = flowName, before = before, after = after, read = true, notified = true,
    )

    private fun slot(
        pairId: Long,
        date: LocalDate,
        start: LocalTime = LocalTime(8, 20),
        room: String? = "1506",
        building: String? = "Кронверкский проспект, 49",
        formatId: Int = 1,
        format: String? = "Очный",
        teacherName: String? = TEACHER_NAME,
    ) = LessonSlot(
        pairId, date, start, LocalTime.fromSecondOfDay(start.toSecondOfDay() + LESSON_SECONDS), room, building,
        formatId, format, teacherIsu = 300001, teacherName = teacherName,
    )

    private const val LESSON_SECONDS = 90 * 60
}
