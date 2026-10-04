package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHomeCardSource(vararg initial: HomeCard) : HomeCardSource {
    val cards = MutableStateFlow(initial.toList())
    var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
    var pendingRefresh: CompletableDeferred<AppResult<Unit>>? = null
    var refreshes = 0
    var revalidations = 0
    val dismissed = mutableListOf<HomeCardKind>()

    override fun observe(): Flow<List<HomeCard>> = cards

    override suspend fun refresh(): AppResult<Unit> {
        refreshes++
        return pendingRefresh?.await() ?: refreshResult
    }

    override suspend fun revalidate() {
        revalidations++
    }

    override suspend fun dismiss(kind: HomeCardKind) {
        dismissed += kind
    }
}
