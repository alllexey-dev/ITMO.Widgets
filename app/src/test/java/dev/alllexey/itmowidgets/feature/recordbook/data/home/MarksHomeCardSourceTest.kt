package dev.alllexey.itmowidgets.feature.recordbook.data.home

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.FakeMarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.markNews
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksHomeCardSourceTest {
    private val repository = FakeMarkTrackingRepository()
    private val source = MarksHomeCardSource(repository)

    @Test
    fun `no unread subjects give no card`() = runTest {
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `the card names the unread subjects in the repository's order`() = runTest {
        repository.news.value = listOf(
            markNews("Тестовый предмет 2", detectedAt = Instant.parse("2026-09-07T10:00:00Z")),
            markNews("Тестовый предмет 1", detectedAt = Instant.parse("2026-09-07T09:00:00Z"))
        )

        val card = source.observe().first().single()

        assertEquals(HomeCard.Marks(listOf("Тестовый предмет 2", "Тестовый предмет 1")), card)
    }

    @Test
    fun `only the close button of this card marks everything read`() = runTest {
        repository.news.value = listOf(markNews())

        source.dismiss(HomeCardKind.SPORT)
        assertEquals(0, repository.markAllReadCalls)

        source.dismiss(HomeCardKind.MARKS)
        assertEquals(1, repository.markAllReadCalls)
        assertTrue(source.observe().first().isEmpty())
    }

    @Test
    fun `refresh succeeds without a check`() = runTest {
        assertEquals(AppResult.Success(Unit), source.refresh())
        assertEquals(0, repository.myItmoChecks)
        assertEquals(0, repository.barsChecks)
    }
}
