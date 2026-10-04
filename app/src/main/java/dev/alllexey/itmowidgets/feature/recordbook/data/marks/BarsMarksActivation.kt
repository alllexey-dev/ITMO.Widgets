package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.feature.recordbook.data.bars.BarsSessionListener
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * A successful BARS answer of the account turns "Оценки БАРС" on the first time and withdraws the sign-in prompt.
 * It depends on DataStore, not on the mark repository: the repository reads BARS through the client that calls this.
 */
class BarsMarksActivation @Inject constructor(
    private val markSources: MarkSourcePreferences,
    private val scheduler: MarksScheduler,
    private val notifier: AppNotifier
) : BarsSessionListener {

    override suspend fun onBarsAnswered() {
        try {
            if (markSources.enableBarsMarksIfUnset()) scheduler.ensurePeriodic()
            if (markSources.getBarsLoginPrompt() != BarsLoginPrompt.NONE) {
                markSources.setBarsLoginPrompt(BarsLoginPrompt.NONE)
                notifier.cancel(AppNotificationChannels.MARKS, MarkDigests.PROMPT_ID)
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // A failed preference write must not turn a good BARS answer into an error; the next answer tries again.
        }
    }
}
