package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.core.testing.communityReview
import dev.alllexey.itmowidgets.core.testing.ownReview
import dev.alllexey.itmowidgets.core.testing.profile
import dev.alllexey.itmowidgets.core.testing.teacherReviews
import dev.alllexey.itmowidgets.core.testing.teacherSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.shared.core.Res
import dev.alllexey.itmowidgets.shared.core.user_name_placeholder
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun loadsTheProfileAndMarksTheSignedInUsersOwnPage() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val other = viewModel(repository)
        val otherEvents = events(other)
        runCurrent()
        assertEquals(backendContent(), other.uiState.value)
        val self = viewModel(repository, currentIsu = 5)
        val selfEvents = events(self)
        runCurrent()
        assertEquals(true, self.content().social?.isSelf)
        assertEquals(emptyList<UserProfileEvent>(), otherEvents)
        assertEquals(emptyList<UserProfileEvent>(), selfEvents)
    }

    @Test
    fun aPersonSeenInAListOpensWithContentAndTheNetworkOnlyUpdatesIt() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeSocialRepository().apply {
            cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS))
            profiles = mapOf(5 to profile(5, RelationshipState.NONE))
            profileGate = { gate.await() }
        }
        val viewModel = viewModel(repository)
        val events = events(viewModel)
        runCurrent()
        assertEquals(backendContent(RelationshipState.FRIENDS), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun aFailedRefreshKeepsTheSeededPageUnlessAccessWasRevoked() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val revoked = viewModel(repository)
        val revokedEvents = events(revoked)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.NotFound), revoked.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), revokedEvents)

        repository.profileError = AppError.Network
        val offline = viewModel(repository)
        val offlineEvents = events(offline)
        runCurrent()
        assertEquals(backendContent(RelationshipState.FRIENDS), offline.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), offlineEvents)
    }

    @Test
    fun primaryActionFollowsTheRelationshipState() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val viewModel = viewModel(repository)
        val events = events(viewModel)
        runCurrent()
        viewModel.onPrimaryAction()
        runCurrent()
        assertEquals(RelationshipState.OUTGOING, viewModel.relationship())
        viewModel.onPrimaryAction()
        runCurrent()
        assertEquals(RelationshipState.NONE, viewModel.relationship())
        assertEquals(listOf("send:5", "cancel:5"), repository.actions)
        repository.profiles = mapOf(5 to profile(5, RelationshipState.INCOMING))
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        viewModel.onSecondaryAction()
        runCurrent()
        assertEquals(listOf("send:5", "cancel:5", "reject:5"), repository.actions)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun removingAFriendRequiresConfirmationAndFailuresRestoreTheProfile() = runTest(main.dispatcher) {
        val repository = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val viewModel = viewModel(repository)
        val events = events(viewModel)
        runCurrent()
        viewModel.onPrimaryAction()
        runCurrent()
        assertEquals(emptyList<String>(), repository.actions)
        repository.actionError = AppError.Network
        viewModel.removeFriend()
        runCurrent()
        assertEquals(RelationshipState.FRIENDS, viewModel.relationship())
        assertEquals(false, viewModel.content().social?.busy)
        assertEquals(listOf(UserProfileEvent.ConfirmRemove(UiText.Dynamic("Пользователь 5")), UserProfileEvent.ActionFailed(AppError.Network)), events)
    }

    @Test
    fun unknownUsersAreReportedAsNotFound() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        advanceUntilIdle()
        assertEquals(UserProfileUiState.Error(AppError.NotFound), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun teacherProfileCombinesMyITMOIdentityAndReviewsWithoutASocialAccount() = runTest(main.dispatcher) {
        val viewModel = viewModel(people = personRepository(), reviews = reviewsRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent().copy(reviews = section()), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun missingMyITMOIdentityQuietlyFallsBackToTheBackendIncludingAfterRetry() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val viewModel = viewModel(social)
        val events = events(viewModel)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun myITMONotFoundReplacesItsCachedIdentityWithBackendIdentityQuietly() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val people = FakePersonRepository().apply { cached = mapOf(5 to samplePerson(5)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun lostCachedIdentityKeepsVisibleContentUntilTheFinalReviewsResponse() = runTest(main.dispatcher) {
        val personGate = CompletableDeferred<Unit>()
        val reviewsGate = CompletableDeferred<Unit>()
        val people = FakePersonRepository().apply { cached = mapOf(5 to samplePerson(5)); gate = { personGate.await() } }
        val reviews = FakeTeacherReviewsRepository().apply { cached = mapOf(5 to sampleReviews()); gate = { reviewsGate.await() } }
        val viewModel = viewModel(people = people, reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        val initial = personContent().copy(reviews = section())
        assertEquals(initial, viewModel.uiState.value)
        personGate.complete(Unit)
        runCurrent()
        assertEquals(initial, viewModel.uiState.value)
        reviewsGate.complete(Unit)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.NotFound), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun disabledServicesProduceOneCompletePersonOnlyPageWithoutASnackbar() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profileError = AppError.CustomServicesDisabled }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        advanceUntilIdle()
        assertEquals(listOf(UserProfileUiState.Loading, personContent()), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun aFriendsProfileRetainsItsRelationshipAndIdentifiesTheCurrentUser() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val viewModel = viewModel(social, personRepository(), currentIsu = 5)
        val events = events(viewModel)
        runCurrent()
        assertEquals(SocialBlock(profile(5, RelationshipState.FRIENDS), isSelf = true, busy = false), viewModel.content().social)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun cachedOwnProfileWaitsForCurrentIdentityBeforeItsFirstContent() = runTest(main.dispatcher) {
        val currentGate = CompletableDeferred<Unit>()
        val profileGate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply {
            cachedProfiles = mapOf(5 to profile(5, RelationshipState.NONE))
            profiles = cachedProfiles
            this.profileGate = { profileGate.await() }
        }
        val current = object : CurrentUserProvider {
            override suspend fun getCurrentUser(): CurrentUser { currentGate.await(); return CurrentUser(5, "Я", null) }
        }
        val viewModel = UserProfileViewModel(handle(5), social, FakePersonRepository(), FakeTeacherReviewsRepository(), current)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        currentGate.complete(Unit)
        runCurrent()
        assertEquals(true, states.filterIsInstance<UserProfileUiState.Content>().single().social?.isSelf)
        profileGate.complete(Unit)
        runCurrent()
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun myITMONetworkFailureKeepsBackendContentAndReportsOnePartialFailure() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun myITMONetworkErrorWithoutIdentityCanBeRetriedFromFullScreenError() = runTest(main.dispatcher) {
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(people = people)
        val events = events(viewModel)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.Network), viewModel.uiState.value)
        people.people = mapOf(5 to AppResult.Success(samplePerson(5)))
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun backendNetworkErrorWithoutMyITMOIdentityCanBeRetried() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profileError = AppError.Network }
        val viewModel = viewModel(social)
        val events = events(viewModel)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.Network), viewModel.uiState.value)
        social.profileError = null
        social.profiles = mapOf(5 to profile(5))
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun threeCachedPartsShowContentWhileAllRequestsArePending() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply {
            cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profiles = cachedProfiles; profileGate = { gate.await() }
        }
        val people = personRepository().apply { cached = mapOf(5 to samplePerson(5)); this.gate = { gate.await() } }
        val reviews = reviewsRepository().apply { cached = mapOf(5 to sampleReviews()); this.gate = { gate.await() } }
        val viewModel = viewModel(social, people, reviews)
        val events = events(viewModel)
        runCurrent()
        val expected = personContent().copy(social = SocialBlock(profile(5, RelationshipState.FRIENDS), false, false), reviews = section())
        assertEquals(expected, viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(expected, viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun socialResponseBeforeDeadlineIsIncludedInTheOnlyInitialContent() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profileGate = { gate.await() } }
        val viewModel = viewModel(social, personRepository(), reviewsRepository())
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        advanceTimeBy(1_000)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(personContent().copy(social = SocialBlock(profile(5, RelationshipState.FRIENDS), false, false), reviews = section())),
            states.filterIsInstance<UserProfileUiState.Content>())
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun personResponseBeforeDeadlineOverridesCachedBackendNameInFirstContent() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.NONE)); profiles = cachedProfiles }
        val people = personRepository().apply { this.gate = { gate.await() } }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        advanceTimeBy(1_000)
        gate.complete(Unit)
        runCurrent()
        assertEquals("Персона 5", states.filterIsInstance<UserProfileUiState.Content>().single().name)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun reviewsArrivingAfterTheExactDeadlineOnlyAppendTheLastSection() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val reviews = reviewsRepository().apply { this.gate = { gate.await() } }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        advanceTimeBy(UserProfileViewModel.PART_DEADLINE.inWholeMilliseconds - 1)
        runCurrent()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, personContent(), personContent().copy(reviews = section())), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun lateReviewsFailureKeepsContentAndEmitsExactlyOnePartialFailure() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val reviews = FakeTeacherReviewsRepository().apply { results = mapOf(5 to AppResult.Failure(AppError.Network)); this.gate = { gate.await() } }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        expireDeadline()
        assertEquals(personContent(), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, personContent()), states)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun lateSocialSuccessStaysHiddenUntilASilentRetryUsesItsCache() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profileGate = { gate.await() } }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        expireDeadline()
        assertEquals(personContent(), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
        val retryGate = CompletableDeferred<Unit>()
        social.cachedProfiles = social.profiles
        social.profileGate = { retryGate.await() }
        states.clear()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(listOf<UserProfileUiState>(personContent().copy(social = SocialBlock(profile(5, RelationshipState.FRIENDS), false, false))), states)
        retryGate.complete(Unit)
        runCurrent()
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun lateSocialNotFoundIsQuietAndNeverInsertsASection() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { profileGate = { gate.await() } }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        expireDeadline()
        assertEquals(personContent(), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, personContent()), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun lateSocialForbiddenIsReportedOnceWithoutChangingVisibleContent() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profileGate = { gate.await() } }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        expireDeadline()
        assertEquals(personContent(), viewModel.uiState.value)
        social.profileError = AppError.Forbidden
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, personContent()), states)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun latePersonSuccessPreservesBackendHeaderAndRetryImmediatelyAppliesItsCache() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.NONE)); profiles = cachedProfiles }
        val people = personRepository().apply { this.gate = { gate.await() } }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        expireDeadline()
        assertEquals(backendContent(), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
        people.cached = mapOf(5 to samplePerson(5))
        people.people = mapOf(5 to AppResult.Failure(AppError.Network))
        states.clear()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(listOf<UserProfileUiState>(personContent().copy(social = SocialBlock(profile(5), false, false))), states)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed, UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun latePersonAppliesWhenBackendIdentityDisappearsBeforeThePersonResponse() = runTest(main.dispatcher) {
        val personGate = CompletableDeferred<Unit>()
        val socialGate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.NONE)); profileGate = { socialGate.await() } }
        val people = personRepository().apply { gate = { personGate.await() } }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        expireDeadline()
        assertEquals(backendContent(), viewModel.uiState.value)
        socialGate.complete(Unit)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        personGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, backendContent(), personContent()), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun deferredPersonReplaysWhenBackendIdentityDisappearsAfterThePersonResponse() = runTest(main.dispatcher) {
        val personGate = CompletableDeferred<Unit>()
        val socialGate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.NONE)); profileGate = { socialGate.await() } }
        val people = personRepository().apply { gate = { personGate.await() } }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        expireDeadline()
        personGate.complete(Unit)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        socialGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, backendContent(), personContent()), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun noReadyIdentityMeansNoDeadlineEvenAfterTenSeconds() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { profileGate = { gate.await() } }
        val viewModel = viewModel(social)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf<UserProfileUiState>(UserProfileUiState.Loading), states)
        gate.complete(Unit)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.NotFound), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun deadlineStartsAtTheFirstReadyIdentityRatherThanScreenOpening() = runTest(main.dispatcher) {
        val personGate = CompletableDeferred<Unit>()
        val reviewsGate = CompletableDeferred<Unit>()
        val people = personRepository().apply { gate = { personGate.await() } }
        val reviews = reviewsRepository().apply { gate = { reviewsGate.await() } }
        val viewModel = viewModel(people = people, reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        personGate.complete(Unit)
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        reviewsGate.complete(Unit)
        runCurrent()
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun losingLastReadyIdentityCancelsTheDeadlineAndANewIdentityRestartsIt() = runTest(main.dispatcher) {
        val personGate = CompletableDeferred<Unit>()
        val socialGate = CompletableDeferred<Unit>()
        val reviewsGate = CompletableDeferred<Unit>()
        val people = FakePersonRepository().apply { cached = mapOf(5 to samplePerson(5)); gate = { personGate.await() } }
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)); profileGate = { socialGate.await() } }
        val reviews = FakeTeacherReviewsRepository().apply { gate = { reviewsGate.await() } }
        val viewModel = viewModel(social, people, reviews)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        advanceTimeBy(2_999)
        personGate.complete(Unit)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf<UserProfileUiState>(UserProfileUiState.Loading), states)
        socialGate.complete(Unit)
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(UserProfileUiState.Loading, viewModel.uiState.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        reviewsGate.complete(Unit)
        runCurrent()
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun retryAfterFullScreenErrorNeverResurrectsTheObsoleteFriendsPage() = runTest(main.dispatcher) {
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        assertEquals(backendContent(RelationshipState.FRIENDS), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
        social.profiles = emptyMap()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.Network), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
        val gate = CompletableDeferred<Unit>()
        people.people = mapOf(5 to AppResult.Success(samplePerson(5)))
        people.gate = { gate.await() }
        social.profiles = mapOf(5 to profile(5))
        states.clear()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(listOf<UserProfileUiState>(UserProfileUiState.Loading), states)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, backendContent()), states)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed, UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun reviewsFailureWithoutCachedReviewsShowsIdentityAndOnePartialFailure() = runTest(main.dispatcher) {
        val reviews = FakeTeacherReviewsRepository().apply { results = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun reviewsFailurePreservesCachedReviewsAndReportsOnePartialFailure() = runTest(main.dispatcher) {
        val reviews = FakeTeacherReviewsRepository().apply { cached = mapOf(5 to sampleReviews()); results = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent().copy(reviews = section()), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun backendNetworkFailureWithoutCacheKeepsPersonOnlyContentAndReportsOnce() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profileError = AppError.Network }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun forbiddenBackendResponseRemovesCachedPrivateSectionButRetainsMyITMOIdentity() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profileError = AppError.Forbidden }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun unauthorizedBackendResponseRemovesCachedPrivateSectionButRetainsMyITMOIdentity() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profileError = AppError.Unauthorized }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun simultaneousPartialFailuresProduceOnlyOneLoadEventAndPreserveCachedIdentity() = runTest(main.dispatcher) {
        val people = FakePersonRepository().apply { cached = mapOf(5 to samplePerson(5)); this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val social = FakeSocialRepository().apply { profileError = AppError.Network }
        val reviews = FakeTeacherReviewsRepository().apply { results = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(social, people, reviews)
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun visibleRetryStaysSilentWithoutLoadingOrDeadlineWhileFreshSectionsArrive() = runTest(main.dispatcher) {
        val people = personRepository()
        val reviews = FakeTeacherReviewsRepository()
        val viewModel = viewModel(people = people, reviews = reviews)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        people.people = mapOf(5 to AppResult.Success(samplePerson(5).copy(name = "Новое имя")))
        reviews.results = mapOf(5 to AppResult.Success(sampleReviews()))
        people.gate = { gate.await() }
        reviews.gate = { gate.await() }
        states.clear()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(emptyList<UserProfileUiState>(), states)
        gate.complete(Unit)
        runCurrent()
        assertEquals(personContent().copy(name = "Новое имя", reviews = section()), viewModel.uiState.value)
        assertTrue(states.all { it is UserProfileUiState.Content })
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun visibleRetryRetainsLastBackendPageUntilReplacementMyITMOIdentityArrives() = runTest(main.dispatcher) {
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
        val gate = CompletableDeferred<Unit>()
        people.people = mapOf(5 to AppResult.Success(samplePerson(5)))
        people.gate = { gate.await() }
        social.profiles = emptyMap()
        states.clear()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileUiState>(), states)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf<UserProfileUiState>(personContent()), states)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun retryCancelsPreviousRequestsWithoutAcceptingTheirResultOrEmittingTheirFailure() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val people = personRepository().apply { this.gate = { try { gate.await() } finally { cancelled = true } } }
        val viewModel = viewModel(people = people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        people.gate = {}
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(2, people.calls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(UserProfileUiState.Loading, personContent()), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun relationshipBusyStateRejectsRapidPrimaryAndSecondaryTapsUntilActionFinishes() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val base = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.INCOMING)) }
        val social = object : SocialRepository by base {
            override suspend fun acceptRequest(isu: Int): AppResult<UserProfile> { calls += 1; gate.await(); return base.acceptRequest(isu) }
        }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        viewModel.onPrimaryAction()
        viewModel.onPrimaryAction()
        viewModel.onSecondaryAction()
        runCurrent()
        assertEquals(true, viewModel.content().social?.busy)
        viewModel.onPrimaryAction()
        viewModel.onSecondaryAction()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(emptyList<String>(), base.actions)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("accept:5"), base.actions)
        assertEquals(RelationshipState.FRIENDS, viewModel.relationship())
        assertEquals(false, viewModel.content().social?.busy)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun removeConfirmationUsesMyITMOHeaderNameAndSuccessfulRemovalUpdatesRelationship() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        viewModel.onPrimaryAction()
        runCurrent()
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.ConfirmRemove(UiText.Dynamic("Персона 5"))), events)
        assertEquals(emptyList<String>(), social.actions)
        viewModel.removeFriend()
        runCurrent()
        assertEquals(listOf("remove:5"), social.actions)
        assertEquals(RelationshipState.NONE, viewModel.relationship())
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.ConfirmRemove(UiText.Dynamic("Персона 5"))), events)
    }

    @Test
    fun anEmptyBackendNameConfirmsTheRemovalAndHeadsThePageWithThePlaceholder() = runTest(main.dispatcher) {
        val unnamed = profile(5, RelationshipState.FRIENDS).let { it.copy(user = it.user.copy(name = "")) }
        val viewModel = viewModel(FakeSocialRepository().apply { profiles = mapOf(5 to unnamed) })
        val events = events(viewModel)
        runCurrent()
        val placeholder = UiText.Res(Res.string.user_name_placeholder, listOf(5))
        assertEquals(placeholder, viewModel.content().displayName)

        viewModel.onPrimaryAction()
        runCurrent()

        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.ConfirmRemove(placeholder)), events)
    }

    @Test
    fun aRetryWhileARetryRunsJoinsIt() = runTest(main.dispatcher) {
        val people = personRepository()
        val viewModel = viewModel(people = people)
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        people.gate = { gate.await() }

        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(2, people.calls)
        assertEquals(personContent(), viewModel.uiState.value)
    }

    @Test
    fun blockedRelationshipIgnoresPrimaryAndSecondaryActions() = runTest(main.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.BLOCKED)) }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        viewModel.onPrimaryAction()
        viewModel.onSecondaryAction()
        runCurrent()
        assertEquals(emptyList<String>(), social.actions)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun personOnlyProfileIgnoresAllRelationshipActions() = runTest(main.dispatcher) {
        val social = FakeSocialRepository()
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        viewModel.onPrimaryAction()
        viewModel.onSecondaryAction()
        viewModel.removeFriend()
        runCurrent()
        assertEquals(emptyList<String>(), social.actions)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun anUpdateForThisTeacherReplacesTheReviewsOnThePageAndOthersAreIgnored() = runTest(main.dispatcher) {
        val reviews = reviewsRepository()
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        runCurrent()
        val updated = teacherReviews(5, listOf(communityReview("r2")), mine = ownReview(OwnReviewStatus.PENDING))

        reviews.updates.emit(teacherReviews(6, listOf(communityReview("elsewhere"))))
        runCurrent()
        assertEquals(section(), viewModel.content().reviews)
        reviews.updates.emit(updated)
        runCurrent()

        assertEquals(section(updated), viewModel.content().reviews)
        assertEquals(2, viewModel.content().reviews?.count)
    }

    @Test
    fun theSummaryFromTheReviewsReplyReachesTheSectionAndSurvivesAVote() = runTest(main.dispatcher) {
        val summary = teacherSummary()
        val loaded = teacherReviews(5, listOf(communityReview("r1")), summary = summary)
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(5 to AppResult.Success(loaded))
            voteResult = AppResult.Success(teacherReviews(5, listOf(communityReview("r1").copy(score = 1, myVote = 1)), summary = summary))
        }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        runCurrent()
        assertEquals(summary, viewModel.content().reviews?.summary)

        viewModel.vote("r1", up = true)
        runCurrent()

        assertEquals(1, viewModel.content().reviews?.items?.single()?.myVote)
        assertEquals(summary, viewModel.content().reviews?.summary)
    }

    @Test
    fun theSummaryScalesStartFoldedToggleAndKeepTheirStateAcrossUpdates() = runTest(main.dispatcher) {
        val summary = teacherSummary()
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(5 to AppResult.Success(teacherReviews(5, listOf(communityReview("r1")), summary = summary)))
        }
        val handle = handle(5)
        val viewModel = UserProfileViewModel(handle, FakeSocialRepository(), personRepository(), reviews, currentUser(1))
        runCurrent()
        assertEquals(false, viewModel.content().reviews?.summaryExpanded)

        viewModel.toggleSummaryScales()
        runCurrent()
        assertEquals(true, viewModel.content().reviews?.summaryExpanded)
        assertEquals(true, handle.get<Boolean>(UserProfileViewModel.SUMMARY_EXPANDED))

        reviews.updates.emit(teacherReviews(5, listOf(communityReview("r1"), communityReview("r2")), summary = summary))
        runCurrent()
        assertEquals(2, viewModel.content().reviews?.items?.size)
        assertEquals(true, viewModel.content().reviews?.summaryExpanded)

        viewModel.toggleSummaryScales()
        runCurrent()
        assertEquals(false, viewModel.content().reviews?.summaryExpanded)
    }

    @Test
    fun theExpandedSummaryIsRestoredFromTheSavedState() = runTest(main.dispatcher) {
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(5 to AppResult.Success(teacherReviews(5, listOf(communityReview("r1")), summary = teacherSummary())))
        }
        val handle = SavedStateHandle(mapOf(UserScreenArgs.ISU to 5, UserProfileViewModel.SUMMARY_EXPANDED to true))
        val viewModel = UserProfileViewModel(handle, FakeSocialRepository(), personRepository(), reviews, currentUser(1))
        runCurrent()

        assertEquals(true, viewModel.content().reviews?.summaryExpanded)
    }

    @Test
    fun lateReviewsThatArriveAsAnUpdateAreAppendedWithoutASnackbar() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val reviews = reviewsRepository().apply { this.gate = { gate.await() } }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        expireDeadline()
        assertEquals(personContent(), viewModel.uiState.value)

        reviews.updates.emit(sampleReviews())
        runCurrent()

        assertEquals(personContent().copy(reviews = section()), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun aVoteKeepsEveryReviewInItsPlaceUntilARetryRanksThemAfresh() = runTest(main.dispatcher) {
        val first = teacherReviews(5, listOf(communityReview("a").copy(score = 3), communityReview("b").copy(score = 2),
            communityReview("c").copy(score = 1)))
        // Backend answers a vote with its own ranking: «c» jumps to the top.
        val ranked = teacherReviews(5, listOf(communityReview("c").copy(score = 9, myVote = 1), communityReview("a").copy(score = 3),
            communityReview("b").copy(score = 2)))
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(5 to AppResult.Success(first))
            voteResult = AppResult.Success(ranked)
        }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        runCurrent()

        viewModel.vote("c", up = true)
        runCurrent()
        assertEquals(listOf("a", "b", "c"), viewModel.content().reviews?.items?.map { it.id })
        assertEquals(9, viewModel.content().reviews?.items?.last()?.score)

        // A review that was not shown yet follows the shown ones; an update from the editor keeps the order too.
        reviews.updates.emit(teacherReviews(5, listOf(communityReview("new").copy(score = 20)) + ranked.reviews))
        runCurrent()
        assertEquals(listOf("a", "b", "c", "new"), viewModel.content().reviews?.items?.map { it.id })

        reviews.results = mapOf(5 to AppResult.Success(ranked))
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(listOf("c", "a", "b"), viewModel.content().reviews?.items?.map { it.id })
    }

    @Test
    fun anArrowVotesTheArrowOfTheCurrentVoteTakesItBackAndTheOtherArrowSwitches() = runTest(main.dispatcher) {
        val voted = teacherReviews(5, listOf(communityReview("r1").copy(score = 1, myVote = 1)))
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(5 to AppResult.Success(teacherReviews(5, listOf(communityReview("r1")))))
            voteResult = AppResult.Success(voted)
        }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        runCurrent()

        viewModel.vote("r1", up = true)
        runCurrent()
        assertEquals(1, viewModel.content().reviews?.items?.single()?.myVote)
        viewModel.vote("r1", up = true)
        runCurrent()
        viewModel.vote("r1", up = false)
        runCurrent()

        assertEquals(listOf("vote:5:r1:1", "vote:5:r1:0", "vote:5:r1:-1"), reviews.actions)
    }

    @Test
    fun aVoteInFlightMarksTheReviewBusyAndIgnoresMoreTaps() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(5 to AppResult.Success(teacherReviews(5, listOf(communityReview("r1"), communityReview("r2")))))
            voteResult = AppResult.Failure(AppError.Restricted)
            mutationGate = { gate.await() }
        }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        runCurrent()

        viewModel.vote("r1", up = false)
        runCurrent()
        assertEquals("r1", viewModel.content().reviews?.busyId)
        viewModel.vote("r1", up = true)
        viewModel.vote("r2", up = true)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("vote:5:r1:-1"), reviews.actions)
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.ActionFailed(AppError.Restricted)), events)
        assertEquals(null, viewModel.content().reviews?.busyId)
    }

    @Test
    fun deletingTheOwnReviewAsksFirstAndThenBringsBackWriting() = runTest(main.dispatcher) {
        val reviews = FakeTeacherReviewsRepository().apply {
            results = mapOf(5 to AppResult.Success(teacherReviews(5, mine = ownReview())))
            deleteResult = AppResult.Success(teacherReviews(5))
        }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        assertEquals(false, viewModel.content().reviews?.canWrite)

        viewModel.requestDeleteOwnReview()
        runCurrent()
        assertEquals(listOf<UserProfileEvent>(UserProfileEvent.ConfirmDeleteReview), events)
        assertEquals(emptyList<String>(), reviews.actions)
        viewModel.deleteOwnReview()
        runCurrent()

        assertEquals(listOf("delete:5"), reviews.actions)
        assertEquals(null, viewModel.content().reviews?.mine)
        assertEquals(true, viewModel.content().reviews?.canWrite)
    }

    private fun personRepository() = FakePersonRepository().apply { people = mapOf(5 to AppResult.Success(samplePerson(5))) }
    private fun reviewsRepository() = FakeTeacherReviewsRepository().apply { results = mapOf(5 to AppResult.Success(sampleReviews())) }
    private fun sampleReviews() = teacherReviews(5, listOf(TeacherReview("review-1", "Предмет", null, "Текст отзыва", 0, 0,
        ReviewOrigin.Reviews("Источник", "https://example.org/review"))))
    private fun personContent() = UserProfileUiState.Content(5, "Персона 5", null, null, emptyList(), null, null)
    private fun section(reviews: TeacherReviews = sampleReviews(), busyId: String? = null) = profileReviews(reviews, samplePerson(5), busyId)
    private fun TestScope.expireDeadline() { advanceTimeBy(UserProfileViewModel.PART_DEADLINE.inWholeMilliseconds); runCurrent() }
    private fun TestScope.states(viewModel: UserProfileViewModel) = mutableListOf<UserProfileUiState>().also { states ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
    }

    private fun TestScope.events(viewModel: UserProfileViewModel) = mutableListOf<UserProfileEvent>().also { events ->
        backgroundScope.launch { viewModel.events.toList(events) }
    }

    private fun viewModel(
        social: SocialRepository = FakeSocialRepository(),
        people: PersonRepository = FakePersonRepository(),
        reviews: TeacherReviewsRepository = FakeTeacherReviewsRepository(),
        currentIsu: Int = 1
    ) = UserProfileViewModel(handle(5), social, people, reviews, currentUser(currentIsu))

    private fun backendContent(relationship: RelationshipState = RelationshipState.NONE) = UserProfileUiState.Content(
        5, "Пользователь 5", null, ProfileHeadline.Group("M3100", 1),
        listOf(ProfileFact(ProfileFactKind.EDUCATION, "M3100", "ФИТиП", 1)),
        SocialBlock(profile(5, relationship), isSelf = false, busy = false), null
    )

    private fun UserProfileViewModel.content() = uiState.value as UserProfileUiState.Content
    private fun UserProfileViewModel.relationship() = content().social?.profile?.relationship
    private fun handle(isu: Int) = SavedStateHandle(mapOf(UserScreenArgs.ISU to isu))
    private fun currentUser(isu: Int) = object : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser = CurrentUser(isu, "Я", null)
    }
}
