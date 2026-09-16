package dev.alllexey.itmowidgets.feature.sport.data.push

import com.google.gson.Gson
import com.google.gson.JsonElement
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.SportLessonDto
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.schedule.ScheduleWidgetRefreshRequester
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class SportSignPushHandler(
    private val auto: Boolean,
    private val gson: Gson,
    private val actions: SportActionRepository,
    private val api: ItmoWidgetsApi,
    private val bookings: SportBookingRepository,
    private val pending: PendingSportBookingsRepository,
    private val widgets: ScheduleWidgetRefreshRequester,
    private val notifier: AppNotifier,
    private val clock: Clock,
    private val diagnostics: AppDiagnostics
) : FcmPayloadHandler {
    override val type = if (auto) SportAutoSignLessonsPayload.TYPE else SportFreeSignLessonsPayload.TYPE

    override suspend fun handle(payload: JsonElement) {
        if (!actions.areCommunityServicesEnabled()) return
        val lessons = payload.asJsonObject["sportLessons"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        val seen = mutableSetOf<Long>()
        for (element in lessons.take(100)) {
            safely {
                val lesson = gson.fromJson(element, SportLessonDto::class.java) ?: return@safely
                // Delayed work must not book an expired or malformed lesson.
                if (lesson.id <= 0 || !seen.add(lesson.id) || lesson.end.toInstant() <= clock.instant()) return@safely
                val section = lesson.sectionName.trim().takeIf { it.isNotEmpty() } ?: return@safely
                if (!actions.areCommunityServicesEnabled()) return@safely
                when (actions.signIn(lesson.id).sportSignOutcome()) {
                    SportSignOutcome.SIGNED_IN -> {
                        // Notification failures must never convert a successful booking into cancellation.
                        safely { notify(lesson, section, true) }
                        safely { markSatisfied(lesson.id).requireSuccess() }
                        safely { widgets.refreshScheduleWidgets() }
                    }
                    SportSignOutcome.REJECTED -> {
                        safely { notify(lesson, section, false) }
                        safely { cancel(lesson.id).requireSuccess() }
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

    private suspend fun markSatisfied(id: Long): ApiResponse<String> =
        if (auto) api.markSportAutoSignEntrySatisfiedByLesson(id) else api.markSportFreeSignEntrySatisfiedByLesson(id)

    private suspend fun cancel(id: Long): ApiResponse<String> =
        if (auto) api.cancelSportAutoSignEntryByLesson(id) else api.cancelSportFreeSignEntryByLesson(id)

    private fun notify(lesson: SportLessonDto, section: String, success: Boolean) {
        notifier.show(AppNotification(
            channel = AppNotificationChannels.SPORT,
            id = lesson.id.hashCode(),
            title = UiText.Resource(if (success) R.string.notification_sport_success else R.string.notification_sport_failure),
            text = UiText.Resource(R.string.notification_sport_lesson, listOf(section, formatter.format(lesson.start))),
            destination = NotificationDestination.Sport
        ))
    }

    private fun ApiResponse<*>.requireSuccess() { check(success) { "Sport queue update rejected" } }

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
        private val gson: Gson,
        private val actions: SportActionRepository,
        private val api: ItmoWidgetsApi,
        private val bookings: SportBookingRepository,
        private val pending: PendingSportBookingsRepository,
        private val widgets: ScheduleWidgetRefreshRequester,
        private val notifier: AppNotifier,
        @param:WallClock private val clock: Clock,
        private val diagnostics: AppDiagnostics
    ) {
        fun create(auto: Boolean): FcmPayloadHandler =
            SportSignPushHandler(auto, gson, actions, api, bookings, pending, widgets, notifier, clock, diagnostics)
    }

    companion object {
        private const val TAG = "SportSignPush"
        private val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
            .withZone(ZoneId.of("Europe/Moscow"))
    }
}
