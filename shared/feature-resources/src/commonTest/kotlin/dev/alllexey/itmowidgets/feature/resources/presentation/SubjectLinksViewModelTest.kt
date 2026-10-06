package dev.alllexey.itmowidgets.feature.resources.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SubjectLinksArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceReportReason
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.RestrictionCapability
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.UserRestriction
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeSubjectLinksRepository
import dev.alllexey.itmowidgets.core.testing.linkTime
import dev.alllexey.itmowidgets.core.testing.linksSnapshot
import dev.alllexey.itmowidgets.core.testing.subjectLink
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SubjectLinksViewModelTest {
    private val main = TestMainDispatcher()
    private val repository = FakeSubjectLinksRepository()
    private val shared = subjectLink("shared", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false, score = 3)

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test fun linksAreGroupedByCategoryWithChatsAndPastPeriodsLast() = runTest(main.dispatcher) {
        show(linksSnapshot(
            mine = listOf(subjectLink("own-other", LinkCategory.OTHER), subjectLink("own-scores", LinkCategory.SCORES)),
            shared = listOf(subjectLink("chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false),
                subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.FLOW, isMine = false), shared),
            previous = listOf(subjectLink("old", LinkCategory.MATERIALS, LinkVisibility.ALL, isMine = false)),
        ))
        val vm = model()

        val sections = vm.uiState.value.sections

        assertEquals(listOf(
            LinkSection.Category(LinkCategory.SCORES, listOf(shared, subjectLink("own-scores", LinkCategory.SCORES))),
            LinkSection.Category(LinkCategory.TASKS, listOf(subjectLink("tasks", LinkCategory.TASKS, LinkVisibility.FLOW, isMine = false))),
            LinkSection.Category(LinkCategory.OTHER, listOf(subjectLink("own-other", LinkCategory.OTHER))),
            LinkSection.Category(LinkCategory.CHAT, listOf(subjectLink("chat", LinkCategory.CHAT, LinkVisibility.FLOW, isMine = false))),
            LinkSection.Previous(listOf(subjectLink("old", LinkCategory.MATERIALS, LinkVisibility.ALL, isMine = false))),
        ), sections)
    }

    @Test fun ownAndOthersLinksOfACategoryAreRankedTogetherNewerFirstOnEqualScores() = runTest(main.dispatcher) {
        val ownTop = subjectLink("own-top", score = 7)
        val ownTied = subjectLink("own-tied", score = 2)
        val newerTied = subjectLink("newer-tied", LinkCategory.MATERIALS, LinkVisibility.ALL, isMine = false, score = 2)
            .copy(updatedAt = linkTime + 1.hours)
        val low = subjectLink("low", visibility = LinkVisibility.FLOW, isMine = false, score = -1)
        show(linksSnapshot(mine = listOf(ownTied, ownTop), shared = listOf(low, newerTied)))
        val vm = model()

        assertEquals(listOf(LinkSection.Category(LinkCategory.MATERIALS, listOf(ownTop, newerTied, ownTied, low))), vm.uiState.value.sections)
    }

    @Test fun aVoteKeepsTheRowsInPlaceUntilAPullRanksThemAfresh() = runTest(main.dispatcher) {
        val a = subjectLink("a", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false, score = 3)
        val b = subjectLink("b", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false, score = 2)
        val c = subjectLink("c", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false, score = 1)
        repository.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(a, b, c)))
        val vm = model()
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.scores())

        repository.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(a, b, c.copy(score = 9, myVote = 1))))
        runCurrent()
        assertEquals(listOf("a", "b", "c"), vm.uiState.value.scores())

        val added = subjectLink("new", LinkCategory.SCORES, LinkVisibility.ALL, isMine = false, score = 20)
        repository.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(a, b, c.copy(score = 9), added)))
        runCurrent()
        assertEquals(listOf("a", "b", "c", "new"), vm.uiState.value.scores())

        vm.refresh(RefreshMode.Pull)
        runCurrent()
        repository.state.value = SubjectLinksState.Content(linksSnapshot(shared = listOf(a, b, c.copy(score = 9), added)))
        runCurrent()
        assertEquals(listOf("new", "c", "a", "b"), vm.uiState.value.scores())
    }

    private fun SubjectLinksUiState.scores() =
        sections.filterIsInstance<LinkSection.Category>().single { it.category == LinkCategory.SCORES }.links.map { it.id }

    @Test fun arrowsSetAVoteAndTheSameArrowRemovesIt() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared.copy(myVote = 1))))
        val vm = model()

        vm.vote("shared", up = true); runCurrent()
        vm.vote("shared", up = false); runCurrent()

        assertEquals(listOf("vote:shared:0", "vote:shared:-1"), repository.actions)
    }

    @Test fun pinningThePinnedLinkUnpinsIt() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared), pinnedId = "own"))
        val vm = model()

        vm.pin("shared"); runCurrent()
        vm.pin("own"); runCurrent()

        assertEquals(listOf("pin:shared", "pin:null"), repository.actions)
    }

    @Test fun aFailedActionSendsAnEventAndKeepsTheList() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val vm = model()
        val before = vm.uiState.value
        repository.result = AppResult.Failure(AppError.Network)

        vm.vote("shared", up = true); runCurrent()

        assertEquals(LinkEvent.Failed(AppError.Network), vm.events.first())
        assertEquals(before, vm.uiState.value)
    }

    @Test fun aSuccessfulActionSendsDone() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val vm = model()

        vm.pin("shared"); runCurrent()

        assertEquals(LinkEvent.Done, vm.events.first())
    }

    @Test fun aVoteSendsNoDoneSoTheActionsSheetStaysOpen() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val vm = model()
        val events = mutableListOf<LinkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }

        vm.vote("shared", up = false); runCurrent()

        assertEquals(listOf("vote:shared:-1"), repository.actions)
        assertTrue(events.isEmpty())
    }

    @Test fun aSecondActionWhileOneIsInFlightIsIgnored() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val gate = CompletableDeferred<Unit>()
        repository.gate = { gate.await() }
        val vm = model()

        vm.vote("shared", up = true); vm.report("shared", ResourceReportReason.SPAM, null)
        runCurrent(); gate.complete(Unit); runCurrent()

        assertEquals(listOf("vote:shared:1"), repository.actions)
    }

    @Test fun restrictionsAndAMissingConnectionHideVotesAndReports() = runTest(main.dispatcher) {
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

    @Test fun aFailedPullReportsTheErrorWhileASilentLoadDoesNot() = runTest(main.dispatcher) {
        repository.refreshResult = AppResult.Failure(AppError.Network)
        val vm = model()
        val events = mutableListOf<LinkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        runCurrent()
        assertEquals(1, repository.refreshes)
        assertTrue(events.isEmpty())

        vm.refresh(RefreshMode.Pull); runCurrent()

        assertEquals(listOf<LinkEvent>(LinkEvent.Failed(AppError.Network)), events)
        assertEquals(2, repository.restrictionRefreshes)
        assertFalse(vm.uiState.value.refreshing)
    }

    @Test fun theSilentLoadOnEntryShowsNoIndicatorAndAPullJoiningItDoesUntilItEnds() = runTest(main.dispatcher) {
        val gated = GatedRefreshes(repository)
        val vm = model(gated)
        assertFalse(vm.uiState.value.refreshing)

        vm.refresh(RefreshMode.Pull); runCurrent()
        assertTrue(vm.uiState.value.refreshing)

        gated.gate.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.refreshing)
        assertEquals(1, repository.refreshes)
    }

    @Test fun aForcedRetryReplacesTheSilentLoadInFlight() = runTest(main.dispatcher) {
        val gated = GatedRefreshes(repository)
        val vm = model(gated)

        vm.refresh(RefreshMode.Force); runCurrent()
        assertTrue(vm.uiState.value.refreshing)
        assertEquals(2, gated.started)

        gated.gate.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.refreshing)
        assertEquals(1, repository.refreshes)
    }

    @Test fun aFailureSentWhileNoViewCollectsReachesTheNextCollectorOnce() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val vm = model()
        val first = mutableListOf<LinkEvent>()
        val view = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { first += it } }
        view.cancel()
        repository.result = AppResult.Failure(AppError.Forbidden)

        vm.vote("shared", up = true); runCurrent()
        val second = mutableListOf<LinkEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { second += it } }
        runCurrent()

        assertTrue(first.isEmpty())
        assertEquals(listOf<LinkEvent>(LinkEvent.Failed(AppError.Forbidden)), second)
    }

    @Test fun aFailedActionFreesTheSheetAndASuccessfulOneKeepsItBusyUntilItCloses() = runTest(main.dispatcher) {
        show(linksSnapshot(shared = listOf(shared)))
        val gate = CompletableDeferred<Unit>()
        repository.gate = { gate.await() }
        repository.result = AppResult.Failure(AppError.Network)
        val vm = model()

        vm.pin("shared"); runCurrent()
        assertTrue(vm.uiState.value.busy)
        gate.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.busy)

        repository.result = AppResult.Success(Unit)
        vm.delete("shared"); runCurrent()

        assertEquals(listOf("pin:shared", "delete:shared"), repository.actions)
        assertTrue(vm.uiState.value.busy)
    }

    private fun show(snapshot: SubjectLinksSnapshot) { repository.state.value = SubjectLinksState.Content(snapshot) }

    private fun restriction(capability: RestrictionCapability) = UserRestriction("r", capability, "Правила", null)

    private fun TestScope.model(links: SubjectLinksRepository = repository): SubjectLinksViewModel {
        val vm = SubjectLinksViewModel(SavedStateHandle(mapOf(SubjectLinksArgs.SUBJECT_ID to 42L,
            SubjectLinksArgs.SUBJECT_NAME to "Предмет", SubjectLinksArgs.PERIOD_KEY to "2026-1")), links)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        runCurrent()
        return vm
    }

    /** Every refresh waits for [gate]; [started] counts the refreshes that began. */
    private class GatedRefreshes(private val fake: FakeSubjectLinksRepository) : SubjectLinksRepository by fake {
        val gate = CompletableDeferred<Unit>()
        var started = 0

        override suspend fun refresh(scope: ResourceScope): AppResult<Unit> {
            started++
            gate.await()
            return fake.refresh(scope)
        }
    }
}
