package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking.QueueKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The booking decision of a free or auto queue push, the same on every platform: Android's FCM handler
 * (`SportSignPushHandler`) and the iOS notification service call it with the payload and show what [book] reports.
 *
 * Each lesson is decoded on its own and skipped when malformed, unnamed, repeated or already ended (by the wall
 * clock), then booked on MyITMO. A booking is reported, marked satisfied on Backend and refreshes the schedule widgets;
 * MyITMO's refusal is reported and cancels the queue entry; a full lesson stays queued; any other failure is retried
 * by Backend later. Afterwards the bookings and the pending queues refresh. The demo session and a missing opt-in
 * book nothing.
 */
class SportSignPushBooker(
    private val actions: SportActionRepository,
    private val api: SportApi,
    private val bookings: SportBookingRepository,
    private val pending: PendingSportBookingsRepository,
    private val widgets: ScheduleWidgetRefreshRequester,
    private val clock: Clock,
    private val diagnostics: AppDiagnostics,
    private val backend: BackendGate,
    private val demo: DemoMode
) {

    /**
     * Books the lessons of [payload], a push of the [queue]'s type, and hands each booking or refusal to [report];
     * a failing [report] never undoes the booking.
     */
    suspend fun book(queue: QueueKind, payload: JsonElement, report: suspend (SportSignNotice) -> Unit) {
        if (demo.isActive() || !backend.mayCallBackend()) return
        val lessons = (payload as? JsonObject)?.get(LESSONS) as? JsonArray ?: return
        val seen = mutableSetOf<Long>()
        for (element in lessons.take(MAX_LESSONS)) {
            safely {
                val lesson = try {
                    decode(queue, element)
                } catch (_: BackendException) {
                    // The decoder's cause may quote the payload, which names people: log only the fact.
                    diagnostics.warn(TAG, "Malformed sport push lesson skipped")
                    return@safely
                }
                // Delayed work must not book an expired or malformed lesson.
                if (lesson.id <= 0 || !seen.add(lesson.id) || lesson.end <= clock.now()) return@safely
                val section = lesson.sectionName.trim().takeIf { it.isNotEmpty() } ?: return@safely
                if (!backend.mayCallBackend()) return@safely
                when (actions.signIn(lesson.id).sportSignOutcome()) {
                    SportSignOutcome.SIGNED_IN -> {
                        // A failed report must never turn a successful booking into a cancellation.
                        safely { report(SportSignNotice(lesson.id, section, lesson.start, booked = true)) }
                        safely { markSatisfied(queue, lesson.id) }
                        safely { widgets.refreshScheduleWidgets() }
                    }
                    SportSignOutcome.REJECTED -> {
                        safely { report(SportSignNotice(lesson.id, section, lesson.start, booked = false)) }
                        safely { cancel(queue, lesson.id) }
                        safely { widgets.refreshScheduleWidgets() }
                    }
                    SportSignOutcome.NO_CAPACITY -> Unit
                    SportSignOutcome.RETRY_LATER ->
                        diagnostics.warn(TAG, "Sport push booking deferred for lesson ${lesson.id}")
                }
            }
        }
        safely { bookings.refreshSportBookings() }
        safely { pending.refresh() }
    }

    /**
     * One lesson at a time, so a malformed lesson costs only itself, as with 1.x. The decoder takes whole payloads;
     * a payload of this one lesson keeps the free and auto `type` decoding in the client.
     */
    private fun decode(queue: QueueKind, lesson: JsonElement): SportLessonDto {
        val single = JsonObject(mapOf(LESSONS to JsonArray(listOf(lesson))))
        val lessons = when (queue) {
            QueueKind.AUTO -> FcmDecoder.sportAutoSignLessons(single).sportLessons
            QueueKind.FREE -> FcmDecoder.sportFreeSignLessons(single).sportLessons
        }
        return lessons.single()
    }

    private suspend fun markSatisfied(queue: QueueKind, id: Long) = when (queue) {
        QueueKind.AUTO -> api.markSportAutoSignEntrySatisfiedByLesson(id)
        QueueKind.FREE -> api.markSportFreeSignEntrySatisfiedByLesson(id)
    }

    private suspend fun cancel(queue: QueueKind, id: Long) = when (queue) {
        QueueKind.AUTO -> api.cancelSportAutoSignEntryByLesson(id)
        QueueKind.FREE -> api.cancelSportFreeSignEntryByLesson(id)
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.warn(TAG, "Sport push operation failed", error)
        }
    }

    private companion object {
        const val TAG = "SportSignPush"
        const val LESSONS = "sportLessons"
        const val MAX_LESSONS = 100
    }
}
