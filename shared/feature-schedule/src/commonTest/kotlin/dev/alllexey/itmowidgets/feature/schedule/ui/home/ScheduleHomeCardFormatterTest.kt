package dev.alllexey.itmowidgets.feature.schedule.ui.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.headline
import dev.alllexey.itmowidgets.shared.core.home_pending_predicted
import dev.alllexey.itmowidgets.shared.core.home_schedule_today
import dev.alllexey.itmowidgets.shared.core.schedule_building_kronva
import dev.alllexey.itmowidgets.shared.core.title_sport
import dev.alllexey.itmowidgets.shared.feature.schedule.Res
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_completed
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_done
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_empty
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_next
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_now
import dev.alllexey.itmowidgets.shared.feature.schedule.home_schedule_tomorrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

class ScheduleHomeCardFormatterTest {
    private val moscow = TimeZone.of("Europe/Moscow")
    private val formatter = ScheduleHomeCardFormatter(moscow)

    @Test
    fun aLessonRowCarriesItsTimesTypeRoomBuildingAndBadge() {
        val row = lessonRow(pairId = 1, state = HomeLessonState.NEXT)
        val card = formatter.schedule(scheduleCard(row))

        assertEquals(UiText.Res(CoreRes.string.home_schedule_today), card.title)
        assertEquals("понедельник, 7 сентября", card.date)
        val formatted = card.rows.single()
        assertEquals("09:30", formatted.start)
        assertEquals("11:00", formatted.end)
        assertEquals("Предмет 1", formatted.title)
        assertEquals(
            UiText.Joined(
                listOf(UiText.Res(lessonTypeName(1)), UiText.Dynamic("1506"), UiText.Res(CoreRes.string.schedule_building_kronva)),
                " \u00B7 ",
            ),
            formatted.subtitle,
        )
        assertEquals(UiText.Res(Res.string.home_schedule_next), formatted.badge)
        assertNull(formatted.progress)
        assertEquals(HomeRowTarget.Lesson(row.args), formatted.target)
    }

    @Test
    fun theCurrentLessonIsFocusedAndUpcomingOnesHaveNoBadge() {
        val card = formatter.schedule(
            scheduleCard(
                lessonRow(1, HomeLessonState.CURRENT).copy(progress = 1.4f),
                lessonRow(2, HomeLessonState.UPCOMING),
            )
        )

        assertEquals(UiText.Res(Res.string.home_schedule_now), card.rows[0].badge)
        assertEquals(1f, card.rows[0].progress)
        assertNull(card.rows[1].badge)
        assertNull(card.rows[1].progress)
    }

    @Test
    fun pendingSportTimesAreWrittenInTheAcademicZoneNotTheOffsetTheyCameIn() {
        // 09:00 UTC is 12:00 in Moscow, whatever the device zone.
        val args = PendingSportBooking(
            queueId = 1, queueKind = PendingSportBooking.QueueKind.FREE, lessonId = 101, sectionName = "Плавание",
            start = Instant.parse("2026-09-07T09:00:00Z"), end = Instant.parse("2026-09-07T10:30:00Z"),
            teacherFio = "Тренер", roomName = "Бассейн", isPrediction = false,
        ).toDetailsArgs(TimeZone.UTC)
        val card = formatter.schedule(scheduleCard(HomeScheduleRow.PendingSport(args, predicted = true)))

        val row = card.rows.single()
        assertEquals("12:00", row.start)
        assertEquals("13:30", row.end)
        assertEquals(ScheduleHomeCardFormatter.SPORT_TYPE_ID, row.typeId)
        assertEquals(UiText.Res(CoreRes.string.home_pending_predicted), row.badge)
        assertEquals(UiText.Joined(listOf(UiText.Res(CoreRes.string.title_sport), UiText.Dynamic("Бассейн")), " \u00B7 "), row.subtitle)
        assertEquals(HomeRowTarget.PendingSport(args), row.target)
    }

    @Test
    fun theFooterSaysTheDayIsEmptyOverOrHowManyLessonsPassed() {
        fun footer(rows: Int, completed: Int, tomorrow: Boolean = false): UiText? {
            val schedule = scheduleCard(*Array(rows) { lessonRow(it.toLong()) }).copy(completed = completed, tomorrow = tomorrow)
            return formatter.schedule(schedule).footer
        }

        assertEquals(UiText.Res(Res.string.home_schedule_empty), footer(rows = 0, completed = 0))
        assertEquals(UiText.Res(Res.string.home_schedule_done), footer(rows = 0, completed = 3))
        assertEquals(UiText.Plural(Res.plurals.home_schedule_completed, 2, listOf(2)), footer(rows = 1, completed = 2))
        assertNull(footer(rows = 1, completed = 0))
        assertNull(footer(rows = 1, completed = 2, tomorrow = true))
        val tomorrow = formatter.schedule(scheduleCard().copy(tomorrow = true))
        assertEquals(UiText.Res(Res.string.home_schedule_tomorrow), tomorrow.title)
    }

    @Test
    fun theChangesCardCountsTheUnreadAndNamesTheLatest() {
        val latest = scheduleChange()

        val card = formatter.changes(HomeCard.ScheduleChanges(unread = 4, latest = latest))

        assertEquals(HomeScheduleChangesCardUi(unread = 4, latest = latest.headline()), card)
    }

    private fun lessonRow(pairId: Long = 1, state: HomeLessonState = HomeLessonState.NEXT) = HomeScheduleRow.Lesson(
        LessonDetailsArgs(
            pairId = pairId, date = "2026-09-07", subjectName = "Предмет $pairId", typeId = 1, format = "Очный",
            start = "09:30", end = "11:00", teacherFio = "Преподаватель", teacherIsu = 300001, room = "1506",
            building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13, note = null,
            zoomUrl = null, zoomPassword = null, zoomInfo = null
        ),
        state
    )

    private fun scheduleCard(vararg rows: HomeScheduleRow = arrayOf(lessonRow())) =
        HomeCard.Schedule(LocalDate(2026, 9, 7), tomorrow = false, rows.toList(), completed = 0)
}
