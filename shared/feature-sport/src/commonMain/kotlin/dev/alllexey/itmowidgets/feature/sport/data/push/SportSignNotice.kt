package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.notification_sport_failure
import dev.alllexey.itmowidgets.shared.core.notification_sport_lesson
import dev.alllexey.itmowidgets.shared.core.notification_sport_success
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime

/** A queue push's lesson that MyITMO [booked] or refused: what the user is told about it. */
data class SportSignNotice(
    val lessonId: Long,
    val sectionName: String,
    val start: Instant,
    val booked: Boolean
)

/**
 * The notification of [SportSignNotice] on the `sport` channel, one per lesson, opening the sport tab: "Вы записаны
 * на спорт" or "Не удалось записать на спорт", then the section and the Moscow start, "Плавание, 21 сент., 12:00".
 */
fun SportSignNotice.toAppNotification(): AppNotification = AppNotification(
    channel = AppNotificationChannels.SPORT,
    id = lessonId.hashCode(),
    title = UiText.Res(if (booked) Res.string.notification_sport_success else Res.string.notification_sport_failure),
    text = UiText.Res(
        Res.string.notification_sport_lesson,
        listOf(sectionName, start.toLocalDateTime(MOSCOW).format(DateTexts.DAY_SHORT_MONTH_TIME))
    ),
    destination = NotificationDestination.Sport
)

/** ITMO's time zone, which the lesson times of the notification are in wherever the device is. */
private val MOSCOW = TimeZone.of("Europe/Moscow")
