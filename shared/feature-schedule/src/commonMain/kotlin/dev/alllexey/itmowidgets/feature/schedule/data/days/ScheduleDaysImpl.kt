package dev.alllexey.itmowidgets.feature.schedule.data.days

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.LessonOccurrence
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.schedule.SchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.toLessonDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Schedule days from the cache, for Сегодня ([DayLessonsGateway]) and the schedule feed ([ScheduleDays]). The own
 * schedule: each cached lesson with the newest change found on it, the cancelled lessons of the change history on
 * their old day, and the own sport queues while the queue rows are on. Another user's: their cached lessons, a change
 * mark where the occurrence is one of the viewer's own, and `Вы` on the lessons the viewer's own cache has too. Cache
 * only; [refresh] fetches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleDaysImpl(
    private val repository: ScheduleRepository,
    private val local: ScheduleLocalDataSource,
    private val changes: ScheduleChangesRepository,
    private val pending: PendingSportBookingsRepository,
    private val preferences: SchedulePreferencesRepository,
    private val time: AcademicTimeProvider,
) : ScheduleDays, DayLessonsGateway {

    override fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> = observe(null, from, to)

    override suspend fun refresh(from: LocalDate, to: LocalDate): AppResult<Unit> = refresh(null, from, to)

    override fun observe(userIsu: Int?, from: LocalDate, to: LocalDate): Flow<List<LessonDay>> =
        if (userIsu == null) own(from, to) else other(userIsu, from, to)

    override suspend fun refresh(userIsu: Int?, from: LocalDate, to: LocalDate): AppResult<Unit> {
        if (userIsu != null) return repository.refreshSchedule(userIsu, from, to)
        if (!preferences.observeSportAutoSignEnabled().first()) return repository.refreshSchedule(null, from, to)
        return coroutineScope {
            val queues = async { pending.refresh() }
            val schedule = repository.refreshSchedule(null, from, to)
            queues.await()
            schedule
        }
    }

    private fun own(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> = combine(
        repository.observeScheduleForRange(null, from, to),
        changes.observeChanges(),
        queues(),
    ) { days, found, queued -> Triple(days, found, queued) }.map { (days, found, queued) ->
        val byDate = days.associateBy { it.date }
        val dates = (days.map { it.date } + found.cancelledDates() + queued.map { it.date() })
            .filter { it in from..to }
            .distinct()
            .sorted()
        dates.map { date ->
            val cached = byDate[date]
            val lessons = cached?.lessons.orEmpty().map { it.toDayLesson(date, found) } +
                found.cancelledOn(date, cached?.lessons.orEmpty()) +
                queued.filter { it.date() == date }.map { it.toDayLesson() }
            LessonDay(date, lessons.sortedBy { it.start }, cachedAt(null, date), cached?.week())
        }
    }

    private fun other(userIsu: Int, from: LocalDate, to: LocalDate): Flow<List<LessonDay>> = combine(
        repository.observeScheduleForRange(userIsu, from, to),
        repository.observeScheduleForRange(null, from, to),
        changes.observeChanges(),
    ) { days, own, found -> Triple(days, own, found) }.map { (days, own, found) ->
        val mine = own.flatMap { day -> day.lessons.map { LessonOccurrence(it.pairId, day.date) } }.toSet()
        days.sortedBy { it.date }.map { day ->
            val lessons = day.lessons.map { lesson ->
                lesson.toDayLesson(day.date, found).copy(withYou = LessonOccurrence(lesson.pairId, day.date) in mine)
            }
            LessonDay(day.date, lessons.sortedBy { it.start }, cachedAt(userIsu, day.date), day.week())
        }
    }

    private fun queues(): Flow<List<PendingSportBooking>> = preferences.observeSportAutoSignEnabled().flatMapLatest { on ->
        if (!on) {
            flowOf(emptyList())
        } else {
            pending.observePendingBookings().map { result ->
                result.valueOrNull().orEmpty().distinctBy { it.queueKind to it.queueId }
            }
        }
    }

    private fun cachedAt(userIsu: Int?, date: LocalDate): Instant? =
        local.peekTimestamp(userIsu, date)?.let(Instant::fromEpochMilliseconds)

    private fun DaySchedule.week(): Int? = weekNumber.takeIf { it > 0 }

    private fun Lesson.toDayLesson(date: LocalDate, found: List<ScheduleChange>): DayLesson {
        val occurrence = LessonOccurrence(pairId, date)
        val change = found.filter { occurrence in it.occurrences() }
            .maxByOrNull { it.detectedAt }
        return DayLesson(
            start = start,
            end = end,
            subject = subjectName,
            typeId = kindId,
            teacher = teacherFio,
            room = room?.raw,
            building = building?.raw,
            online = formatId == REMOTE_FORMAT,
            target = LessonTarget.Lesson(toDetailsArgs(date)),
            note = note,
            videoUrl = zoomUrl?.takeIf(String::isNotBlank),
            change = change?.toLessonChange(),
            sport = if (flowTypeId == SPORT_FLOW) SportMark.BOOKED else null,
        )
    }

    private fun ScheduleChange.toLessonChange() = LessonChange(
        detectedAt = detectedAt.toLocalDateTime(time.timeZone),
        previousRoom = before?.room?.takeIf { ScheduleChangeField.PLACE in fields },
        cancelled = kind == ScheduleChangeKind.CANCELLED,
    )

    private fun List<ScheduleChange>.cancelledDates(): List<LocalDate> =
        filter { it.kind == ScheduleChangeKind.CANCELLED }.mapNotNull { it.before?.date }

    /** A cancelled lesson stays on its day struck through, unless the cache still has it (then it is marked there). */
    private fun List<ScheduleChange>.cancelledOn(date: LocalDate, cached: List<Lesson>): List<DayLesson> =
        filter { it.kind == ScheduleChangeKind.CANCELLED && it.before?.date == date }
            .filter { change -> cached.none { it.pairId == change.before?.pairId } }
            .distinctBy { it.before?.pairId }
            .map { change ->
                val slot = checkNotNull(change.before)
                DayLesson(
                    start = slot.start,
                    end = slot.end,
                    subject = change.subjectName,
                    typeId = change.typeId,
                    teacher = slot.teacherName,
                    room = slot.room,
                    building = slot.building,
                    online = slot.formatId == REMOTE_FORMAT,
                    target = LessonTarget.Lesson(change.toLessonDetailsArgs()),
                    change = change.toLessonChange(),
                )
            }

    private fun PendingSportBooking.date(): LocalDate = start.toLocalDateTime(time.timeZone).date

    private fun PendingSportBooking.toDayLesson() = DayLesson(
        start = start.toLocalDateTime(time.timeZone).time,
        end = end.toLocalDateTime(time.timeZone).time,
        subject = sectionName,
        typeId = SPORT_TYPE,
        teacher = teacherFio,
        room = roomName,
        building = null,
        online = false,
        target = LessonTarget.PendingSport(toDetailsArgs(time.timeZone)),
        sport = SportMark.QUEUED,
    )

    private companion object {
        /** MyITMO's `format_id` of a remote lesson. */
        const val REMOTE_FORMAT = 3

        /** MyITMO flow types: 2 academic pairs, 3 sport. */
        const val SPORT_FLOW = 3

        /** MyITMO's lesson type id of sport. */
        const val SPORT_TYPE = 11
    }
}
