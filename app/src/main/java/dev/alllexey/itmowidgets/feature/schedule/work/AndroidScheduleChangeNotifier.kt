package dev.alllexey.itmowidgets.feature.schedule.work

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.ui.headline
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigest
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigests
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeNotifier
import javax.inject.Inject

/** "Расписание изменилось: 3 пары" with the nearest change; only today and tomorrow make a sound. */
class AndroidScheduleChangeNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifier: AppNotifier
) : ScheduleChangeNotifier {

    override fun show(digest: ScheduleChangeDigest) {
        notifier.show(AppNotification(
            channel = AppNotificationChannels.SCHEDULE_CHANGES,
            id = ScheduleChangeDigests.NOTIFICATION_ID,
            title = UiText.Dynamic(
                context.resources.getQuantityString(R.plurals.schedule_changes_notification_title, digest.unread, digest.unread)
            ),
            text = UiText.Dynamic(digest.first.headline(context)),
            destination = NotificationDestination.ScheduleChanges,
            silent = !digest.audible
        ))
    }
}
