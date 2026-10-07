package dev.alllexey.itmowidgets.feature.schedule.data.changes

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.QuietHours
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangeDigests
import dev.alllexey.itmowidgets.feature.schedule.domain.changes.ScheduleChangesRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

/**
 * The schedule change check as one step of the iOS background runner (IO-14): LT-1's [ScheduleChangesCheck], then
 * the quiet hours. Android's check keeps a night change until its first run after 06:00, which WorkManager makes
 * within two hours; iOS may not wake the app for many hours, so a change found between 00:00 and 06:00 (Moscow, the
 * academic zone) is handed to the system for 06:00 at once (a calendar trigger) and counts as delivered.
 *
 * [check] and [repository] come from the schedule data graph at each run and are null while the iOS graph lacks it
 * (`scheduleDataModule`, IO-09b); the step is then skipped.
 */
class ScheduleChangesRefresh(
    private val check: () -> ScheduleChangesCheck?,
    private val repository: () -> ScheduleChangesRepository?,
    private val notifier: MorningScheduleChangeNotifier,
    private val timeProvider: AcademicTimeProvider,
) {
    suspend fun run(): CheckOutcome {
        val check = check() ?: return CheckOutcome.SKIPPED
        val outcome = check.run()
        if (outcome != CheckOutcome.SKIPPED) repository()?.let { scheduleMorningDigest(it) }
        return outcome
    }

    private suspend fun scheduleMorningDigest(repository: ScheduleChangesRepository) {
        val now = timeProvider.localNow()
        if (!QuietHours.isQuiet(now.time)) return
        val morning = LocalDateTime(now.date, QuietHours.UNTIL)
        val decision = ScheduleChangeDigests.decide(repository.observeChanges().first(), morning)
        decision.digest?.let { notifier.showAt(it, morning.toInstant(timeProvider.timeZone)) }
        if (decision.handled.isNotEmpty()) repository.markNotified(decision.handled)
    }
}
