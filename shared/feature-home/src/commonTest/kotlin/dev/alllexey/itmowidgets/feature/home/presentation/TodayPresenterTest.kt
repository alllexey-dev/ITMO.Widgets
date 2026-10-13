package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonState
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.home.dayLesson
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.home.HomeStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class TodayPresenterTest {

    private val monday = LocalDate(2026, 9, 7)
    private val tuesday = LocalDate(2026, 9, 8)
    private val zone = TimeZone.of("Europe/Moscow")

    private val today = listOf(
        dayLesson("08:20", "09:50", "Физика"),
        dayLesson("10:00", "11:30", "Базы данных"),
        dayLesson("11:40", "13:10", "Матанализ"),
        dayLesson("15:20", "16:50", "Английский", room = null, online = true),
    )
    private val tomorrow = listOf(dayLesson("10:00", "11:30", "Алгоритмы"), dayLesson("11:40", "13:10", "История"))
    private val days = listOf(LessonDay(monday, today, null), LessonDay(tuesday, tomorrow, null))

    @Test
    fun duringALessonTheHeroIsThatLessonWithTheTimeLeft() {
        val state = content("12:22")
        val hero = state.hero!!

        assertEquals(UiText.Res(HomeStrings.today_hero_now), hero.label)
        assertEquals(UiText.Res(HomeStrings.today_left, listOf(minutes(48))), hero.countdown)
        assertEquals("Матанализ", hero.subject)
        assertEquals("11:40–13:10", hero.time)
        assertEquals(42f / 90f, hero.progress!!, 0.001f)
        assertEquals(UiText.Res(HomeStrings.today_day_title), state.day!!.title)
        assertTrue(state.day!!.showNow)
    }

    @Test
    fun beforeTheFirstLessonTheHeroIsTheFirstLessonAndAfterOneTheNextOne() {
        val morning = content("08:05").hero!!
        assertEquals(UiText.Res(HomeStrings.today_hero_first), morning.label)
        assertEquals(UiText.Res(HomeStrings.today_in, listOf(minutes(15))), morning.countdown)

        val gap = content("13:40").hero!!
        assertEquals(UiText.Res(HomeStrings.today_hero_next), gap.label)
        assertEquals(UiText.Res(HomeStrings.today_in, listOf(UiText.Res(CoreStrings.duration_hours_minutes, listOf(1, 40)))), gap.countdown)
        assertTrue(gap.online)
        assertEquals("https://example.org/meet", gap.videoUrl)
        assertNull(gap.room)
    }

    @Test
    fun inTheEveningTomorrowLeadsWithItsFirstLessonAsNext() {
        val state = content("19:40")

        assertEquals(UiText.Res(HomeStrings.today_hero_tomorrow), state.hero!!.label)
        assertEquals(UiText.Res(HomeStrings.today_at, listOf("10:00")), state.hero!!.countdown)
        assertEquals(UiText.Res(HomeStrings.today_tomorrow_title), state.day!!.title)
        assertEquals(false, state.day!!.showNow)
        assertEquals(LessonState.NEXT, (state.day!!.items.first() as TimelineItem.Lesson).lesson.state)
    }

    @Test
    fun cancelledLessonsAndSportQueuesNeverLead() {
        val lessons = listOf(
            dayLesson("10:00", "11:30", "Отменённая", change = LessonChange(LocalDateTime(2026, 9, 6, 20, 0), cancelled = true)),
            dayLesson("11:40", "13:10", "Очередь", sport = SportMark.QUEUED),
            dayLesson("13:30", "15:00", "Настоящая"),
        )
        val state = TodayPresenter.state(at("09:00"), listOf(LessonDay(monday, lessons, null)), emptyList(), TodayRefresh.Done, false, zone)

        assertEquals("Настоящая", (state as TodayUiState.Content).hero!!.subject)
        assertEquals(UiText.Plural(dev.alllexey.itmowidgets.shared.core.CorePlurals.schedule_lesson_count, 1, listOf(1)), state.day!!.count)
    }

    @Test
    fun withoutCacheTheFirstRefreshDecidesBetweenLoadingErrorAndNoLessons() {
        val pending = TodayPresenter.state(at("09:00"), emptyList(), emptyList(), TodayRefresh.Pending, false, zone)
        val failed = TodayPresenter.state(at("09:00"), emptyList(), emptyList(), TodayRefresh.Failed(AppError.Network), true, zone)
        val done = TodayPresenter.state(at("09:00"), emptyList(), emptyList(), TodayRefresh.Done, false, zone)

        assertIs<TodayUiState.Loading>(pending)
        assertEquals(TodayUiState.Failed(AppError.Network, retrying = true), failed)
        assertEquals(TodayUiState.Content(hero = null, day = null, todos = emptyList()), done)
    }

    @Test
    fun aFailedRefreshOverTheCacheSaysWhenTheDataIsFrom() {
        val cached = listOf(LessonDay(monday, today, Instant.parse("2026-09-07T08:44:00Z")))
        val state = TodayPresenter.state(at("12:22"), cached, emptyList(), TodayRefresh.Failed(AppError.Network), false, zone)

        assertEquals(UiText.Res(HomeStrings.today_stale, listOf("11:44")), (state as TodayUiState.Content).stale)
    }

    private fun at(time: String) = LocalDateTime(monday, LocalTime.parse(time))

    private fun content(time: String) =
        TodayPresenter.state(at(time), days, emptyList(), TodayRefresh.Done, false, zone) as TodayUiState.Content

    private fun minutes(count: Int) = UiText.Res(CoreStrings.duration_minutes, listOf(count))
}
