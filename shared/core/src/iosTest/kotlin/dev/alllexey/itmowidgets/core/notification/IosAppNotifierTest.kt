package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.EntryRouteParser
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import dev.alllexey.itmowidgets.core.text.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** The iOS notifier over a recording centre: identifiers, threads, sound, delivery time and the tap's entry. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosAppNotifierTest {

    private val center = RecordingCenter()

    @Test
    fun aNotificationBecomesOneRequestOnItsChannelsThread() = runTest {
        val notifier = notifier()

        notifier.show(notification(silent = true))
        notifier.awaitPosts()

        val request = center.added.single()
        assertEquals("schedule_changes-1", request.identifier)
        assertEquals(AppNotificationChannels.SCHEDULE_CHANGES, request.threadIdentifier)
        assertEquals("Title", request.title)
        assertEquals("Text", request.body)
        assertEquals(true, request.silent)
        assertNull(request.deliverAt)
        assertEquals(mapOf<String, Any>("action" to AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES), request.userInfo)
    }

    @Test
    fun aDelayedPostCarriesItsInstant() = runTest {
        val at = Instant.parse("2026-10-08T03:00:00Z")

        notifier().post(notification(), deliverAt = at)

        assertEquals(at, center.added.single().deliverAt)
    }

    @Test
    fun cancelRemovesOneIdentifierAndClearEverything() = runTest {
        val notifier = notifier()

        notifier.cancel(AppNotificationChannels.MARKS, 7)
        notifier.clearSessionData()

        assertEquals(listOf("marks-7"), center.removed)
        assertEquals(1, center.removedAll)
    }

    @Test
    fun theTapEntryParsesToAndroidsDestination() {
        val profile = IosAppNotifier.entryOf(NotificationDestination.UserProfile(isu = 123456))
        val route = EntryRouteParser.parse(
            action = profile[IosAppNotifier.USER_INFO_ACTION] as String,
            isu = profile[IosAppNotifier.USER_INFO_ISU] as Int,
        )
        assertEquals(AppRoutes.UserProfile(123456), route?.overlay)

        val changes = IosAppNotifier.entryOf(NotificationDestination.ScheduleChanges)
        assertEquals(
            AppRoutes.ScheduleChanges,
            EntryRouteParser.parse(changes[IosAppNotifier.USER_INFO_ACTION] as String)?.overlay,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.notifier(): IosAppNotifier {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return IosAppNotifier(center, AppDispatchers(dispatcher, dispatcher, dispatcher), RecordingAppLog())
    }

    private fun notification(silent: Boolean = false) = AppNotification(
        channel = AppNotificationChannels.SCHEDULE_CHANGES,
        id = 1,
        title = UiText.Dynamic("Title"),
        text = UiText.Dynamic("Text"),
        destination = NotificationDestination.ScheduleChanges,
        silent = silent,
    )

    private class RecordingCenter : LocalNotificationCenter {
        val added = mutableListOf<LocalNotificationRequest>()
        val removed = mutableListOf<String>()
        var removedAll = 0

        override fun add(request: LocalNotificationRequest) {
            added += request
        }

        override fun remove(identifier: String) {
            removed += identifier
        }

        override fun removeAll() {
            removedAll++
        }
    }
}
