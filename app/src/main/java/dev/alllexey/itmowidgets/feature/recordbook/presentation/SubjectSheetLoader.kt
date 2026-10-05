package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.resources.GoogleSheetUrl
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkRanking
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/** The own total from a sheet on the subject page: the connected one, or the sheet links to connect. */
class SubjectSheetLoader @Inject constructor(
    private val sheets: SheetScoresRepository,
    private val time: AcademicTimeProvider,
) {
    /** On entry and on every pull, without an indicator: the stored total shows until the new one arrives. */
    fun observe(scope: ResourceScope): Flow<List<SheetScore>> = channelFlow {
        launch { sheets.refresh(scope) }
        sheets.observe().collect { send(it) }
    }

    /** The connection of [scope], else the sheet links to connect, else nothing. */
    fun state(scope: ResourceScope, links: SubjectLinksState, scores: List<SheetScore>): SubjectSheetState? {
        scores.firstOrNull { it.scope.key == scope.key }?.let { score ->
            val updatedAt = score.updatedAt?.toLocalDateTime(time.timeZone)
            return SubjectSheetState.Connected(score, updatedAt, time.today())
        }
        val snapshot = (links as? SubjectLinksState.Content)?.snapshot ?: return null
        val options = (snapshot.mine + snapshot.shared + snapshot.previous)
            .filter { GoogleSheetUrl.parse(it.url) != null }
            .sortedWith(compareBy<SubjectLink> { link ->
                when {
                    link.isMine -> 0
                    link.id == snapshot.pinnedId -> 1
                    link.category == LinkCategory.SCORES -> 2
                    else -> 3
                }
            }.then(SubjectLinkRanking))
            .distinctBy { it.url.trim() }
            .map { SheetLinkOption(it.url, it.title, it.isMine) }
        return options.takeIf { it.isNotEmpty() }?.let(SubjectSheetState::Hint)
    }

    suspend fun disconnect(scope: ResourceScope) = sheets.disconnect(scope)
}
