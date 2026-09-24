package dev.alllexey.itmowidgets.feature.resources.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectLinksViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeSubjectLinksRepository()
    private val shared = subjectLink("shared", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false, score = 3)

    @Test fun `links are grouped by category with chats and past periods last`() = runTest(main.dispatcher) {
        show(linksSnapshot(
            mine = listOf(subjectLink("own-other", LinkCategory.OTHER), subjectLink("own-scores", LinkCategory.SCORES)),
            shared = listOf(subjectLink("chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false),
                subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.FLOW, isMine = false), shared),
            previous = listOf(subjectLink("old", LinkCategory.MATERIALS, LinkVisibility.ALL, isMine = false)),
        ))
        val vm = model()

        val sections = vm.uiState.value.sections

        assertEquals(listOf(
            LinkSection.Category(LinkCategory.SCORES, listOf(subjectLink("own-scores", LinkCategory.SCORES), shared)),
            LinkSection.Category(LinkCategory.TASKS, listOf(subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.FLOW, isMine = false))),
            LinkSection.Category(LinkCategory.OTHER, listOf(subjectLink("own-other", LinkCategory.OTHER))),
            LinkSection.Category(LinkCategory.CHAT, listOf(subjectLink("chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false))),
            LinkSection.Previous(listOf(subjectLink("old", LinkCategory.MATERIALS, LinkVisibility.ALL, isMine = false))),
        ), sections)
    }

    @Test fun `arrows set a vote and the same arrow removes it`() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared.copy(myVote = 1))))
        val vm = model()

        vm.vote("shared", up = true); runCurrent()
        vm.vote("shared", up = false); runCurrent()

        assertEquals(listOf("vote:shared:0", "vote:shared:-1"), repository.actions)
    }

    @Test fun `adding to own flips the saved flag`() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared, subjectLink("kept", isMine = false, isSaved = true))))
        val vm = model()

        vm.toggleSaved("shared"); runCurrent()
        vm.toggleSaved("kept"); runCurrent()

        assertEquals(listOf("saved:shared:true", "saved:kept:false"), repository.actions)
    }

    @Test fun `pinning the pinned link unpins it`() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared), pinnedId = "own"))
        val vm = model()

        vm.pin("shared"); runCurrent()
        vm.pin("own"); runCurrent()

        assertEquals(listOf("pin:shared", "pin:null"), repository.actions)
    }

    @Test fun `a failed action sends an event and keeps the list`() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val vm = model()
        val before = vm.uiState.value
        repository.result = AppResult.Failure(AppError.Network)

        vm.vote("shared", up = true); runCurrent()

        assertEquals(LinkEvent.Failed(UiText.Resource(R.string.common_error_network)), vm.events.first())
        assertEquals(before, vm.uiState.value)
    }

    @Test fun `a successful action sends Done`() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val vm = model()

        vm.toggleSaved("shared"); runCurrent()

        assertEquals(LinkEvent.Done, vm.events.first())
    }

    @Test fun `a second action while one is in flight is ignored`() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val gate = CompletableDeferred<Unit>()
        repository.gate = { gate.await() }
        val vm = model()

        vm.vote("shared", up = true); vm.report("shared", ResourceReportReason.SPAM, null)
        runCurrent(); gate.complete(Unit); runCurrent()

        assertEquals(listOf("vote:shared:1"), repository.actions)
    }

    @Test fun `restrictions and a missing connection hide votes and reports`() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val vm = model()
        assertTrue(vm.uiState.value.canVote && vm.uiState.value.canReport)

        repository.restrictions.value = listOf(restriction(RestrictionCapability.VOTE)); runCurrent()
        assertFalse(vm.uiState.value.canVote); assertTrue(vm.uiState.value.canReport)

        repository.restrictions.value = listOf(restriction(RestrictionCapability.ALL)); runCurrent()
        assertFalse(vm.uiState.value.canVote); assertFalse(vm.uiState.value.canReport)

        repository.restrictions.value = emptyList()
        show(linksSnapshot(shared = listOf(shared), servicesEnabled = false)); runCurrent()
        assertFalse(vm.uiState.value.canVote); assertFalse(vm.uiState.value.canReport)
    }

    @Test fun `a failed pull reports the error while a silent load does not`() = runTest(main.dispatcher) {
        repository.refreshResult = AppResult.Failure(AppError.Network)
        val vm = model()
        val events = mutableListOf<LinkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        runCurrent()
        assertEquals(1, repository.refreshes)
        assertTrue(events.isEmpty())

        vm.refresh(); runCurrent()

        assertEquals(listOf(LinkEvent.Failed(UiText.Resource(R.string.common_error_network))), events)
        assertEquals(2, repository.restrictionRefreshes)
        assertFalse(vm.uiState.value.refreshing)
    }

    private fun show(snapshot: SubjectLinksSnapshot) { repository.state.value = SubjectLinksState.Content(snapshot) }

    private fun restriction(capability: RestrictionCapability) = UserRestriction("r", capability, "Правила", null)

    private fun TestScope.model(): SubjectLinksViewModel {
        val vm = SubjectLinksViewModel(SavedStateHandle(mapOf(SubjectLinksArgs.SUBJECT_ID to 42L,
            SubjectLinksArgs.SUBJECT_NAME to "Предмет", SubjectLinksArgs.PERIOD_KEY to "2026-1")), repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        return vm
    }
}
