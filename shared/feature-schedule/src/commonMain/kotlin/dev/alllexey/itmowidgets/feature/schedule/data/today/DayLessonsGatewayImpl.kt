package dev.alllexey.itmowidgets.feature.schedule.data.today

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
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.toLessonDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Сегодня's days from the own schedule cache: each cached lesson with the newest change found on it, the cancelled
 * lessons of the change history on their old day, and the own sport queues of those days when the queue rows are
 * on. Cache only; [refresh] fetches the range and the queues.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayLessonsGatewayImpl(
    private val repository: ScheduleRepository,
    private val local: ScheduleLocalDataSource,
    private val changes: ScheduleChangesRepository,
    private val pending: PendingSportBookingsRepository,
    private val preferences: SchedulePreferencesRepository,
    private val time: AcademicTimeProvider,
) : DayLessonsGateway {

    override fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> = combine(
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
            val cached = byDate[date]?.lessons.orEmpty()
            val lessons = cached.map { it.toDayLesson(date, found) } +
                found.cancelledOn(date, cached) +
                queued.filter { it.date() == date }.map { it.toDayLesson() }
            LessonDay(date, lessons.sortedBy { it.start }, cachedAt(date))
        }
    }

    override suspend fun refresh(from: LocalDate, to: LocalDate): AppResult<Unit> = coroutineScope {
        val queues = async { pending.refresh() }
        val schedule = repository.refreshSchedule(null, from, to)
        queues.await()
        schedule
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

    private suspend fun cachedAt(date: LocalDate): Instant? =
        local.get(null, date)?.timestamp?.let(Instant::fromEpochMilliseconds)

    private fun Lesson.toDayLesson(date: LocalDate, found: List<ScheduleChange>): DayLesson {
        val occurrence = LessonOccurrence(pairId, date)
        val change = found.filter { occurrence in it.occurrences() }
            .maxByOrNull { it.detectedAt }
        return DayLesson(
            start = start,
            end = end,
            subject = subjectName,
            typeId = typeId.raw,
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
