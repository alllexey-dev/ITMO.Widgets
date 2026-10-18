package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.profile
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private val main = TestMainDispatcher()
    private val time = FixedAcademicTime(LocalDateTime(LocalDate(2026, 10, 5), LocalTime(12, 0)))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    // Lists

    @Test
    fun aRequestStatusJoinsItsBaseWithTheMomentAndKeepsTheBaseWithoutOne() {
        val base = UiText.Res(SocialStrings.user_status_incoming)
        val today = profile(2, RelationshipState.INCOMING).copy(requestedAt = Instant.parse("2026-10-05T09:05:00Z"))
        val earlier = profile(2, RelationshipState.INCOMING).copy(requestedAt = Instant.parse("2026-09-28T09:00:00Z"))

        assertEquals(UiText.Joined(listOf(base, UiText.Dynamic("12:05")), ", "), today.requestStatus(base, time))
        assertEquals(UiText.Joined(listOf(base, UiText.Dynamic("28 сент.")), ", "), earlier.requestStatus(base, time))
        assertEquals(base, profile(2, RelationshipState.INCOMING).requestStatus(base, time))
        assertNull(profile(2, RelationshipState.INCOMING).requestStatus(null, time))
    }

    @Test
    fun requestsAnswerInlineSentOnesCancelAndFriendsOnlyOpenTheirCard() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(listOf(profile(1, RelationshipState.FRIENDS)))
            requests.value = LoadState.Content(
                FriendRequests(
                    incoming = listOf(profile(2, RelationshipState.INCOMING)),
                    outgoing = listOf(profile(3, RelationshipState.OUTGOING).copy(requestedAt = Instant.parse("2026-10-04T09:00:00Z"))),
                ),
            )
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        advanceUntilIdle()

        val lists = viewModel.uiState.value.lists as FriendLists.Content
        assertEquals(
            UiText.Joined(listOf(UiText.Res(SocialStrings.user_status_outgoing), UiText.Res(CoreStrings.event_moment_yesterday)), ", "),
            lists.outgoing.single().status,
            "a sent request says when",
        )
        assertEquals(1, repository.refreshes)
        assertEquals(listOf<Pair<UserAction?, UserAction?>>(UserAction.ACCEPT to UserAction.REJECT), lists.incoming.map { it.primary to it.secondary })
        assertEquals(UiText.Res(SocialStrings.user_status_incoming), lists.incoming.single().status)
        assertEquals(listOf<Pair<UserAction?, UserAction?>>(null to UserAction.CANCEL), lists.outgoing.map { it.primary to it.secondary })
        val friend = lists.friends.single()
        assertEquals(1, friend.isu)
        assertNull(friend.primary)
        assertNull(friend.secondary)
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Plural(CorePlurals.friends_count, 1, listOf(1)),
                    UiText.Plural(CorePlurals.friend_requests_count, 1, listOf(1)),
                ),
                ", ",
            ),
            lists.subtitle,
        )
    }

    @Test
    fun acceptingMovesThePersonToFriendsAndRemovalLivesInTheCard() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(emptyList())
            requests.value = LoadState.Content(
                FriendRequests(incoming = listOf(profile(2, RelationshipState.INCOMING)), outgoing = emptyList()),
            )
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        advanceUntilIdle()
        val incoming = (viewModel.uiState.value.lists as FriendLists.Content).incoming.single()

        viewModel.onAction(incoming, UserAction.ACCEPT)
        advanceUntilIdle()

        assertEquals(listOf("accept:2"), repository.actions)
        val lists = viewModel.uiState.value.lists as FriendLists.Content
        assertTrue(lists.incoming.isEmpty())
        assertEquals(listOf(2), lists.friends.map { it.isu })

        viewModel.onAction(lists.friends.single(), UserAction.REMOVE)
        advanceUntilIdle()
        assertEquals(listOf("accept:2"), repository.actions)
    }

    @Test
    fun aFailedActionSurfacesAnEventAndKeepsTheRow() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(emptyList())
            requests.value = LoadState.Content(
                FriendRequests(incoming = emptyList(), outgoing = listOf(profile(3, RelationshipState.OUTGOING))),
            )
            actionError = AppError.Network
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        advanceUntilIdle()
        val outgoing = (viewModel.uiState.value.lists as FriendLists.Content).outgoing.single()

        viewModel.onAction(outgoing, UserAction.CANCEL)

        assertEquals(FriendsEvent.ActionFailed(AppError.Network), viewModel.events.first())
        advanceUntilIdle()
        assertEquals(1, (viewModel.uiState.value.lists as FriendLists.Content).outgoing.size)
    }

    @Test
    fun disabledServicesAndErrorsMapToTheirStatesWhileARefreshKeepsProgress() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository()
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        advanceUntilIdle()
        assertEquals(FriendLists.Loading, viewModel.uiState.value.lists)

        repository.friends.value = LoadState.Disabled
        repository.requests.value = LoadState.Disabled
        advanceUntilIdle()
        assertEquals(FriendLists.Disabled, viewModel.uiState.value.lists)

        repository.friends.value = LoadState.Error(AppError.Unauthorized)
        repository.requests.value = LoadState.Content(FriendRequests.EMPTY)
        advanceUntilIdle()
        assertEquals(FriendLists.Error(AppError.Unauthorized), viewModel.uiState.value.lists)
    }

    @Test
    fun anEmptyScreenHasNoSubtitleAndOffersTheOwnLink() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(emptyList())
            requests.value = LoadState.Content(FriendRequests.EMPTY)
            currentUser.value = profile(9, RelationshipState.NONE).user
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        advanceUntilIdle()

        val lists = viewModel.uiState.value.lists as FriendLists.Content
        assertTrue(lists.isEmpty)
        assertNull(lists.subtitle)
        assertEquals(9, viewModel.uiState.value.viewer?.isu)
    }

    @Test
    fun anEmptyNameShowsThePlaceholder() = runTest(main.dispatcher) {
        val unnamed = profile(7, RelationshipState.FRIENDS).let { it.copy(user = it.user.copy(name = " ")) }
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(listOf(unnamed))
            requests.value = LoadState.Content(FriendRequests.EMPTY)
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        advanceUntilIdle()

        val row = (viewModel.uiState.value.lists as FriendLists.Content).friends.single()
        assertEquals(UiText.Res(CoreStrings.user_name_placeholder, listOf(7)), row.displayName)
    }

    @Test
    fun entryRefreshesSilentlyAPullJoinsItWithTheIndicatorAndARetryForcesANewRefresh() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = GatedSocialRepository(gate).apply {
            friends.value = LoadState.Content(emptyList())
            requests.value = LoadState.Content(FriendRequests.EMPTY)
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        runCurrent()
        assertEquals(1, repository.refreshes)
        assertFalse((viewModel.uiState.value.lists as FriendLists.Content).refreshing)

        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals(1, repository.refreshes)
        assertTrue((viewModel.uiState.value.lists as FriendLists.Content).refreshing)

        gate.complete(Unit)
        runCurrent()
        assertFalse((viewModel.uiState.value.lists as FriendLists.Content).refreshing)

        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(2, repository.refreshes)
    }

    @Test
    fun anErrorShowsProgressWhileARetryMayReplaceIt() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = GatedSocialRepository(gate).apply {
            friends.value = LoadState.Error(AppError.Network)
            requests.value = LoadState.Content(FriendRequests.EMPTY)
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        runCurrent()
        assertEquals(FriendLists.Error(AppError.Network), viewModel.uiState.value.lists)

        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(FriendLists.Loading, viewModel.uiState.value.lists)

        gate.complete(Unit)
        runCurrent()
        assertEquals(FriendLists.Error(AppError.Network), viewModel.uiState.value.lists)
    }

    @Test
    fun aSecondTapOnABusyRowIsIgnoredAndAnEventWaitsForTheNextCollector() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply {
            friends.value = LoadState.Content(emptyList())
            requests.value = LoadState.Content(
                FriendRequests(incoming = emptyList(), outgoing = listOf(profile(3, RelationshipState.OUTGOING))),
            )
            actionError = AppError.Network
        }
        val viewModel = FriendsViewModel(repository, PagedPeopleSearch(), time)
        advanceUntilIdle()
        val outgoing = (viewModel.uiState.value.lists as FriendLists.Content).outgoing.single()

        viewModel.onAction(outgoing, UserAction.CANCEL)
        viewModel.onAction(outgoing, UserAction.CANCEL)
        advanceUntilIdle()

        assertEquals(listOf("cancel:3"), repository.actions)
        val events = mutableListOf<FriendsEvent>()
        backgroundScope.launch { viewModel.events.toList(events) }
        runCurrent()
        assertEquals(listOf<FriendsEvent>(FriendsEvent.ActionFailed(AppError.Network)), events)
    }

    // Search

    @Test
    fun aQueryDebouncesAndSplitsRegisteredPeopleFromTheRestInPlaceOfTheLists() = runTest(main.dispatcher) {
        val search = PagedPeopleSearch(
            pages = mapOf(
                0 to PeopleSearchPage(
                    results = listOf(
                        PersonSearchResult(1, "Зарегистрированный", null, profile(1)),
                        PersonSearchResult(2, "Посторонний", null, null),
                    ),
                    total = 3,
                    nextOffset = 2,
                ),
                2 to PeopleSearchPage(listOf(PersonSearchResult(3, "Третий", null, null)), 3, null),
            ),
        )
        val viewModel = FriendsViewModel(FakeSocialRepository(), search, time)

        viewModel.onQueryChanged("и")
        viewModel.onQueryChanged("ив")
        advanceTimeBy(100)
        assertEquals(emptyList<String>(), search.queries)
        assertNull(viewModel.uiState.value.search)
        assertEquals("ив", viewModel.uiState.value.query)

        advanceUntilIdle()

        assertEquals(listOf("ив"), search.queries)
        val found = viewModel.uiState.value.search as PeopleSearchResults.Content
        assertEquals(listOf(1), found.registered.map { it.isu })
        assertEquals(UserAction.ADD, found.registered.single().primary)
        assertEquals(listOf(2), found.others.map { it.isu })
        assertEquals(UserAction.INVITE, found.others.single().primary)
        assertTrue(found.offersMore)

        viewModel.loadMore()
        advanceUntilIdle()
        val more = viewModel.uiState.value.search as PeopleSearchResults.Content
        assertEquals(listOf(0, 2), search.offsets)
        assertFalse(more.offersMore)
        assertEquals(listOf(2, 3), more.others.map { it.isu })
    }

    @Test
    fun addingAFriendUpdatesTheRowWithoutANewSearchAndFailuresBecomeEvents() = runTest(main.dispatcher) {
        val search = PagedPeopleSearch(
            pages = mapOf(0 to PeopleSearchPage(listOf(PersonSearchResult(1, "Кто-то", null, profile(1))), 1, null)),
        )
        val social = FakeSocialRepository()
        val viewModel = FriendsViewModel(social, search, time)
        viewModel.onQueryChanged("кто")
        advanceUntilIdle()
        val row = (viewModel.uiState.value.search as PeopleSearchResults.Content).registered.single()

        viewModel.onAction(row, UserAction.ADD)
        advanceUntilIdle()

        assertEquals(listOf("send:1"), social.actions)
        assertEquals(listOf("кто"), search.queries)
        val updated = (viewModel.uiState.value.search as PeopleSearchResults.Content).registered.single()
        assertEquals(UserAction.CANCEL, updated.primary)
        assertEquals(UiText.Res(SocialStrings.user_status_outgoing), updated.status)

        social.actionError = AppError.Forbidden
        viewModel.onAction(updated, UserAction.CANCEL)
        assertEquals(FriendsEvent.ActionFailed(AppError.Forbidden), viewModel.events.first())
    }

    @Test
    fun clearingTheQueryReturnsToTheListsAtOnceAndARetrySearchesAgain() = runTest(main.dispatcher) {
        val search = PagedPeopleSearch(
            pages = mapOf(0 to PeopleSearchPage(listOf(PersonSearchResult(2, "Посторонний", null, null)), 1, null)),
            error = AppError.Network,
        )
        val viewModel = FriendsViewModel(FakeSocialRepository(), search, time)

        viewModel.onQueryChanged("пос")
        advanceUntilIdle()
        assertEquals(PeopleSearchResults.Error(AppError.Network), viewModel.uiState.value.search)

        search.error = null
        viewModel.retrySearch()
        advanceUntilIdle()
        assertEquals(listOf("пос", "пос"), search.queries)
        assertEquals(1, (viewModel.uiState.value.search as PeopleSearchResults.Content).others.size)

        viewModel.onQueryChanged("")
        runCurrent()
        assertNull(viewModel.uiState.value.search)
        advanceUntilIdle()
        assertEquals(listOf("пос", "пос"), search.queries)
    }

    @Test
    fun inviteGoesToTheHostWithThePersonsName() = runTest(main.dispatcher) {
        val search = PagedPeopleSearch(
            pages = mapOf(0 to PeopleSearchPage(listOf(PersonSearchResult(2, "Посторонний", null, null)), 1, null)),
        )
        val viewModel = FriendsViewModel(FakeSocialRepository(), search, time)
        viewModel.onQueryChanged("пос")
        advanceUntilIdle()
        val row = (viewModel.uiState.value.search as PeopleSearchResults.Content).others.single()

        viewModel.onAction(row, UserAction.INVITE)

        assertEquals(FriendsEvent.Invite("Посторонний"), viewModel.events.first())
    }

    @Test
    fun nobodyFoundIsTheEmptyState() = runTest(main.dispatcher) {
        val viewModel = FriendsViewModel(FakeSocialRepository(), PagedPeopleSearch(), time)
        viewModel.onQueryChanged("никто")
        advanceUntilIdle()
        assertEquals(PeopleSearchResults.Empty, viewModel.uiState.value.search)
    }

    @Test
    fun theNextPageShowsTheFooterIgnoresMoreTapsAndANewQueryDropsIt() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val search = PagedPeopleSearch(
            pages = mapOf(
                0 to PeopleSearchPage(listOf(PersonSearchResult(2, "Посторонний", null, null)), 2, 1),
                1 to PeopleSearchPage(listOf(PersonSearchResult(3, "Третий", null, null)), 2, null),
            ),
        )
        val viewModel = FriendsViewModel(FakeSocialRepository(), search, time)
        viewModel.onQueryChanged("пос")
        advanceUntilIdle()
        search.pageGate = { gate.await() }

        viewModel.loadMore()
        viewModel.loadMore()
        runCurrent()
        val loading = viewModel.uiState.value.search as PeopleSearchResults.Content
        assertTrue(loading.loadingMore)
        assertFalse(loading.offersMore)
        assertEquals(listOf(0, 1), search.offsets)

        viewModel.onQueryChanged("тре")
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 0), search.offsets)
        assertFalse((viewModel.uiState.value.search as PeopleSearchResults.Content).loadingMore)
    }

    /** A social repository whose refresh waits for [gate], so a test sees the refresh in flight. */
    private class GatedSocialRepository(
        private val gate: CompletableDeferred<Unit>,
        private val delegate: FakeSocialRepository = FakeSocialRepository(),
    ) : SocialRepository by delegate {
        val friends get() = delegate.friends
        val requests get() = delegate.requests
        var refreshes = 0

        override suspend fun refresh() {
            refreshes += 1
            gate.await()
        }
    }

    private class PagedPeopleSearch(
        private val pages: Map<Int, PeopleSearchPage> = emptyMap(),
        var error: AppError? = null,
    ) : PeopleSearchRepository {
        val queries = mutableListOf<String>()
        val offsets = mutableListOf<Int>()
        /** Awaited by pages after the first one. */
        var pageGate: (suspend () -> Unit)? = null

        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> {
            queries += query
            offsets += offset
            if (offset > 0) pageGate?.invoke()
            error?.let { return AppResult.Failure(it) }
            return AppResult.Success(pages[offset] ?: PeopleSearchPage.EMPTY)
        }
    }
}
