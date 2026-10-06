package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.markSubjectList
import dev.alllexey.itmowidgets.feature.home.homeLessonRow
import dev.alllexey.itmowidgets.feature.home.homeScheduleCard
import dev.alllexey.itmowidgets.shared.core.home_schedule_today
import dev.alllexey.itmowidgets.shared.core.schedule_building_kronva
import dev.alllexey.itmowidgets.shared.core.title_sport
import dev.alllexey.itmowidgets.shared.feature.home.Res
import dev.alllexey.itmowidgets.shared.feature.home.home_pending_predicted
import dev.alllexey.itmowidgets.shared.feature.home.home_pending_waiting
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_completed
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_done
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_empty
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_next
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_now
import dev.alllexey.itmowidgets.shared.feature.home.home_schedule_tomorrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes

class HomeCardFormatterTest {
    private val moscow = TimeZone.of("Europe/Moscow")
    private val formatter = HomeCardFormatter(moscow)

    @Test
    fun aLessonRowCarriesItsTimesTypeRoomBuildingAndBadge() {
        val row = homeLessonRow(pairId = 1, state = HomeLessonState.NEXT)
        val card = formatter.format(homeScheduleCard(row)) as HomeCardUi.Schedule

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
        val card = formatter.format(
            homeScheduleCard(
                homeLessonRow(1, HomeLessonState.CURRENT).copy(progress = 1.4f),
                homeLessonRow(2, HomeLessonState.UPCOMING),
            )
        ) as HomeCardUi.Schedule

        assertEquals(UiText.Res(Res.string.home_schedule_now), card.rows[0].badge)
        assertEquals(1f, card.rows[0].progress)
        assertNull(card.rows[1].badge)
        assertNull(card.rows[1].progress)
    }

    @Test
    fun pendingSportTimesAreWrittenInTheAcademicZoneNotTheOffsetTheyCameIn() {
        // 09:00 UTC is 12:00 in Moscow, whatever the device zone.
        val args = booking(start = "2026-09-07T09:00:00Z", end = "2026-09-07T10:30:00Z").toDetailsArgs(TimeZone.UTC)
        val card = formatter.format(homeScheduleCard(HomeScheduleRow.PendingSport(args, predicted = true))) as HomeCardUi.Schedule

        val row = card.rows.single()
        assertEquals("12:00", row.start)
        assertEquals("13:30", row.end)
        assertEquals(HomeCardFormatter.SPORT_TYPE_ID, row.typeId)
        assertEquals(UiText.Res(Res.string.home_pending_predicted), row.badge)
        assertEquals(UiText.Joined(listOf(UiText.Res(CoreRes.string.title_sport), UiText.Dynamic("Бассейн")), " \u00B7 "), row.subtitle)
        assertEquals(HomeRowTarget.PendingSport(args), row.target)
    }

    @Test
    fun theFooterSaysTheDayIsEmptyOverOrHowManyLessonsPassed() {
        fun footer(rows: Int, completed: Int, tomorrow: Boolean = false): UiText? {
            val schedule = homeScheduleCard(*Array(rows) { homeLessonRow(it.toLong()) })
                .copy(completed = completed, tomorrow = tomorrow)
            return (formatter.format(schedule) as HomeCardUi.Schedule).footer
        }

        assertEquals(UiText.Res(Res.string.home_schedule_empty), footer(rows = 0, completed = 0))
        assertEquals(UiText.Res(Res.string.home_schedule_done), footer(rows = 0, completed = 3))
        assertEquals(UiText.Plural(Res.plurals.home_schedule_completed, 2, listOf(2)), footer(rows = 1, completed = 2))
        assertNull(footer(rows = 1, completed = 0))
        assertNull(footer(rows = 1, completed = 2, tomorrow = true))
        val tomorrow = formatter.format(homeScheduleCard().copy(tomorrow = true)) as HomeCardUi.Schedule
        assertEquals(UiText.Res(Res.string.home_schedule_tomorrow), tomorrow.title)
    }

    @Test
    fun theSportCardShowsThreeQueueRowsAndCountsTheRest() {
        val queue = (1..5).map { booking(id = it.toLong(), start = "2026-09-07T13:00:00Z", end = "2026-09-07T14:30:00Z") }
        val card = formatter.format(HomeCard.Sport(SportScoreSummary(attendances = 50, bonus = 50), queue)) as HomeCardUi.Sport

        assertEquals(HomeSportScoreUi(total = 90, remaining = 10), card.score)
        assertEquals(3, card.queue.size)
        assertEquals(2, card.more)
        assertEquals("пн, 7 сент. \u00B7 16:00\u201317:30", card.queue.first().subtitle)
        assertEquals(UiText.Res(Res.string.home_pending_waiting), card.queue.first().badge)
        assertEquals(queue.first().toDetailsArgs(moscow), card.queue.first().args)

        val noScore = formatter.format(HomeCard.Sport(null, queue.take(1))) as HomeCardUi.Sport
        assertNull(noScore.score)
        assertEquals(0, noScore.more)
    }

    @Test
    fun friendRequestsShowThreePeopleWithTheirPrimaryGroup() {
        val people = (1..4).map { user(300000 + it, "Человек $it") }
        val card = formatter.format(HomeCard.FriendRequests(people)) as HomeCardUi.FriendRequests

        assertEquals(4, card.count)
        assertEquals(listOf(300001, 300002, 300003), card.incoming.map { it.isu })
        assertEquals(HomeFriendUi(300001, "Человек 1", null, "M3100"), card.incoming.first())
    }

    @Test
    fun marksChangesAndHintsKeepTheirKind() {
        val marks = formatter.format(HomeCard.Marks(listOf("А", "Б"))) as HomeCardUi.Marks
        assertEquals(2, marks.count)
        assertEquals(markSubjectList(listOf("А", "Б")), marks.subjects)
        assertEquals(HomeCardUi.Hint(HomeHint.SERVICES), formatter.format(HomeCard.Hint(HomeHint.SERVICES)))
    }

    private fun booking(id: Long = 1, start: String, end: String) = PendingSportBooking(
        queueId = id, queueKind = PendingSportBooking.QueueKind.FREE, lessonId = 100 + id, sectionName = "Плавание",
        start = Instant.parse(start), end = Instant.parse(end), teacherFio = "Тренер", roomName = "Бассейн",
        isPrediction = false,
    )

    private fun user(isu: Int, name: String) = UserSummary(
        isu = isu, name = name, pictureUrl = null,
        groups = listOf(UserGroup("M3100", 1, "ФИТиП")), sharing = UserSharing(sport = true, schedule = true),
    )
}
