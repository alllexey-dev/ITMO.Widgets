package dev.alllexey.itmowidgets.feature.me.moderation.data.push

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.moderation.ModerationNotice
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.me.moderation.data.toDomain
import dev.alllexey.itmowidgets.feature.me.moderation.domain.ModerationNoticesRepository
import kotlinx.serialization.json.JsonElement

/**
 * Backend's push when a moderation decision on the user's link or review became final: the notice joins the bell at
 * once and, while `Модерация` is on, a notification with the bell's title and text opens the subject's links or the
 * teacher's profile. The lock screen shows only the title; the decision's id keeps two notices apart.
 */
class ModerationNoticePushHandler(
    private val services: CustomServicesRepository,
    private val notices: ModerationNoticesRepository,
    private val diagnostics: AppDiagnostics,
) : FcmPayloadHandler {
    override val type: String = ModerationNotice.TYPE

    override val channel: String = AppNotificationChannels.MODERATION

    override suspend fun handle(payload: JsonElement, notifier: AppNotifier) {
        if (!services.isEnabled()) return
        val notice = try {
            FcmDecoder.moderationNotice(payload).toDomain()
        } catch (_: BackendException) {
            diagnostics.warn(TAG, "Invalid moderation notice push")
            return
        } ?: return
        notices.received(notice)
        val link = notice.link
        val review = notice.review
        val destination = when {
            link != null -> link.args.validOrNull()?.let(NotificationDestination::SubjectLinks)
            review != null -> review.teacherIsu.takeIf { it > 0 }?.let(NotificationDestination::UserProfile)
            else -> null
        } ?: return
        notifier.show(
            AppNotification(
                channel = AppNotificationChannels.MODERATION,
                id = notice.id.hashCode(),
                title = notice.title,
                text = notice.text,
                destination = destination,
                publicTitle = notice.title,
            ),
        )
    }

    private companion object {
        const val TAG = "ModerationNoticePush"
    }
}
