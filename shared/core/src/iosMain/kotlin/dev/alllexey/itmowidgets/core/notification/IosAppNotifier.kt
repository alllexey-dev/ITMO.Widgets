package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.text.resolve
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * The iOS [AppNotifier] (IO-14): local notifications through [center]. The channel becomes the thread identifier
 * (the system groups a channel's notifications), `<channel>-<id>` the request identifier, so a newer notification
 * replaces the shown one as Android's tag and id do. Texts are resolved from the catalog by key (`UiText.resolve`,
 * Russian through the app locale). A silent notification has no sound. iOS has no lock screen variant to set:
 * whether the lock screen shows the text is the user's "Show Previews" setting, so `publicTitle` is not used.
 *
 * [show] does not wait for the post; [awaitPosts] does, so the background runner finishes only after its
 * notifications reached the system.
 */
class IosAppNotifier(
    private val center: LocalNotificationCenter,
    dispatchers: AppDispatchers,
    private val log: AppLog,
) : AppNotifier, SessionDataCleaner {

    private val posts = SupervisorJob()
    private val scope = CoroutineScope(posts + dispatchers.default)

    override fun show(notification: AppNotification) {
        scope.launch { post(notification) }
    }

    /** Posts [notification] now, or at [deliverAt] (a calendar trigger) when given; failures are logged. */
    suspend fun post(notification: AppNotification, deliverAt: Instant? = null) {
        val request = try {
            requestOf(notification, deliverAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.warn(TAG, "Could not resolve the ${notification.channel} notification", error)
            return
        }
        center.add(request)
    }

    /** Waits for every post [show] started so far. */
    suspend fun awaitPosts() {
        posts.children.toList().joinAll()
    }

    override fun cancel(channel: String, id: Int) = center.remove(identifierOf(channel, id))

    override fun clear() = center.removeAll()

    /** Sign-out and every new sign-in drop what the previous session showed or scheduled. */
    override suspend fun clearSessionData() = clear()

    private suspend fun requestOf(notification: AppNotification, deliverAt: Instant?) = LocalNotificationRequest(
        identifier = identifierOf(notification.channel, notification.id),
        threadIdentifier = notification.channel,
        title = notification.title.resolve(),
        body = notification.text.resolve(),
        silent = notification.silent,
        deliverAt = deliverAt,
        userInfo = entryOf(notification.destination),
    )

    companion object {
        /** The request identifier of a channel's notification, as Android's tag and id. */
        fun identifierOf(channel: String, id: Int): String = "$channel-$id"

        /** What a tap opens, in `EntryRouteParser`'s terms; the tap handler comes with the push card (IO-13a). */
        fun entryOf(destination: NotificationDestination): Map<String, Any> = when (destination) {
            NotificationDestination.Sport -> action(AppEntryIntents.ACTION_OPEN_SPORT)
            is NotificationDestination.UserProfile ->
                action(AppEntryIntents.ACTION_OPEN_USER_PROFILE) + (USER_INFO_ISU to destination.isu)
            NotificationDestination.ScheduleChanges -> action(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES)
            NotificationDestination.Recordbook -> action(AppEntryIntents.ACTION_OPEN_RECORDBOOK)
            // The subject's arguments stay on Android until the recordbook reaches iOS (IO-09d2); the tab is one tap
            // away.
            is NotificationDestination.RecordbookSubject -> action(AppEntryIntents.ACTION_OPEN_RECORDBOOK)
            NotificationDestination.BarsLogin -> action(AppEntryIntents.ACTION_OPEN_BARS_LOGIN)
        }

        const val USER_INFO_ACTION = "action"
        const val USER_INFO_ISU = "isu"

        private fun action(value: String): Map<String, Any> = mapOf(USER_INFO_ACTION to value)

        private const val TAG = "AppNotifier"
    }
}
