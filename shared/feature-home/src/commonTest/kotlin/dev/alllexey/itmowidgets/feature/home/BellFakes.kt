package dev.alllexey.itmowidgets.feature.home

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.home.domain.BellRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** The bell over [items] with an in-memory seen set; reads, refreshes and answers are recorded. */
class FakeBellRepository(vararg initial: BellItem, seen: Set<String> = emptySet()) : BellRepository {
    val items = MutableStateFlow(initial.toList())
    val seen = MutableStateFlow(seen)
    var readAllCalls = 0
    var refreshCalls = 0
    val answers = mutableListOf<Pair<Int, Boolean>>()
    var answer: AppResult<Unit> = AppResult.Success(Unit)

    override fun observeItems(): Flow<List<BellItem>> = items

    override fun observeSeen(): Flow<Set<String>> = seen

    override fun observeUnseenCount(): Flow<Int> = combine(items, seen) { all, ids -> all.count { it.id !in ids } }

    override suspend fun markSeen(ids: Set<String>) {
        seen.value += ids
    }

    override suspend fun readAll() {
        readAllCalls++
    }

    override suspend fun refresh(): AppResult<Unit> {
        refreshCalls++
        return AppResult.Success(Unit)
    }

    override suspend fun answer(isu: Int, accept: Boolean): AppResult<Unit> {
        answers += isu to accept
        return answer
    }
}

fun bellItem(id: String, at: Instant? = Instant.parse("2026-09-07T09:05:00Z"), request: Int? = null) = BellItem(
    id = id,
    kind = if (request != null) BellKind.FRIEND_REQUEST else BellKind.MARK,
    at = at,
    title = UiText.Dynamic(id),
    request = request,
)
