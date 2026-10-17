package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.storage.BellPreferences
import dev.alllexey.itmowidgets.feature.home.domain.BellRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.supervisorScope

/**
 * The bell over every Koin `BellSource` (an open set) and the seen ids in [BellPreferences]. A source that fails
 * counts as empty, so one feature never empties the bell. The seen ids belong to the account: sign-out clears them
 * (`homeModule`).
 */
class BellRepositoryImpl(
    private val sources: List<BellSource>,
    private val preferences: BellPreferences,
) : BellRepository {

    override fun observeItems(): Flow<List<BellItem>> = if (sources.isEmpty()) {
        flowOf(emptyList())
    } else {
        combine(sources.map { source -> source.observe().catch { emit(emptyList()) } }) { lists ->
            lists.flatMap { it }.sortedWith(ORDER)
        }
    }

    override fun observeSeen(): Flow<Set<String>> = preferences.observeSeen()

    override fun observeUnseenCount(): Flow<Int> =
        combine(observeItems(), observeSeen()) { items, seen -> items.count { it.id !in seen } }

    override suspend fun markSeen(ids: Set<String>) {
        if (ids.isNotEmpty()) preferences.addSeen(ids)
    }

    override suspend fun readAll() = sources.forEach { it.markAllRead() }

    override suspend fun refresh(): AppResult<Unit> = supervisorScope {
        sources.map { source -> async { source.refresh() } }.awaitAll()
            .firstOrNull { it is AppResult.Failure } ?: AppResult.Success(Unit)
    }

    override suspend fun answer(isu: Int, accept: Boolean): AppResult<Unit>? =
        sources.firstNotNullOfOrNull { it.answer(isu, accept) }

    private companion object {
        /** Requests wait for an answer, so they lead; within each part newest first, an unknown moment last. */
        val ORDER: Comparator<BellItem> = compareBy<BellItem> { it.request == null }.thenByDescending { it.at }
    }
}
