package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmowidgets.client.push.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.client.push.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking.QueueKind
import kotlinx.serialization.json.JsonElement

/**
 * Android's handler of one queue's sport push (`FcmMessageWorker`): the shared [SportSignPushBooker] decides and
 * books, this adapter posts each booking or refusal as a `sport` notification.
 */
class SportSignPushHandler(
    private val queue: QueueKind,
    private val booker: SportSignPushBooker,
) : FcmPayloadHandler {
    override val type: String = when (queue) {
        QueueKind.AUTO -> SportAutoSignLessonsPayload.TYPE
        QueueKind.FREE -> SportFreeSignLessonsPayload.TYPE
    }

    override val channel: String = AppNotificationChannels.SPORT

    override suspend fun handle(payload: JsonElement, notifier: AppNotifier) {
        booker.book(queue, payload) { notice -> notifier.show(notice.toAppNotification()) }
    }
}
