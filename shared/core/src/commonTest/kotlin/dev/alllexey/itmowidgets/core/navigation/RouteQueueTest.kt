package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `MainRouteQueueTest`, case for case, plus the saved-state round trip. */
class RouteQueueTest {
    private val qrPass = EntryRoute(AppTab.HOME, overlay = AppRoutes.QrPass, shortcutId = EntryShortcuts.QR_PASS)
    private val today =
        EntryRoute(AppTab.SCHEDULE, request = TabRequest.ScheduleToday, shortcutId = EntryShortcuts.TODAY)

    @Test
    fun aRouteWaitsWhileTheAppIsNotReadyAndTheTabIsNotTouched() {
        val queue = RouteQueue().apply { offer(qrPass) }
        val selected = mutableListOf<AppTab>()

        val taken = queue.take(ready = false) { selected += it; true }

        assertNull(taken)
        assertEquals(qrPass, queue.pending)
        assertEquals(emptyList(), selected)
    }

    @Test
    fun aRouteStaysQueuedWhileItsTabCannotBeSelected() {
        val queue = RouteQueue().apply { offer(today) }

        val taken = queue.take(ready = true) { false }

        assertNull(taken)
        assertEquals(today, queue.pending)
    }

    @Test
    fun aReadyRouteIsHandedOutOnce() {
        val queue = RouteQueue().apply { offer(qrPass) }
        val selected = mutableListOf<AppTab>()

        val first = queue.take(ready = true) { selected += it; true }
        val second = queue.take(ready = true) { selected += it; true }

        assertEquals(qrPass, first)
        assertNull(second)
        assertNull(queue.pending)
        assertEquals(listOf(AppTab.HOME), selected)
    }

    @Test
    fun aNewerRouteReplacesTheWaitingOne() {
        val queue = RouteQueue().apply { offer(qrPass) }

        queue.offer(today)

        assertEquals(today, queue.take(ready = true) { true })
    }

    @Test
    fun aWaitingRouteSurvivesTheSavedState() {
        val json = Json { serializersModule = appRouteSerializersModule() }
        listOf(qrPass, today, EntryRoute(AppTab.HOME, alert = AppRoutes.LinkUnavailable)).forEach { route ->
            val queue = RouteQueue().apply { offer(route) }

            assertEquals(route, json.roundTrip(queue).pending)
        }
        assertNull(json.roundTrip(RouteQueue()).pending)
    }

    private fun Json.roundTrip(queue: RouteQueue): RouteQueue =
        decodeFromString(RouteQueue.serializer(), encodeToString(RouteQueue.serializer(), queue))
}
