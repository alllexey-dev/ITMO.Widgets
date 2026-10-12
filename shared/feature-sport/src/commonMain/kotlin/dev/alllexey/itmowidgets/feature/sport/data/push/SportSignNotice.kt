package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmoapi.core.MOSCOW
import dev.alllexey.itmowidgets.client.push.SportAgentBookedPayload
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlin.time.Instant
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
    title = UiText.Res(if (booked) CoreStrings.notification_sport_success else CoreStrings.notification_sport_failure),
    text = UiText.Res(
        CoreStrings.notification_sport_lesson,
        listOf(sectionName, start.toLocalDateTime(MOSCOW).format(DateTexts.DAY_SHORT_MONTH_TIME))
    ),
    destination = NotificationDestination.Sport
)

/**
 * The user's own agent booked a lesson: the same `sport` notification as a booking on the phone, its text naming the
 * agent, "Плавание, 21 сент., 12:00 — записал «nas»". Null for a push without a lesson.
 */
fun SportAgentBookedPayload.toAppNotification(): AppNotification? {
    val lesson = sportLessons.firstOrNull() ?: return null
    return AppNotification(
        channel = AppNotificationChannels.SPORT,
        id = lesson.id.hashCode(),
        title = UiText.Res(CoreStrings.notification_sport_success),
        text = UiText.Res(
            CoreStrings.notification_sport_agent_booked,
            listOf(
                lesson.sectionName.trim(),
                lesson.start.toLocalDateTime(MOSCOW).format(DateTexts.DAY_SHORT_MONTH_TIME),
                agentName.trim(),
            ),
        ),
        destination = NotificationDestination.Sport,
    )
}
