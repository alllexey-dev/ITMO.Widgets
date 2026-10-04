package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.work.CheckOutcome
import dev.alllexey.itmowidgets.core.work.outcomeOf
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsCheck
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksNotifier
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** One background run: check the switched-on sources, then deliver what waits, even when a check failed. */
class MarksCheck @Inject constructor(
    private val sessionTokens: SessionTokenStore,
    private val markSources: MarkSourcePreferences,
    private val repository: MarkTrackingRepository,
    private val notifier: MarksNotifier,
    private val barsPreference: BarsPreferenceRepository,
    private val timeProvider: AcademicTimeProvider
) {
    suspend fun run(): CheckOutcome {
        if (!sessionTokens.hasRefreshToken()) return CheckOutcome.SKIPPED
        val myItmo = markSources.getMyItmoMarksEnabled()
        val bars = markSources.getBarsMarksEnabled() == true
        val sheets = markSources.getSheetMarksEnabled()
        if (!myItmo && !bars && !sheets) return CheckOutcome.SKIPPED
        val errors = mutableListOf<AppError>()
        if (myItmo) (repository.checkMyItmo() as? AppResult.Failure)?.let { errors += it.error }
        if (bars) {
            when (val result = repository.checkBars()) {
                // A network failure is retried and never prompts: only ITMO.ID's answer or missing cookies end a session.
                is BarsCheck.Failed -> errors += result.error
                BarsCheck.SessionEnded ->
                    if (markSources.getBarsLoginPrompt() == BarsLoginPrompt.NONE) markSources.setBarsLoginPrompt(BarsLoginPrompt.PENDING)
                is BarsCheck.Done, BarsCheck.NoSession -> Unit
            }
        }
        if (sheets) errors += repository.checkSheets().errors
        // Marks found in the quiet hours wait here for the first run after them, whatever the network does then.
        deliver()
        return outcomeOf(errors)
    }

    private suspend fun deliver() {
        val decision = MarkDigests.decide(
            repository.observeNews().first(),
            markSources.getBarsLoginPrompt(),
            timeProvider.now().toLocalDateTime()
        )
        decision.digest?.let { digest ->
            val target = digest.single?.let { repository.target(it, barsPreference.isEnabled()) }
            notifier.showDigest(digest, target)
        }
        if (decision.showPrompt) {
            notifier.showBarsPrompt()
            // Shown or not (no permission), the reminder is spent; the recordbook's snackbar still offers the sign-in.
            markSources.setBarsLoginPrompt(BarsLoginPrompt.SHOWN)
        }
        // Delivered even without the permission: the records stay in the home card and the recordbook dots.
        if (decision.handled.isNotEmpty()) repository.markNotified(decision.handled)
    }
}
