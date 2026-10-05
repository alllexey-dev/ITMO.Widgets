package dev.alllexey.itmowidgets.core.text

import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.schedule_building_kronva
import dev.alllexey.itmowidgets.shared.core.schedule_building_lomo
import dev.alllexey.itmowidgets.shared.core.schedule_change_added
import dev.alllexey.itmowidgets.shared.core.schedule_change_cancelled
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_format
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_line
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_place
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_teacher
import dev.alllexey.itmowidgets.shared.core.schedule_change_field_time
import dev.alllexey.itmowidgets.shared.core.schedule_change_format
import dev.alllexey.itmowidgets.shared.core.schedule_change_headline
import dev.alllexey.itmowidgets.shared.core.schedule_change_moved
import dev.alllexey.itmowidgets.shared.core.schedule_change_place
import dev.alllexey.itmowidgets.shared.core.schedule_change_teacher
import dev.alllexey.itmowidgets.shared.core.schedule_change_value_none
import dev.alllexey.itmowidgets.shared.core.schedule_unknown_subject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleChangeTextsTest {

    @Test
    fun anAddedLessonSaysWhenItStarts() {
        val change = scheduleChange(kind = ScheduleChangeKind.ADDED)
        val summary = UiText.Res(Res.string.schedule_change_added, listOf(WED_START))

        assertEquals(summary, change.summary())
        assertEquals(listOf<UiText>(summary), change.detailLines())
        assertEquals(summary, change.listSummary())
        assertTrue(change.listLines().isEmpty())
        assertEquals(headline(SUBJECT, summary), change.headline())
    }

    @Test
    fun aCancelledLessonSaysWhenItWas() {
        val change = scheduleChange(kind = ScheduleChangeKind.CANCELLED)
        val summary = UiText.Res(Res.string.schedule_change_cancelled, listOf(TUE_START))

        assertEquals(summary, change.summary())
        assertEquals(listOf<UiText>(summary), change.detailLines())
        assertEquals(summary, change.listSummary())
        assertTrue(change.listLines().isEmpty())
        assertEquals(headline(SUBJECT, summary), change.headline())
    }

    @Test
    fun aMovedLessonComparesStartsAcrossDays() {
        val change = scheduleChange(fields = setOf(ScheduleChangeField.TIME))
        val line = line(Res.string.schedule_change_field_time, UiText.Dynamic(TUE_START), UiText.Dynamic(WED_START))

        assertEquals(UiText.Res(Res.string.schedule_change_moved, listOf(WED_START)), change.summary())
        assertEquals(listOf<UiText>(line), change.detailLines())
        assertEquals(line, change.listSummary())
        assertTrue(change.listLines().isEmpty())
    }

    @Test
    fun aMoveWithinADayComparesTimeRanges() {
        val change = scheduleChange(
            fields = setOf(ScheduleChangeField.TIME),
            before = slot(1, WED),
            after = slot(1, WED, start = LocalTime(10, 0))
        )

        assertEquals(
            listOf<UiText>(line(Res.string.schedule_change_field_time, UiText.Dynamic("08:20–09:50"), UiText.Dynamic("10:00–11:30"))),
            change.detailLines()
        )
    }

    @Test
    fun aFormatChangeNamesTheNewFormat() {
        val change = scheduleChange(fields = setOf(ScheduleChangeField.FORMAT), after = slot(1, WED, format = " Дистанционный "))

        assertEquals(UiText.Res(Res.string.schedule_change_format, listOf(UiText.Dynamic("Дистанционный"))), change.summary())
        assertEquals(
            listOf<UiText>(line(Res.string.schedule_change_field_format, UiText.Dynamic("Очный"), UiText.Dynamic("Дистанционный"))),
            change.detailLines()
        )
    }

    @Test
    fun aPlaceChangeShortensRoomAndBuilding() {
        val change = scheduleChange(fields = setOf(ScheduleChangeField.PLACE), after = slot(1, WED, room = null, building = "ул. Ломоносова, 9"))
        val oldPlace = UiText.Joined(listOf(UiText.Dynamic("1506"), UiText.Res(Res.string.schedule_building_kronva)), " · ")
        val newPlace = UiText.Joined(listOf(UiText.Res(Res.string.schedule_building_lomo)), " · ")

        assertEquals(UiText.Res(Res.string.schedule_change_place, listOf(newPlace)), change.summary())
        assertEquals(listOf<UiText>(line(Res.string.schedule_change_field_place, oldPlace, newPlace)), change.detailLines())
    }

    @Test
    fun aMissingPlaceOrTeacherReadsAsADash() {
        val place = scheduleChange(fields = setOf(ScheduleChangeField.PLACE), after = slot(1, WED, room = " ", building = null))
        val teacher = scheduleChange(fields = setOf(ScheduleChangeField.TEACHER), after = slot(1, WED, teacherName = null))

        assertEquals(UiText.Res(Res.string.schedule_change_place, listOf(NONE)), place.summary())
        assertEquals(UiText.Res(Res.string.schedule_change_teacher, listOf(NONE)), teacher.summary())
        assertEquals(
            listOf<UiText>(line(Res.string.schedule_change_field_teacher, UiText.Dynamic(TEACHER), NONE)),
            teacher.detailLines()
        )
    }

    @Test
    fun oneChangedFieldShowsOnlyItsLine() {
        // The teacher differs too, but only the place is a changed field.
        val change = scheduleChange(
            fields = setOf(ScheduleChangeField.PLACE),
            after = slot(1, WED, room = "2304", teacherName = "Другой преподаватель")
        )

        val lines = change.detailLines()

        assertEquals(1, lines.size)
        assertEquals(Res.string.schedule_change_field_line, (lines.single() as UiText.Res).resource)
        assertEquals(UiText.Res(Res.string.schedule_change_field_place), (lines.single() as UiText.Res).arguments.first())
        assertEquals(lines.single(), change.listSummary())
        assertTrue(change.listLines().isEmpty())
    }

    @Test
    fun severalChangedFieldsListEachInHeadlineOrder() {
        val change = scheduleChange(
            fields = setOf(ScheduleChangeField.TEACHER, ScheduleChangeField.FORMAT),
            after = slot(1, WED, format = "Дистанционный", teacherName = "Другой преподаватель")
        )
        val lines = listOf<UiText>(
            line(Res.string.schedule_change_field_format, UiText.Dynamic("Очный"), UiText.Dynamic("Дистанционный")),
            line(Res.string.schedule_change_field_teacher, UiText.Dynamic(TEACHER), UiText.Dynamic("Другой преподаватель"))
        )

        assertEquals(UiText.Res(Res.string.schedule_change_format, listOf(UiText.Dynamic("Дистанционный"))), change.summary())
        assertEquals(change.summary(), change.listSummary())
        assertEquals(lines, change.listLines())
        assertEquals(lines, change.detailLines())
    }

    @Test
    fun theHeadlineKeepsALongSubjectAndNamesAMissingOne() {
        val long = "  $LONG_SUBJECT "
        val cancelled = UiText.Res(Res.string.schedule_change_cancelled, listOf(TUE_START))

        assertEquals(headline(LONG_SUBJECT, cancelled), scheduleChange(kind = ScheduleChangeKind.CANCELLED, subject = long).headline())
        assertEquals(
            UiText.Res(
                Res.string.schedule_change_headline,
                listOf(UiText.Res(Res.string.schedule_unknown_subject), UiText.LowercaseFirst(cancelled))
            ),
            scheduleChange(kind = ScheduleChangeKind.CANCELLED, subject = " ").headline()
        )
    }

    private fun headline(subject: String, summary: UiText) =
        UiText.Res(Res.string.schedule_change_headline, listOf(UiText.Dynamic(subject), UiText.LowercaseFirst(summary)))

    private fun line(title: StringResource, from: UiText, to: UiText) =
        UiText.Res(Res.string.schedule_change_field_line, listOf(UiText.Res(title), from, to))

    private companion object {
        val WED = LocalDate(2026, 9, 9)
        const val TUE_START = "вт, 8 сентября, 08:20"
        const val WED_START = "ср, 9 сентября, 08:20"
        const val SUBJECT = "Математический анализ"
        const val TEACHER = "Тестовый преподаватель"
        const val LONG_SUBJECT = "Проектирование и разработка распределённых информационных систем реального времени"
        val NONE = UiText.Res(Res.string.schedule_change_value_none)
    }
}
