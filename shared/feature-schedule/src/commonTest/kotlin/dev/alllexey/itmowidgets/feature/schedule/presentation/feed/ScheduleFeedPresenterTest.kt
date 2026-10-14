package dev.alllexey.itmowidgets.feature.schedule.presentation.feed

import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonState
import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.schedule.feedLesson
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

class ScheduleFeedPresenterTest {

    private val monday = LocalDate(2026, 9, 7)
    private val window = LocalDate(2026, 8, 31)..LocalDate(2026, 9, 27)
    private val now = LocalDateTime(monday, LocalTime(12, 22))
    private fun day(offset: Int) = monday.plus(offset, DateTimeUnit.DAY)

    private fun input(
        days: List<LessonDay>,
        known: Set<LocalDate> = (0..6).map(::day).toSet(),
        visible: LocalDate = monday,
        owner: FeedOwner = FeedOwner.Own,
        loading: Boolean = false,
        error: AppError? = null,
        denied: AppError? = null,
    ) = FeedInput(now, owner, window, days, known, visible, loading, error, denied, refreshing = false, jump = null)

    private fun rows(state: ScheduleFeedUiState) = (state.body as ScheduleFeedBody.Days).rows

    @Test
    fun aDayWithLessonsIsAStickyHeaderOverTheSharedTimeline() {
        val today = LessonDay(monday, listOf(feedLesson("11:40", "13:10"), feedLesson("15:20", "16:50")), null, week = 2)

        val rows = rows(ScheduleFeedPresenter.state(input(listOf(today))))

        val label = rows.first() as FeedRow.WeekLabel
        assertEquals(UiText.Res(ScheduleStrings.schedule_week_even, listOf(2)), label.text)
        val header = rows[1] as FeedRow.DayHeader
        assertEquals("Понедельник, 7 сентября", header.title)
        assertTrue(header.today)
        val lessons = rows[2] as FeedRow.Lessons
        assertTrue(lessons.showNow)
        val states = lessons.items.filterIsInstance<TimelineItem.Lesson>().map { it.lesson.state }
        assertEquals(listOf(LessonState.NOW, LessonState.NEXT), states)
    }

    @Test
    fun aFreeWeekdayIsOneShortRowAndAFreeWeekendOneRow() {
        val rows = rows(ScheduleFeedPresenter.state(input(listOf(LessonDay(monday, listOf(feedLesson("10:00", "11:30")), null)))))

        val free = rows.filterIsInstance<FeedRow.NoLessons>()
        assertEquals((1..4).map(::day), free.map { it.date })
        assertEquals(UiText.Res(ScheduleStrings.schedule_day_free, listOf("Вт, 8 сентября")), free.first().text)
        val weekend = rows.filterIsInstance<FeedRow.Weekend>().single()
        assertEquals(day(5), weekend.date)
        assertEquals(UiText.Res(ScheduleStrings.schedule_weekend, listOf("12–13 сентября")), weekend.text)
    }

    @Test
    fun aDayNoRefreshAnsweredForIsLeftOutUntilItIsKnown() {
        val rows = rows(ScheduleFeedPresenter.state(input(emptyList(), known = setOf(monday))))

        assertEquals(listOf(monday), rows.map { it.date })
    }

    @Test
    fun theStripCountsTheLessonsThatTakePlaceAndMarksAChange() {
        val changed = feedLesson("10:00", "11:30").copy(change = LessonChange(now, previousRoom = "1506"))
        val cancelled = feedLesson("11:40", "13:10").copy(change = LessonChange(now, cancelled = true))
        val state = ScheduleFeedPresenter.state(input(listOf(LessonDay(day(2), listOf(changed, cancelled), null))))

        val wednesday = state.weeks[state.selectedWeek].days[2]
        assertEquals(1, wednesday.lessons)
        assertTrue(wednesday.changed)
        assertEquals("ср", wednesday.weekday)
    }

    @Test
    fun theHeaderNamesTheVisibleMonthAndTodayShowsOffTheCurrentWeek() {
        val onWeek = ScheduleFeedPresenter.state(input(emptyList()))
        assertEquals(UiText.Dynamic("Сентябрь"), onWeek.title)
        assertFalse(onWeek.offWeek)
        assertTrue(onWeek.monthMenu)

        val nextWeek = ScheduleFeedPresenter.state(input(emptyList(), visible = day(7)))
        assertTrue(nextWeek.offWeek)
        assertEquals(2, nextWeek.selectedWeek)
    }

    @Test
    fun aFriendsFeedNamesThemAndOffersNoMonthMenu() {
        val friend = SelectedUser(300002, "Егорова Полина", null, "M3204")

        val state = ScheduleFeedPresenter.state(input(emptyList(), owner = FeedOwner.Friend(friend)))

        assertEquals(UiText.Dynamic("Егорова Полина, M3204"), state.subtitle)
        assertFalse(state.monthMenu)
        assertEquals(friend, state.friend)
        assertTrue(state.canPickFriend)
    }

    @Test
    fun withoutContentTheFeedLoadsFailsOrIsDenied() {
        assertIs<ScheduleFeedBody.Loading>(ScheduleFeedPresenter.state(input(emptyList(), known = emptySet(), loading = true)).body)
        assertEquals(
            ScheduleFeedBody.Failed(AppError.Network),
            ScheduleFeedPresenter.state(input(emptyList(), known = emptySet(), error = AppError.Network)).body,
        )
        val denied = input(listOf(LessonDay(monday, listOf(feedLesson("10:00", "11:30")), null)), denied = AppError.Forbidden)
        assertEquals(ScheduleFeedBody.Failed(AppError.Forbidden), ScheduleFeedPresenter.state(denied).body)
    }

    @Test
    fun aJumpFindsTheRowOfItsDayOrTheNextOne() {
        val rows = ScheduleFeedPresenter.state(input(listOf(LessonDay(day(2), listOf(feedLesson("10:00", "11:30")), null)))).body
            as ScheduleFeedBody.Days

        assertEquals(rows.rows.indexOfFirst { it is FeedRow.DayHeader }, rows.indexOf(day(2)))
        assertEquals(rows.rows.indexOfFirst { it is FeedRow.Weekend }, rows.indexOf(day(6)))
        assertNull(rows.rows.firstOrNull { it.date == day(30) })
    }
}
