package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.IosAppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.headline
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigest
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigests
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.schedule_changes_notification_title
import kotlin.time.Instant

/** A [ScheduleChangeNotifier] that can also hand a digest to the system for a later moment. */
interface MorningScheduleChangeNotifier : ScheduleChangeNotifier {
    /** Posts [digest] for [at]: a change found in the quiet hours is delivered when they end. */
    suspend fun showAt(digest: ScheduleChangeDigest, at: Instant)
}

/**
 * The iOS [ScheduleChangeNotifier] (IO-14), Android's `AndroidScheduleChangeNotifier` over [IosAppNotifier]:
 * "Расписание изменилось: 3 пары" with the nearest change's headline, by catalog key; only today and tomorrow make a
 * sound. One digest at a time: a newer one replaces the shown or the scheduled one.
 */
class IosScheduleChangeNotifier(private val notifier: IosAppNotifier) : MorningScheduleChangeNotifier {

    override fun show(digest: ScheduleChangeDigest) = notifier.show(notificationOf(digest))

    override suspend fun showAt(digest: ScheduleChangeDigest, at: Instant) =
        notifier.post(notificationOf(digest), deliverAt = at)

    private fun notificationOf(digest: ScheduleChangeDigest) = AppNotification(
        channel = AppNotificationChannels.SCHEDULE_CHANGES,
        id = ScheduleChangeDigests.NOTIFICATION_ID,
        title = UiText.Plural(Res.plurals.schedule_changes_notification_title, digest.unread, listOf(digest.unread)),
        text = digest.first.headline(),
        destination = NotificationDestination.ScheduleChanges,
        silent = !digest.audible,
    )
}
