package dev.alllexey.itmowidgets.feature.recordbook.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Unread subjects with new or changed marks, from the local store only. The check runs in the background, so
 * [refresh] has nothing to ask; the close button reads everything.
 */
@Singleton
class MarksHomeCardSource @Inject constructor(
    private val repository: MarkTrackingRepository
) : HomeCardSource {

    override fun observe(): Flow<List<HomeCard>> = repository.observeNews().map { news ->
        if (news.isEmpty()) emptyList() else listOf(HomeCard.Marks(news.map { it.name }))
    }

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun dismiss(kind: HomeCardKind) {
        if (kind == HomeCardKind.MARKS) repository.markAllRead()
    }
}
