package dev.alllexey.itmowidgets.feature.social.data.push

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.push.FcmDecoder
import dev.alllexey.itmowidgets.client.push.FriendshipEvent
import dev.alllexey.itmowidgets.client.push.FriendshipEventPayload
import dev.alllexey.itmowidgets.core.diagnostics.AppDiagnostics
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

class FriendshipPushHandler @Inject constructor(
    private val notifier: AppNotifier,
    private val social: SocialRepository,
    private val services: CustomServicesRepository,
    private val diagnostics: AppDiagnostics
) : FcmPayloadHandler {
    override val type: String = FriendshipEventPayload.TYPE

    override suspend fun handle(payload: JsonElement) {
        if (!services.isEnabled()) return
        val event = try {
            FcmDecoder.friendshipEvent(payload)
        } catch (_: BackendException) {
            // An unknown event fails strictly; the decoder's cause may quote the payload, which names people.
            diagnostics.warn(TAG, "Invalid friendship push")
            return
        }
        val actor = event.user
        if (actor.isu <= 0) return
        val name = actor.name.trim().takeIf(String::isNotEmpty) ?: actor.isu.toString()
        safely {
            notifier.show(AppNotification(
                channel = AppNotificationChannels.FRIENDS,
                id = actor.isu,
                title = UiText.Resource(R.string.notification_channel_friends),
                text = UiText.Resource(when (event.event) {
                    FriendshipEvent.REQUEST_RECEIVED -> R.string.notification_friend_request
                    FriendshipEvent.REQUEST_ACCEPTED -> R.string.notification_friend_accepted
                }, listOf(name)),
                destination = NotificationDestination.UserProfile(actor.isu)
            ))
        }
        if (social.currentFriends != null) safely { social.refresh() }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.warn(TAG, "Friendship push operation failed", error)
        }
    }

    companion object { private const val TAG = "FriendshipPush" }
}
