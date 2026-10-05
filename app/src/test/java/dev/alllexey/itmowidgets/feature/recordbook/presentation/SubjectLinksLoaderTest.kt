package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectLinksLoaderTest {
    private val repository = FakeSubjectLinksRepository()
    private val loader = SubjectLinksLoader(repository)
    private val scope = ResourceScope(1L, "Тестовый предмет", "2026-1")

    @Test fun `the stored links open the page and no links read as loading`() {
        assertEquals(repository.state.value, loader.cached(scope))
        repository.state.value = SubjectLinksState.Loading
        assertEquals(SubjectLinksState.Loading, loader.cached(scope))
    }

    @Test fun `following the links refreshes them once and votes follow the connection and restrictions`() = runTest {
        val updates = mutableListOf<SubjectLinksUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { loader.observe(scope).collect(updates::add) }
        advanceUntilIdle()
        assertEquals(1, repository.refreshes)
        assertTrue(updates.last().canVote)

        repository.restrictions.value = listOf(UserRestriction("r", RestrictionCapability.VOTE, "spam", null))
        advanceUntilIdle()
        assertFalse(updates.last().canVote)

        repository.restrictions.value = emptyList()
        repository.state.value = SubjectLinksState.Content(linksSnapshot(servicesEnabled = false))
        advanceUntilIdle()
        assertFalse(updates.last().canVote)
        assertEquals(1, repository.refreshes)
    }

    @Test fun `the arrow of the current vote takes it back`() = runTest {
        val up = subjectLink("up", LinkCategory.NOTES, LinkVisibility.ALL, isMine = false, myVote = 1)
        loader.vote(scope, up, up = true)
        loader.vote(scope, up, up = false)
        repository.result = AppResult.Failure(AppError.Network)
        val failed = loader.vote(scope, subjectLink("none", isMine = false), up = true)
        assertEquals(listOf("vote:up:0", "vote:up:-1", "vote:none:1"), repository.actions)
        assertEquals(AppResult.Failure(AppError.Network), failed)
    }

    @Test fun `ranked links keep the order first shown until they are ranked afresh`() {
        val a = subjectLink("a", score = 2)
        val b = subjectLink("b", score = 1)
        assertEquals(listOf(a, b), loader.arrange(listOf(a, b)))
        assertEquals(listOf(a, b), loader.arrange(listOf(b, a)))
        loader.rankAfresh()
        assertEquals(listOf(b, a), loader.arrange(listOf(b, a)))
    }
}
