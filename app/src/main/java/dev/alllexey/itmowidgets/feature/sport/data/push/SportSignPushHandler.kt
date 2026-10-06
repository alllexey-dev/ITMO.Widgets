package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.client.push.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.client.push.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.client.sport.SportApi
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.toJavaInstant

class SportSignPushHandler(
    private val auto: Boolean,
    private val actions: SportActionRepository,
    private val api: SportApi,
    private val bookings: SportBookingRepository,
    private val pending: PendingSportBookingsRepository,
    private val widgets: ScheduleWidgetRefreshRequester,
    private val notifier: AppNotifier,
    private val clock: Clock,
    private val diagnostics: AppDiagnostics,
    private val backend: BackendGate,
    private val demo: DemoMode
) : FcmPayloadHandler {
    override val type = if (auto) SportAutoSignLessonsPayload.TYPE else SportFreeSignLessonsPayload.TYPE

    override suspend fun handle(payload: JsonElement) {
        if (demo.isActive() || !backend.mayCallBackend()) return
        val lessons = (payload as? JsonObject)?.get(LESSONS) as? JsonArray ?: return
        val seen = mutableSetOf<Long>()
        for (element in lessons.take(100)) {
            safely {
                val lesson = try {
                    decode(element)
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
                        // Notification failures must never convert a successful booking into cancellation.
                        safely { notify(lesson, section, true) }
                        safely { markSatisfied(lesson.id) }
                        safely { widgets.refreshScheduleWidgets() }
                    }
                    SportSignOutcome.REJECTED -> {
                        safely { notify(lesson, section, false) }
                        safely { cancel(lesson.id) }
                        safely { widgets.refreshScheduleWidgets() }
                    }
                    SportSignOutcome.NO_CAPACITY -> Unit
                    SportSignOutcome.RETRY_LATER -> diagnostics.warn(TAG, "Sport push booking deferred for lesson ${lesson.id}")
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
    private fun decode(lesson: JsonElement): SportLessonDto {
        val single = JsonObject(mapOf(LESSONS to JsonArray(listOf(lesson))))
        val lessons = if (auto) FcmDecoder.sportAutoSignLessons(single).sportLessons
            else FcmDecoder.sportFreeSignLessons(single).sportLessons
        return lessons.single()
    }

    private suspend fun markSatisfied(id: Long) =
        if (auto) api.markSportAutoSignEntrySatisfiedByLesson(id) else api.markSportFreeSignEntrySatisfiedByLesson(id)

    private suspend fun cancel(id: Long) =
        if (auto) api.cancelSportAutoSignEntryByLesson(id) else api.cancelSportFreeSignEntryByLesson(id)

    private fun notify(lesson: SportLessonDto, section: String, success: Boolean) {
        notifier.show(AppNotification(
            channel = AppNotificationChannels.SPORT,
            id = lesson.id.hashCode(),
            title = UiText.Resource(if (success) R.string.notification_sport_success else R.string.notification_sport_failure),
            text = UiText.Resource(R.string.notification_sport_lesson, listOf(section, formatter.format(lesson.start.toJavaInstant()))),
            destination = NotificationDestination.Sport
        ))
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

    class Factory @Inject constructor(
        private val actions: SportActionRepository,
        private val api: SportApi,
        private val bookings: SportBookingRepository,
        private val pending: PendingSportBookingsRepository,
        private val widgets: ScheduleWidgetRefreshRequester,
        private val notifier: AppNotifier,
        private val clock: Clock,
        private val diagnostics: AppDiagnostics,
        private val backend: BackendGate,
        private val demo: DemoMode
    ) {
        fun create(auto: Boolean): FcmPayloadHandler =
            SportSignPushHandler(auto, actions, api, bookings, pending, widgets, notifier, clock, diagnostics, backend, demo)
    }

    companion object {
        private const val TAG = "SportSignPush"
        private const val LESSONS = "sportLessons"
        private val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
            .withZone(ZoneId.of("Europe/Moscow"))
    }
}
