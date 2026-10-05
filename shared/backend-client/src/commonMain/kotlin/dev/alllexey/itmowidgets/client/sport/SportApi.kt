package dev.alllexey.itmowidgets.client.sport

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.sport.model.FriendsSportBookingsResponse
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.client.sport.model.UserSportBookingsResponse
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sport (routes under `/api/sport`): the viewer's confirmed bookings, the friends feed, and the free-sign (`free`,
 * a real lesson) and auto-sign (`auto`, a weekly prototype lesson) queues. Semantics are in Backend's
 * `sport-automation.md` and `privacy.md` contracts. A queue entry is never a confirmed university booking: confirmed
 * lesson IDs come only from [syncSportLessons].
 *
 * Every entry and queue answer is checked against its route: a free-sign route answering an `auto` value (or the
 * reverse), or an unknown `"type"`, is a [BackendException.Contract]. Backend's confirmation texts (`"Entry
 * successfully cancelled"`) are not returned. Mutating another owner's entry is rejected.
 *
 * Not mirrored: `POST /api/sport/free-sign/entry/{id}/mark-satisfied` and its auto-sign twin (by entry ID). The app
 * marks entries satisfied by lesson; Backend keeps the entry-ID routes for released clients.
 */
interface SportApi {

    /**
     * `POST /api/sport/sign/sync` with the bare JSON array [lessonIds]: replaces the viewer's future confirmed
     * bookings and updates both queue types (a booked lesson satisfies its entries). Needs no audience.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun syncSportLessons(lessonIds: List<Long>)

    /**
     * `GET /api/sport/friends/sport-bookings`: confirmed bookings (`entry` null) and waiting or notified entries of
     * accepted friends whose sport audience admits the viewer; an auto entry is listed under its prototype lesson.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun friendsSportBookings(): FriendsSportBookingsResponse

    /**
     * `GET /api/sport/users/{isu}/bookings`: the owner's confirmed current and upcoming lessons and uncancelled
     * waiting or notified entries, authorized by the owner's sport audience (`canViewSport`); self reads always
     * succeed.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun userSportBookings(isu: Int): UserSportBookingsResponse

    /** `GET /api/sport/free-sign/entry/my`: the viewer's free-sign entries. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun mySportFreeSignEntries(): List<SportFreeSignEntry>

    /**
     * `POST /api/sport/free-sign/entry/create`: joins the free-sign queue of a real lesson; repeating it returns
     * the existing waiting or notified entry.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun createSportFreeSignEntry(request: SportFreeSignRequest): SportFreeSignEntry

    /** `POST /api/sport/free-sign/entry/{id}/cancel`; repeating it keeps the first cancellation time. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun cancelSportFreeSignEntry(id: Long)

    /** `POST /api/sport/free-sign/lesson/{lessonId}/cancel`: cancels the viewer's entry for that real lesson. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun cancelSportFreeSignEntryByLesson(lessonId: Long)

    /** `GET /api/sport/free-sign/queue/current`: lesson IDs and queue sizes, never owners. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun currentSportFreeSignQueues(): List<SportFreeSignQueue>

    /** `POST /api/sport/free-sign/lesson/{lessonId}/mark-satisfied`: the viewer got a place on that real lesson. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun markSportFreeSignEntrySatisfiedByLesson(lessonId: Long)

    /** `GET /api/sport/auto-sign/limits`: the auto-sign quota; `nextAvailableAt` is an estimate. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun sportAutoSignLimits(): SportAutoSignLimits

    /** `GET /api/sport/auto-sign/entry/my`: the viewer's auto-sign entries. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun mySportAutoSignEntries(): List<SportAutoSignEntry>

    /**
     * `POST /api/sport/auto-sign/entry/create`: joins the auto-sign queue of a prototype lesson; repeating it
     * returns the existing waiting or notified entry before the quota is checked.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun createSportAutoSignEntry(request: SportAutoSignRequest): SportAutoSignEntry

    /** `POST /api/sport/auto-sign/entry/{id}/cancel`; repeating it keeps the first cancellation time. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun cancelSportAutoSignEntry(id: Long)

    /**
     * `POST /api/sport/auto-sign/lesson/{lessonId}/cancel`: [lessonId] is the real lesson; cancels every auto entry
     * matched to it.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun cancelSportAutoSignEntryByLesson(lessonId: Long)

    /**
     * `POST /api/sport/auto-sign/queue/current`: lesson IDs and queue sizes, never owners. A read that Backend keeps
     * as `POST` for released clients.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun currentSportAutoSignQueues(): List<SportAutoSignQueue>

    /**
     * `POST /api/sport/auto-sign/lesson/{lessonId}/mark-satisfied`: [lessonId] is the real lesson; satisfies every
     * auto entry matched to it.
     */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun markSportAutoSignEntrySatisfiedByLesson(lessonId: Long)
}
