package dev.alllexey.itmowidgets.feature.recordbook.data.bell

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNews
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkNewsTexts
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.toSubjectArgs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * New marks in the bell, one row per subject and check, from the local store: the value in the title and the text,
 * the change of points in front, leading to the subject page (or the recordbook when My ITMO has no single such
 * subject). Opening the bell reads them, which also removes the digest notification.
 */
class MarksBellSource(
    private val repository: MarkTrackingRepository,
    private val barsPreference: BarsPreferenceRepository,
) : BellSource {

    override fun observe(): Flow<List<BellItem>> = repository.observeHistory().map { history ->
        val withBars = barsPreference.isEnabled()
        history.map { item(it, withBars) }
    }

    override suspend fun markAllRead() = repository.markAllRead()

    private suspend fun item(news: MarkNews, withBars: Boolean): BellItem {
        val subject = repository.target(news, withBars)?.toSubjectArgs()
        return BellItem(
            id = "mark:${news.id}:${news.detectedAt.toEpochMilliseconds()}",
            kind = BellKind.MARK,
            at = news.detectedAt,
            title = MarkNewsTexts.title(news),
            text = MarkNewsTexts.details(news),
            lead = MarkNewsTexts.delta(news.value),
            target = subject?.let { EntryRoute(AppTab.HOME, overlay = AppRoutes.RecordbookSubject(it)) }
                ?: EntryRoute(AppTab.RECORDBOOK),
        )
    }
}
