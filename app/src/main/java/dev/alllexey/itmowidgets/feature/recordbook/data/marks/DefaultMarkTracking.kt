package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.recordbook.MarkTracking
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkDigests
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkSource
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two switches and the session decide whether the periodic check exists: it runs with a session and at least one
 * source on. Off forgets that source's snapshot, so turning it on again starts from a baseline, not a flood.
 */
@Singleton
class DefaultMarkTracking @Inject constructor(
    private val settings: AppSettingsStorage,
    private val sessionTokens: SessionTokenStore,
    private val scheduler: MarksScheduler,
    private val repository: MarkTrackingRepository,
    private val notifier: AppNotifier
) : MarkTracking {

    override suspend fun setMyItmoEnabled(enabled: Boolean) {
        settings.setMyItmoMarksEnabled(enabled)
        if (!enabled) repository.resetSource(MarkSource.MY_ITMO)
        syncWork()
    }

    override suspend fun setBarsEnabled(enabled: Boolean) {
        settings.setBarsMarksEnabled(enabled)
        if (!enabled) {
            repository.resetSource(MarkSource.BARS)
            settings.setBarsLoginPrompt(BarsLoginPrompt.NONE)
            notifier.cancel(AppNotificationChannels.MARKS, MarkDigests.PROMPT_ID)
        }
        syncWork()
    }

    override suspend fun syncWork() {
        val anySource = settings.getMyItmoMarksEnabled() || settings.getBarsMarksEnabled() == true
        if (sessionTokens.hasRefreshToken() && anySource) scheduler.ensurePeriodic() else scheduler.cancel()
    }

    override fun stopWork() = scheduler.cancel()

    override fun checkNow() = scheduler.runOnce()
}
