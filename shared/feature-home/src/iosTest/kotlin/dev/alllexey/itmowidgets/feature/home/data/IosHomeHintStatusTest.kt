package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional

class IosHomeHintStatusTest {

    private val dispatchers = AppDispatchers(Dispatchers.Default, Dispatchers.Default, Dispatchers.Default)

    @Test
    fun anyPlacedWidgetOfTheAppEndsTheWidgetHint() = runTest {
        var kinds = emptySet<String>()
        var reads = 0
        val status = IosHomeHintStatus(
            widgets = { completion -> reads++; completion(kinds) },
            notifications = { false },
            dispatchers = dispatchers,
        )

        assertFalse(status.anyWidgetPlaced())
        kinds = setOf("dev.alllexey.itmowidgets.widget.qr")
        assertTrue(status.anyWidgetPlaced())
        assertEquals(2, reads, "every check asks WidgetKit again")
    }

    @Test
    fun widgetKitWithoutAnAnswerReadsAsNoWidget() = runTest {
        val status = IosHomeHintStatus({ }, { false }, dispatchers)

        assertFalse(status.anyWidgetPlaced(), "the hint shows instead of the feed waiting")
    }

    @Test
    fun theNotificationHintFollowsTheAuthorization() = runTest {
        var allowed = false
        val status = IosHomeHintStatus({ it(emptySet()) }, { allowed }, dispatchers)

        assertFalse(status.notificationsEnabled())
        allowed = true
        assertTrue(status.notificationsEnabled())
    }

    @Test
    fun alertsAreAllowedWhenAuthorizedProvisionallyOrForAnAppClip() {
        assertTrue(UserNotificationsAuthorization.allowsAlerts(UNAuthorizationStatusAuthorized))
        assertTrue(UserNotificationsAuthorization.allowsAlerts(UNAuthorizationStatusProvisional))
        assertTrue(UserNotificationsAuthorization.allowsAlerts(UNAuthorizationStatusEphemeral))
        assertFalse(UserNotificationsAuthorization.allowsAlerts(UNAuthorizationStatusDenied))
        assertFalse(UserNotificationsAuthorization.allowsAlerts(UNAuthorizationStatusNotDetermined))
    }
}
