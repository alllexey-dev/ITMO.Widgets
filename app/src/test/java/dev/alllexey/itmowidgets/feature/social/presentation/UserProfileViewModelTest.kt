package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ExternalTeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the profile and marks the signed-in user's own page`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `a person seen in a list opens with content and the network only updates it`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `a failed refresh keeps the seeded page unless access was revoked`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileEvent.LoadFailed), offlineEvents)
    }

    @Test
    fun `primary action follows the relationship state`() = runTest(mainDispatcherRule.dispatcher) {
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
        viewModel.load()
        runCurrent()
        viewModel.onSecondaryAction()
        runCurrent()
        assertEquals(listOf("send:5", "cancel:5", "reject:5"), repository.actions)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `removing a friend requires confirmation and failures restore the profile`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileEvent.ConfirmRemove("Пользователь 5"), UserProfileEvent.ActionFailed(AppError.Network)), events)
    }

    @Test
    fun `unknown users are reported as not found`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel()
        val events = events(viewModel)
        advanceUntilIdle()
        assertEquals(UserProfileUiState.Error(AppError.NotFound), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `teacher profile combines My ITMO identity and reviews without a social account`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = viewModel(people = personRepository(), reviews = reviewsRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent().copy(reviews = sampleReviews().external), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `missing My ITMO identity quietly falls back to the Backend including after retry`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val viewModel = viewModel(social)
        val events = events(viewModel)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        viewModel.retry()
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `My ITMO not found replaces its cached identity with Backend identity quietly`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val people = FakePersonRepository().apply { cached = mapOf(5 to samplePerson(5)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `lost cached identity keeps visible content until the final reviews response`() = runTest(mainDispatcherRule.dispatcher) {
        val personGate = CompletableDeferred<Unit>()
        val reviewsGate = CompletableDeferred<Unit>()
        val people = FakePersonRepository().apply { cached = mapOf(5 to samplePerson(5)); gate = { personGate.await() } }
        val reviews = FakeTeacherReviewsRepository().apply { cached = mapOf(5 to sampleReviews()); gate = { reviewsGate.await() } }
        val viewModel = viewModel(people = people, reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        val initial = personContent().copy(reviews = sampleReviews().external)
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
    fun `disabled services produce one complete person-only page without a snackbar`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `a friends profile retains its relationship and identifies the current user`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val viewModel = viewModel(social, personRepository(), currentIsu = 5)
        val events = events(viewModel)
        runCurrent()
        assertEquals(SocialBlock(profile(5, RelationshipState.FRIENDS), isSelf = true, busy = false), viewModel.content().social)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `cached own profile waits for current identity before its first content`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `My ITMO network failure keeps Backend content and reports one partial failure`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `My ITMO network error without identity can be retried from full-screen error`() = runTest(mainDispatcherRule.dispatcher) {
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(people = people)
        val events = events(viewModel)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.Network), viewModel.uiState.value)
        people.people = mapOf(5 to AppResult.Success(samplePerson(5)))
        viewModel.retry()
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `Backend network error without My ITMO identity can be retried`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { profileError = AppError.Network }
        val viewModel = viewModel(social)
        val events = events(viewModel)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.Network), viewModel.uiState.value)
        social.profileError = null
        social.profiles = mapOf(5 to profile(5))
        viewModel.retry()
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `three cached parts show content while all requests are pending`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply {
            cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profiles = cachedProfiles; profileGate = { gate.await() }
        }
        val people = personRepository().apply { cached = mapOf(5 to samplePerson(5)); this.gate = { gate.await() } }
        val reviews = reviewsRepository().apply { cached = mapOf(5 to sampleReviews()); this.gate = { gate.await() } }
        val viewModel = viewModel(social, people, reviews)
        val events = events(viewModel)
        runCurrent()
        val expected = personContent().copy(social = SocialBlock(profile(5, RelationshipState.FRIENDS), false, false), reviews = sampleReviews().external)
        assertEquals(expected, viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(expected, viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `social response before deadline is included in the only initial content`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(personContent().copy(social = SocialBlock(profile(5, RelationshipState.FRIENDS), false, false), reviews = sampleReviews().external)),
            states.filterIsInstance<UserProfileUiState.Content>())
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `person response before deadline overrides cached Backend name in first content`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `reviews arriving after the exact deadline only append the last section`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileUiState.Loading, personContent(), personContent().copy(reviews = sampleReviews().external)), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `late reviews failure keeps content and emits exactly one partial failure`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `late social success stays hidden until a silent retry uses its cache`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
        val retryGate = CompletableDeferred<Unit>()
        social.cachedProfiles = social.profiles
        social.profileGate = { retryGate.await() }
        states.clear()
        viewModel.retry()
        runCurrent()
        assertEquals(listOf(personContent().copy(social = SocialBlock(profile(5, RelationshipState.FRIENDS), false, false))), states)
        retryGate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `late social not found is quiet and never inserts a section`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `late social forbidden is reported once without changing visible content`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `late person success preserves Backend header and retry immediately applies its cache`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
        people.cached = mapOf(5 to samplePerson(5))
        people.people = mapOf(5 to AppResult.Failure(AppError.Network))
        states.clear()
        viewModel.retry()
        runCurrent()
        assertEquals(listOf(personContent().copy(social = SocialBlock(profile(5), false, false))), states)
        assertEquals(listOf(UserProfileEvent.LoadFailed, UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `late person applies when Backend identity disappears before the person response`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `deferred person replays when Backend identity disappears after the person response`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `no ready identity means no deadline even after ten seconds`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val social = FakeSocialRepository().apply { profileGate = { gate.await() } }
        val viewModel = viewModel(social)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading), states)
        gate.complete(Unit)
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.NotFound), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `deadline starts at the first ready identity rather than screen opening`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `losing last ready identity cancels the deadline and a new identity restarts it`() = runTest(mainDispatcherRule.dispatcher) {
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
        assertEquals(listOf(UserProfileUiState.Loading), states)
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
    fun `retry after full-screen error never resurrects the obsolete friends page`() = runTest(mainDispatcherRule.dispatcher) {
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        assertEquals(backendContent(RelationshipState.FRIENDS), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
        social.profiles = emptyMap()
        viewModel.retry()
        runCurrent()
        assertEquals(UserProfileUiState.Error(AppError.Network), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
        val gate = CompletableDeferred<Unit>()
        people.people = mapOf(5 to AppResult.Success(samplePerson(5)))
        people.gate = { gate.await() }
        social.profiles = mapOf(5 to profile(5))
        states.clear()
        viewModel.retry()
        runCurrent()
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading), states)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(UserProfileUiState.Loading, backendContent()), states)
        assertEquals(listOf(UserProfileEvent.LoadFailed, UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `reviews failure without cached reviews shows identity and one partial failure`() = runTest(mainDispatcherRule.dispatcher) {
        val reviews = FakeTeacherReviewsRepository().apply { results = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `reviews failure preserves cached reviews and reports one partial failure`() = runTest(mainDispatcherRule.dispatcher) {
        val reviews = FakeTeacherReviewsRepository().apply { cached = mapOf(5 to sampleReviews()); results = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(people = personRepository(), reviews = reviews)
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent().copy(reviews = sampleReviews().external), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `Backend network failure without cache keeps person-only content and reports once`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { profileError = AppError.Network }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `forbidden Backend response removes cached private section but retains My ITMO identity`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profileError = AppError.Forbidden }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `unauthorized Backend response removes cached private section but retains My ITMO identity`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { cachedProfiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)); profileError = AppError.Unauthorized }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `simultaneous partial failures produce only one load event and preserve cached identity`() = runTest(mainDispatcherRule.dispatcher) {
        val people = FakePersonRepository().apply { cached = mapOf(5 to samplePerson(5)); this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val social = FakeSocialRepository().apply { profileError = AppError.Network }
        val reviews = FakeTeacherReviewsRepository().apply { results = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val viewModel = viewModel(social, people, reviews)
        val events = events(viewModel)
        runCurrent()
        assertEquals(personContent(), viewModel.uiState.value)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `visible retry stays silent without loading or deadline while fresh sections arrive`() = runTest(mainDispatcherRule.dispatcher) {
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
        viewModel.retry()
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(emptyList<UserProfileUiState>(), states)
        gate.complete(Unit)
        runCurrent()
        assertEquals(personContent().copy(name = "Новое имя", reviews = sampleReviews().external), viewModel.uiState.value)
        assertTrue(states.all { it is UserProfileUiState.Content })
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `visible retry retains last Backend page until replacement My ITMO identity arrives`() = runTest(mainDispatcherRule.dispatcher) {
        val people = FakePersonRepository().apply { this.people = mapOf(5 to AppResult.Failure(AppError.Network)) }
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.NONE)) }
        val viewModel = viewModel(social, people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
        val gate = CompletableDeferred<Unit>()
        people.people = mapOf(5 to AppResult.Success(samplePerson(5)))
        people.gate = { gate.await() }
        social.profiles = emptyMap()
        states.clear()
        viewModel.retry()
        runCurrent()
        assertEquals(backendContent(), viewModel.uiState.value)
        assertEquals(emptyList<UserProfileUiState>(), states)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(personContent()), states)
        assertEquals(listOf(UserProfileEvent.LoadFailed), events)
    }

    @Test
    fun `retry cancels previous requests without accepting their result or emitting their failure`() = runTest(mainDispatcherRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val people = personRepository().apply { this.gate = { try { gate.await() } finally { cancelled = true } } }
        val viewModel = viewModel(people = people)
        val events = events(viewModel)
        val states = states(viewModel)
        runCurrent()
        people.gate = {}
        viewModel.retry()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(2, people.calls)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(UserProfileUiState.Loading, personContent()), states)
        assertEquals(emptyList<UserProfileEvent>(), events)
    }

    @Test
    fun `relationship busy state rejects rapid primary and secondary taps until action finishes`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `remove confirmation uses My ITMO header name and successful removal updates relationship`() = runTest(mainDispatcherRule.dispatcher) {
        val social = FakeSocialRepository().apply { profiles = mapOf(5 to profile(5, RelationshipState.FRIENDS)) }
        val viewModel = viewModel(social, personRepository())
        val events = events(viewModel)
        runCurrent()
        viewModel.onPrimaryAction()
        runCurrent()
        assertEquals(listOf(UserProfileEvent.ConfirmRemove("Персона 5")), events)
        assertEquals(emptyList<String>(), social.actions)
        viewModel.removeFriend()
        runCurrent()
        assertEquals(listOf("remove:5"), social.actions)
        assertEquals(RelationshipState.NONE, viewModel.relationship())
        assertEquals(listOf(UserProfileEvent.ConfirmRemove("Персона 5")), events)
    }

    @Test
    fun `blocked relationship ignores primary and secondary actions`() = runTest(mainDispatcherRule.dispatcher) {
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
    fun `person-only profile ignores all relationship actions`() = runTest(mainDispatcherRule.dispatcher) {
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

    private fun personRepository() = FakePersonRepository().apply { people = mapOf(5 to AppResult.Success(samplePerson(5))) }
    private fun reviewsRepository() = FakeTeacherReviewsRepository().apply { results = mapOf(5 to AppResult.Success(sampleReviews())) }
    private fun sampleReviews() = TeacherReviews(5, listOf(ExternalTeacherReview("review-1", "Предмет", null, "Источник", "https://example.org/review", "Текст отзыва")))
    private fun personContent() = UserProfileUiState.Content(5, "Персона 5", null, emptyList(), null, emptyList())
    private fun TestScope.expireDeadline() { advanceTimeBy(UserProfileViewModel.PART_DEADLINE.inWholeMilliseconds); runCurrent() }
    private fun TestScope.states(viewModel: UserProfileViewModel) = mutableListOf<UserProfileUiState>().also { states ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.toList(states) }
    }

    private fun TestScope.events(viewModel: UserProfileViewModel) = mutableListOf<UserProfileEvent>().also { events ->
        backgroundScope.launch { viewModel.eventFlow.toList(events) }
    }

    private fun viewModel(
        social: SocialRepository = FakeSocialRepository(),
        people: PersonRepository = FakePersonRepository(),
        reviews: TeacherReviewsRepository = FakeTeacherReviewsRepository(),
        currentIsu: Int = 1
    ) = UserProfileViewModel(handle(5), social, people, reviews, currentUser(currentIsu))

    private fun backendContent(relationship: RelationshipState = RelationshipState.NONE) = UserProfileUiState.Content(
        5, "Пользователь 5", null, listOf(ProfileFact(ProfileFactKind.EDUCATION, "M3100", "ФИТиП", 1)),
        SocialBlock(profile(5, relationship), isSelf = false, busy = false), emptyList()
    )

    private fun UserProfileViewModel.content() = uiState.value as UserProfileUiState.Content
    private fun UserProfileViewModel.relationship() = content().social?.profile?.relationship
    private fun handle(isu: Int) = SavedStateHandle(mapOf(UserScreenArgs.ISU to isu))
    private fun currentUser(isu: Int) = object : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser = CurrentUser(isu, "Я", null)
    }
}
