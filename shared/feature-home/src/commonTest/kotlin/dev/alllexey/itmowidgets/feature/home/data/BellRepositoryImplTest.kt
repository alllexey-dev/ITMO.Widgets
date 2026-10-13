package dev.alllexey.itmowidgets.feature.home.data

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.PreferenceStores
import dev.alllexey.itmowidgets.feature.home.bellItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class BellRepositoryImplTest {

    private val stores = PreferenceStores()

    @Test
    fun requestsLeadThenTheNewestAndAFailingSourceCountsAsEmpty() = runTest {
        val bell = BellRepositoryImpl(
            listOf(
                Source(bellItem("old", at = Instant.parse("2026-09-06T09:00:00Z"))),
                Source(bellItem("request", at = null, request = 1)),
                Source(bellItem("new", at = Instant.parse("2026-09-07T09:00:00Z"))),
                Broken,
            ),
            stores.bell,
        )

        assertEquals(listOf("request", "new", "old"), bell.observeItems().first().map { it.id })
    }

    @Test
    fun theBadgeCountsWhatWasNotSeen() = runTest {
        val bell = BellRepositoryImpl(listOf(Source(bellItem("a"), bellItem("b"))), stores.bell)

        assertEquals(2, bell.observeUnseenCount().first())
        bell.markSeen(setOf("a"))
        assertEquals(1, bell.observeUnseenCount().first())
        stores.bell.clearSeen()
        assertEquals(2, bell.observeUnseenCount().first())
    }

    @Test
    fun readingRefreshingAndAnsweringReachTheSources() = runTest {
        val first = Source()
        val second = Source(refresh = AppResult.Failure(AppError.Network), answers = true)
        val bell = BellRepositoryImpl(listOf(first, second), stores.bell)

        bell.readAll()
        assertEquals(AppResult.Failure(AppError.Network), bell.refresh())
        assertEquals(AppResult.Success(Unit), bell.answer(42, accept = true))
        assertEquals(1, first.reads)
        assertEquals(1, second.reads)
        assertEquals(listOf(42 to true), second.answered)
    }

    private class Source(
        vararg val items: BellItem,
        val refresh: AppResult<Unit> = AppResult.Success(Unit),
        val answers: Boolean = false,
    ) : BellSource {
        var reads = 0
        val answered = mutableListOf<Pair<Int, Boolean>>()

        override fun observe(): Flow<List<BellItem>> = flowOf(items.toList())
        override suspend fun refresh(): AppResult<Unit> = refresh
        override suspend fun markAllRead() {
            reads++
        }
        override suspend fun answer(isu: Int, accept: Boolean): AppResult<Unit>? {
            if (!answers) return null
            answered += isu to accept
            return AppResult.Success(Unit)
        }
    }

    private object Broken : BellSource {
        override fun observe(): Flow<List<BellItem>> = flow { error("Synthetic failure") }
    }
}
