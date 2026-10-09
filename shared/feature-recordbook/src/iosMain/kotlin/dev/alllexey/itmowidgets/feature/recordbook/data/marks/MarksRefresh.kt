package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.QuietHours
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

/**
 * The mark check as one step of the iOS background runner (IO-09d3): KM-11b2's [MarksCheck] (My ITMO, BARS with the
 * cookie renewal and no WebView, the sheets), then the quiet hours. Android's check keeps marks found at night until
 * its first run after 06:00, which WorkManager makes within three hours; iOS may not wake the app for many hours, so
 * marks and the BARS reminder found between 00:00 and 06:00 (Moscow, the academic zone) are handed to the system for
 * 06:00 at once (a calendar trigger) and count as delivered, as the schedule change step does (IO-14).
 */
class MarksRefresh(
    private val check: MarksCheck,
    private val repository: MarkTrackingRepository,
    private val markSources: MarkSourcePreferences,
    private val barsPreference: BarsPreferenceRepository,
    private val notifier: MorningMarksNotifier,
    private val timeProvider: AcademicTimeProvider,
) {
    suspend fun run(): CheckOutcome {
        val outcome = check.run()
        if (outcome != CheckOutcome.SKIPPED) scheduleMorningDelivery()
        return outcome
    }

    private suspend fun scheduleMorningDelivery() {
        val now = timeProvider.localNow()
        if (!QuietHours.isQuiet(now.time)) return
        val morning = LocalDateTime(now.date, QuietHours.UNTIL)
        val at = morning.toInstant(timeProvider.timeZone)
        val decision = MarkDigests.decide(repository.observeNews().first(), markSources.getBarsLoginPrompt(), morning)
        decision.digest?.let { digest ->
            val target = digest.single?.let { repository.target(it, barsPreference.isEnabled()) }
            notifier.showDigestAt(digest, target, at)
        }
        if (decision.showPrompt) {
            notifier.showBarsPromptAt(at)
            markSources.setBarsLoginPrompt(BarsLoginPrompt.SHOWN)
        }
        if (decision.handled.isNotEmpty()) repository.markNotified(decision.handled)
    }
}
