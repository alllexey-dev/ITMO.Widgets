package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.PaymentsCheckPreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.QuietHours
import dev.alllexey.itmowidgets.core.work.outcomeOf
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

/**
 * One background run of `Общежитие и оплата` over every feature's [parts]: ask my.itmo for each part
 * [PaymentsCadence] finds due, recording the attempt (the parts store their answers and write their bell events),
 * then post what waits from the stored answers, even when a request failed, so reminders work offline. Each notice
 * is handled once ([PaymentsNoticeLog]): posted, or dropped with the switch off, so turning it back on posts only what
 * comes after. In the quiet hours a notice goes to the system for 06:00 where the platform can, otherwise it waits
 * for the first run after them. The demo sends nothing and posts nothing. Nothing is logged: the notices carry
 * amounts.
 */
class PaymentsCheck(
    private val parts: List<PaymentsCheckPart>,
    private val preferences: PaymentsCheckPreferences,
    private val log: PaymentsNoticeLog,
    private val notifier: PaymentsNotifier,
    private val sessionTokens: SessionTokenStore,
    private val demo: DemoMode,
    private val clock: Clock,
    private val time: AcademicTimeProvider,
) {
    suspend fun run(): CheckOutcome {
        if (demo.isActive() || !sessionTokens.hasRefreshToken()) return CheckOutcome.SKIPPED
        val now = clock.now()
        val attempts = log.lastAttempts()
        val due = parts.filter { PaymentsCadence.isDue(it.stored(), attempts[it.feature], now) }
        log.recordAttempts(due.map { it.feature }, now)
        val errors = due.mapNotNull { (it.refresh() as? AppResult.Failure)?.error }
        deliver()
        return if (due.isEmpty()) CheckOutcome.SKIPPED else outcomeOf(errors)
    }

    private suspend fun deliver() {
        val now = time.localNow()
        val sent = log.sentKeys()
        val waiting = parts.flatMap { part ->
            part.stored()?.let { it.notices + PaymentReminders.of(it.contracts, now.date, part.feature, part.destination) }.orEmpty()
        }.filter { it.key !in sent }.distinctBy { it.key }
        // With the switch off a notice is handled unposted, so turning it back on brings no stale backlog.
        if (!preferences.getNotificationsEnabled()) return log.record(waiting.map { it.key })
        val morning = if (QuietHours.isQuiet(now.time)) LocalDateTime(now.date, QuietHours.UNTIL).toInstant(time.timeZone) else null
        val posted = waiting.filter { notice ->
            val notification = notice.toNotification()
            if (morning == null) {
                notifier.show(notification)
                true
            } else {
                notifier.showAt(notification, morning)
            }
        }
        log.record(posted.map { it.key })
    }

    private fun PaymentsNotice.toNotification() = AppNotification(
        channel = AppNotificationChannels.DORMITORY_PAYMENTS,
        id = slot.hashCode(),
        title = title,
        text = text,
        destination = destination,
        publicTitle = publicTitle,
    )
}
