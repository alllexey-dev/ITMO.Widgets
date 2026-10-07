package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The widgets this app has placed, as WidgetKit's current configurations tell the Swift app (`IosPlatform`): [read]
 * hands the completion the placed kinds, empty when WidgetKit cannot tell, once and on the main thread.
 */
fun interface PlacedWidgetKinds {
    fun read(completion: (Set<String>) -> Unit)
}

/** What iOS answers about alerts for this app; [UserNotificationsAuthorization] asks `UNUserNotificationCenter`. */
fun interface NotificationAuthorization {
    suspend fun allowsAlerts(): Boolean
}

/**
 * The device side of the home hints on iOS. iOS lets no app place a widget, so the widget hint stays until the user
 * places any widget of the app (its action is an instruction sheet); the notification hint stays until iOS allows
 * alerts, provisional and ephemeral authorisation included.
 */
class IosHomeHintStatus(
    private val widgets: PlacedWidgetKinds,
    private val notifications: NotificationAuthorization,
    private val dispatchers: AppDispatchers,
) : HomeHintStatus {

    /** WidgetKit can take seconds to answer the first ask after an install; the feed does not wait for it. */
    override suspend fun anyWidgetPlaced(): Boolean = withContext(dispatchers.main) {
        withTimeoutOrNull(WIDGETS_ANSWER_TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                widgets.read { kinds -> if (continuation.isActive) continuation.resume(kinds.isNotEmpty()) }
            }
        } ?: false
    }

    override suspend fun notificationsEnabled(): Boolean = notifications.allowsAlerts()

    internal companion object {
        /** No answer by then reads as no widget: the hint shows, and the next return to the app asks again. */
        val WIDGETS_ANSWER_TIMEOUT = 2.seconds
    }
}

/** `UNUserNotificationCenter` of the app process; a test binary without a bundle has none. */
object UserNotificationsAuthorization : NotificationAuthorization {
    override suspend fun allowsAlerts(): Boolean = suspendCancellableCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val allowed = settings?.let { allowsAlerts(it.authorizationStatus) } ?: false
            if (continuation.isActive) continuation.resume(allowed)
        }
    }

    internal fun allowsAlerts(status: UNAuthorizationStatus): Boolean =
        status == UNAuthorizationStatusAuthorized ||
            status == UNAuthorizationStatusProvisional ||
            status == UNAuthorizationStatusEphemeral
}
