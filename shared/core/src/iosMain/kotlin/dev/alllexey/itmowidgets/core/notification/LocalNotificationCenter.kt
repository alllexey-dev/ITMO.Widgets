package dev.alllexey.itmowidgets.core.notification

import kotlin.time.Instant
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * One local notification as the system takes it: [identifier] replaces a shown or pending one with the same id,
 * [threadIdentifier] groups by channel, [deliverAt] delays it (a calendar trigger) or shows it at once when null.
 * [userInfo] holds only plain values: the Android entry action that `EntryRouteParser` reads and its ISU.
 */
data class LocalNotificationRequest(
    val identifier: String,
    val threadIdentifier: String,
    val title: String,
    val body: String,
    val silent: Boolean,
    val deliverAt: Instant?,
    val userInfo: Map<String, Any>,
)

/** The notification centre the notifier posts to; `UNUserNotificationCenter` in the app, a recording fake in tests. */
interface LocalNotificationCenter {
    fun add(request: LocalNotificationRequest)

    /** Removes the shown and the pending notification of [identifier]. */
    fun remove(identifier: String)

    /** Removes every shown and pending notification of the app. */
    fun removeAll()
}

/**
 * `UNUserNotificationCenter.current()`, read at each call: a test binary has no app bundle, and the centre raises
 * an Objective-C exception without one. Without the user's permission the system drops a request and reports it in
 * the completion, which is logged.
 */
class UserNotificationCenter(private val onAddFailure: (identifier: String, reason: String) -> Unit) :
    LocalNotificationCenter {

    override fun add(request: LocalNotificationRequest) {
        val content = UNMutableNotificationContent().apply {
            setTitle(request.title)
            setBody(request.body)
            setThreadIdentifier(request.threadIdentifier)
            setSound(if (request.silent) null else UNNotificationSound.defaultSound)
            setUserInfo(request.userInfo.mapKeys { it.key as Any? })
        }
        val trigger = request.deliverAt?.let { at ->
            val date = NSDate.dateWithTimeIntervalSince1970(at.epochSeconds.toDouble())
            val components = NSCalendar.currentCalendar.components(CALENDAR_UNITS, fromDate = date)
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        }
        val unRequest = UNNotificationRequest.requestWithIdentifier(request.identifier, content, trigger)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(unRequest) { error ->
            if (error != null) onAddFailure(request.identifier, error.localizedDescription)
        }
    }

    override fun remove(identifier: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
    }

    override fun removeAll() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllDeliveredNotifications()
        center.removeAllPendingNotificationRequests()
    }

    private companion object {
        /** A calendar trigger to the second in the device's calendar and zone: the request's `deliverAt`. */
        val CALENDAR_UNITS = NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
            NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond
    }
}
