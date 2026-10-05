package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.error.BackendException
import kotlinx.datetime.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * Schedule (routes under `/api/schedule`): the viewer's lesson snapshot, another user's lessons and the friends on
 * a lesson. Semantics are in Backend's `schedule.md` and `privacy.md` contracts. The app reads the schedule from
 * MyITMO itself; Backend only keeps the uploaded snapshot. Dates are ISO (`2026-10-06`) in the query.
 */
interface ScheduleApi {

    /**
     * `POST /api/schedule/lessons/sync`: replaces the viewer's lessons between `from` and `to` inclusive (an empty
     * list clears the range). 400 `invalid_request_data` when `from` is after `to`, a lesson lies outside the range
     * or a `pairId` repeats. Backend's confirmation text is not returned.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun syncLessons(request: LessonSyncRequest)

    /**
     * `GET /api/schedule/lessons/user/{isu}?from=&to=`: the owner's uploaded lessons ordered by date and start. 403
     * `permission_denied` when the owner's schedule audience excludes the viewer (`canViewSchedule`), 404 for an
     * unregistered ISU; self reads always succeed.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun userLessons(isu: Int, from: LocalDate, to: LocalDate): List<LessonDto>

    /**
     * `GET /api/schedule/lessons/{pairId}/friends?date=`: the viewer's accepted friends on that lesson occurrence
     * whose schedule audience admits the viewer, with current groups. A MyITMO pair ID names one occurrence; [date]
     * guards against rows left behind after a pair moved. No match is an empty list.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun friendsOnLesson(pairId: Long, date: LocalDate): List<UserProfile>
}
