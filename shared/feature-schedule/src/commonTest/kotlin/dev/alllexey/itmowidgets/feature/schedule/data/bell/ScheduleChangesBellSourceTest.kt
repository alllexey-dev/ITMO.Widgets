package dev.alllexey.itmowidgets.feature.schedule.data.bell

import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.toLessonDetailsArgs
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class ScheduleChangesBellSourceTest {

    @Test
    fun aMoveSaysWhatHappenedWithTheOldSlotOverTheNewAndLeadsToTheLesson() = runTest {
        val change = scheduleChange()
        val item = ScheduleChangesBellSource(FakeScheduleChangesRepository(change)).observe().first().single()

        assertEquals("change:1", item.id)
        assertEquals(BellKind.SCHEDULE_CHANGE, item.kind)
        assertEquals(UiText.Res(ScheduleStrings.schedule_bell_change_moved, listOf(UiText.Dynamic("Математический анализ"))), item.title)
        assertEquals(slotLine("вт, 8 сент."), item.was)
        assertEquals(slotLine("ср, 9 сент."), item.now)
        assertEquals(EntryRoute(AppTab.HOME, overlay = AppRoutes.LessonDetails(change.toLessonDetailsArgs())), item.target)
    }

    @Test
    fun aCancelledLessonHasOnlyItsOldSlotAndATeacherChangeOnlyTheNames() = runTest {
        val cancelled = scheduleChange(id = "2", kind = ScheduleChangeKind.CANCELLED)
        val teacher = scheduleChange(
            id = "3",
            fields = setOf(ScheduleChangeField.TEACHER),
            before = slot(1, LocalDate(2026, 9, 8), teacherName = "Старый Преподаватель"),
            after = slot(1, LocalDate(2026, 9, 8), teacherName = "Новый Преподаватель"),
        )
        val items = ScheduleChangesBellSource(FakeScheduleChangesRepository(cancelled, teacher)).observe().first()

        assertEquals(UiText.Res(ScheduleStrings.schedule_bell_change_cancelled, listOf(UiText.Dynamic("Математический анализ"))), items[0].title)
        assertNull(items[0].now)
        assertEquals(UiText.Res(ScheduleStrings.schedule_bell_change_updated, listOf(UiText.Dynamic("Математический анализ"))), items[1].title)
        assertEquals(UiText.Dynamic("Старый Преподаватель"), items[1].was)
        assertEquals(UiText.Dynamic("Новый Преподаватель"), items[1].now)
    }

    @Test
    fun openingTheBellReadsTheChanges() = runTest {
        val repository = FakeScheduleChangesRepository(scheduleChange())

        ScheduleChangesBellSource(repository).markAllRead()

        assertEquals(1, repository.markAllReadCalls)
    }

    private fun slotLine(day: String) = UiText.Joined(
        listOf(
            UiText.Dynamic(day),
            UiText.Dynamic("08:20"),
            UiText.Dynamic("1506"),
            UiText.Res(CoreStrings.schedule_building_kronva),
        ),
        ", ",
    )
}
