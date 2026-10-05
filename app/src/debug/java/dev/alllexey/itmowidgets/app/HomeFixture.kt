package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate

/** The whole feed from one in-memory source; a test replaces the cards and watches the same flows. */
data class HomeFixture(
    val cards: List<HomeCard> = defaultCards(),
    val refreshResult: AppResult<Unit> = AppResult.Success(Unit),
    val refreshDelayMs: Long = 0,
    val hidden: Set<HomeCardKind> = emptySet(),
    /** The source never answers: the feed stays on its first-load placeholder. */
    val neverAnswers: Boolean = false
) {
    companion object {
        val DATE: LocalDate = LocalDate.of(2026, 9, 7)

        fun lesson(pairId: Long, start: String, end: String, subject: String, typeId: Int = 1, room: String? = "1506") =
            LessonDetailsArgs(
                pairId = pairId, date = DATE.toString(), subjectName = subject, typeId = typeId, format = "Очный",
                start = start, end = end, teacherFio = DemoPeople.MATH_TEACHER.name, teacherIsu = 300001, room = room,
                building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13, note = null,
                zoomUrl = null, zoomPassword = null, zoomInfo = null
            )

        fun booking(id: Long = 1, hour: Int = 16, prediction: Boolean = false) = PendingSportBooking(
            queueId = id, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100 + id, sectionName = "Плавание",
            start = OffsetDateTime.of(DATE, LocalTime.of(hour, 0), ZoneOffset.ofHours(3)).toInstant().toKotlinInstant(),
            end = OffsetDateTime.of(DATE, LocalTime.of(hour + 1, 30), ZoneOffset.ofHours(3)).toInstant().toKotlinInstant(),
            teacherFio = DemoPeople.SWIMMING_COACH.name, roomName = "Бассейн", isPrediction = prediction
        )

        fun pending(id: Long = 1, hour: Int = 12, prediction: Boolean = false): PendingSportDetailsArgs =
            booking(id, hour, prediction).toDetailsArgs(TimeZone.of("Europe/Moscow"))

        fun user(isu: Int, name: String) = UserSummary(
            isu = isu, name = name, pictureUrl = null,
            groups = listOf(UserGroup("M3100", 1, "ФИТиП")), sharing = UserSharing(sport = true, schedule = true)
        )

        fun schedule() = HomeCard.Schedule(
            date = DATE.toKotlinLocalDate(),
            tomorrow = false,
            rows = listOf(
                HomeScheduleRow.Lesson(lesson(1, "09:30", "11:00", DemoStudy.MATH.name), HomeLessonState.CURRENT, progress = 0.55f),
                HomeScheduleRow.Lesson(lesson(2, "11:20", "12:50", "Дискретная математика и основы алгоритмов", 3), HomeLessonState.UPCOMING),
                HomeScheduleRow.PendingSport(pending(1, 12), predicted = false),
                HomeScheduleRow.Lesson(lesson(3, "13:30", "15:00", "Физика", 2, room = null), HomeLessonState.UPCOMING)
            ),
            completed = 1
        )

        /** A synthetic move of a Tuesday lesson to Wednesday, found this morning. */
        fun scheduleChangeSample(subject: String = DemoStudy.MATH.name) = ScheduleChange(
            id = "fixture-1",
            detectedAt = Instant.parse("2026-09-07T06:00:00Z"),
            kind = ScheduleChangeKind.UPDATED,
            fields = setOf(ScheduleChangeField.TIME),
            subjectName = subject,
            typeId = 1,
            flowName = "МАТ АН 1.1",
            before = changeSlot(DATE.plusDays(1)),
            after = changeSlot(DATE.plusDays(2)),
            read = false,
            notified = true
        )

        private fun changeSlot(date: LocalDate) = LessonSlot(
            pairId = 1, date = date.toKotlinLocalDate(), start = kotlinx.datetime.LocalTime(10, 0),
            end = kotlinx.datetime.LocalTime(11, 30), room = "1506",
            building = "Кронверкский проспект, 49", formatId = 1, format = "Очный", teacherIsu = 300001,
            teacherName = DemoPeople.MATH_TEACHER.name
        )

        /** Three unread subjects of the demo set, newest first. */
        fun marksSample() = HomeCard.Marks(listOf(DemoStudy.DATABASES.name, DemoStudy.DISCRETE.name, DemoStudy.ALGORITHMS.name))

        fun defaultCards() = listOf(
            HomeCard.Hint(HomeHint.WIDGETS),
            HomeCard.ScheduleChanges(unread = 3, latest = scheduleChangeSample()),
            marksSample(),
            HomeCard.FriendRequests(listOf(user(300001, "Александра Константинопольская"), user(300002, "Иван Петров"))),
            HomeCard.Sport(SportScoreSummary(attendances = 50, bonus = 22), listOf(booking(1, 16), booking(2, 18, prediction = true))),
            schedule()
        )
    }
}

class FixtureHomeCardSource(fixture: HomeFixture) : HomeCardSource {
    val cards = MutableStateFlow(fixture.cards)
    var refreshResult = fixture.refreshResult
    var refreshDelayMs = fixture.refreshDelayMs
    var refreshes = 0
    var revalidations = 0
    val dismissed = mutableListOf<HomeCardKind>()
    private val neverAnswers = fixture.neverAnswers

    override fun observe(): Flow<List<HomeCard>> = if (neverAnswers) flow { awaitCancellation() } else cards

    override suspend fun refresh(): AppResult<Unit> {
        refreshes++
        delay(refreshDelayMs)
        return refreshResult
    }

    override suspend fun revalidate() {
        revalidations++
    }

    override suspend fun dismiss(kind: HomeCardKind) {
        dismissed += kind
        cards.value = cards.value.filterNot { it.kind == kind }
    }
}

class FixtureHomeCardPreferences(hidden: Set<HomeCardKind>) : HomeCardPreferences {
    val hidden = MutableStateFlow(hidden)
    override fun observeHidden(): Flow<Set<HomeCardKind>> = hidden
}

class FixtureHomeHintStore : HomeHintStore {
    val dismissed = MutableStateFlow<Set<HomeHint>>(emptySet())
    override fun observeDismissed(): Flow<Set<HomeHint>> = dismissed
    override suspend fun dismiss(hint: HomeHint) {
        dismissed.value = dismissed.value + hint
    }
}
