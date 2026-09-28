package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

sealed interface UserProfileEvent {
    data class ActionFailed(val error: AppError) : UserProfileEvent
    data class ConfirmRemove(val name: String) : UserProfileEvent
    data object LoadFailed : UserProfileEvent
}

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val social: SocialRepository,
    private val people: PersonRepository,
    private val reviews: TeacherReviewsRepository,
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    val isu: Int = checkNotNull(savedStateHandle.get<Int>(UserScreenArgs.ISU)) { "Profile needs an ISU" }
    private val personPart = MutableStateFlow<ProfilePart<Person>>(ProfilePart.Loading)
    private val socialPart = MutableStateFlow<ProfilePart<SocialBlock>>(ProfilePart.Loading)
    private val reviewsPart = MutableStateFlow<ProfilePart<TeacherReviews>>(ProfilePart.Loading)
    private val inFlight = MutableStateFlow(true)
    val uiState: StateFlow<UserProfileUiState> = combine(personPart, socialPart, reviewsPart, inFlight) { person, block, reviews, loading ->
        userProfileUiState(isu, person, block, reviews) to loading
    }.scan(partsState()) { previous, (next, loading) ->
        if (loading && next !is UserProfileUiState.Content && previous is UserProfileUiState.Content) previous else next
    }.stateIn(viewModelScope, SharingStarted.Eagerly, partsState())

    private val events = Channel<UserProfileEvent>(Channel.BUFFERED)
    val eventFlow: Flow<UserProfileEvent> = events.receiveAsFlow()
    private var loadJob: Job? = null
    private var isSelf = false
    private val lateParts = mutableSetOf<Part>()

    init { load() }

    fun load() {
        loadJob?.cancel()
        val hadContent = partsState() is UserProfileUiState.Content
        inFlight.value = true
        val previouslyLate = lateParts.toSet()
        lateParts.clear()
        if (!hadContent) {
            if (personPart.value !is ProfilePart.Ready) personPart.value = people.cachedPerson(isu)?.let { ProfilePart.Ready(it) } ?: ProfilePart.Loading
            if (reviewsPart.value !is ProfilePart.Ready) reviewsPart.value = reviews.cachedReviews(isu)?.let { ProfilePart.Ready(it) } ?: ProfilePart.Loading
            if (socialPart.value !is ProfilePart.Ready) socialPart.value = ProfilePart.Loading
        }
        loadJob = viewModelScope.launch {
            isSelf = currentUserProvider.getCurrentUser()?.isu == isu
            currentCoroutineContext().ensureActive()
            if (socialPart.value is ProfilePart.Loading || Part.SOCIAL in previouslyLate) {
                social.cachedProfile(isu)?.let { socialPart.value = ProfilePart.Ready(SocialBlock(it, isSelf, busy = false)) }
            }
            if (Part.PERSON in previouslyLate) {
                people.cachedPerson(isu)?.let { personPart.value = ProfilePart.Ready(it) }
            }
            val attempt = LoadAttempt()
            val deadline = if (hadContent) null else launch {
                combine(personPart, socialPart, uiState) { person, block, state ->
                    (person is ProfilePart.Ready || block is ProfilePart.Ready) && state is UserProfileUiState.Loading
                }.distinctUntilChanged().collectLatest { waiting ->
                    if (waiting) {
                        delay(PART_DEADLINE)
                        expireLoadingParts()
                    }
                }
            }
            listOf(
                launch { val result = people.person(isu); currentCoroutineContext().ensureActive(); attempt.onPerson(result) },
                launch { val result = social.profile(isu); currentCoroutineContext().ensureActive(); attempt.onSocial(result) },
                launch { val result = reviews.reviews(isu); currentCoroutineContext().ensureActive(); attempt.onReviews(result) }
            ).joinAll()
            deadline?.cancel()
            inFlight.value = false
            if (partsState() is UserProfileUiState.Content && attempt.hasFailures()) events.send(UserProfileEvent.LoadFailed)
        }
    }

    fun retry() = load()

    private fun expireLoadingParts() {
        if (personPart.value !is ProfilePart.Ready && socialPart.value !is ProfilePart.Ready) return
        if (partsState() !is UserProfileUiState.Loading || uiState.value !is UserProfileUiState.Loading) return
        if (personPart.value is ProfilePart.Loading) { lateParts += Part.PERSON; personPart.value = ProfilePart.Absent }
        if (socialPart.value is ProfilePart.Loading) { lateParts += Part.SOCIAL; socialPart.value = ProfilePart.Absent }
        if (reviewsPart.value is ProfilePart.Loading) { lateParts += Part.REVIEWS; reviewsPart.value = ProfilePart.Absent }
    }

    /** Deferred identity sections must not move a visible page; they become usable if its last identity disappears. */
    private inner class LoadAttempt {
        private var failed = false
        private var deferredPerson: AppResult<Person>? = null
        private var deferredSocial: AppResult<UserProfile>? = null

        fun onPerson(result: AppResult<Person>) {
            if (Part.PERSON in lateParts && partsState() is UserProfileUiState.Content) deferredPerson = result
            else { failed = applyPerson(result) || failed; releaseDeferred() }
        }

        fun onSocial(result: AppResult<UserProfile>) {
            if (Part.SOCIAL in lateParts && partsState() is UserProfileUiState.Content) deferredSocial = result
            else { failed = applySocial(result) || failed; releaseDeferred() }
        }

        fun onReviews(result: AppResult<TeacherReviews>) {
            failed = applyReviews(result) || failed
            releaseDeferred()
        }

        private fun releaseDeferred() {
            if (lateParts.isEmpty() || partsState() is UserProfileUiState.Content) return
            lateParts.clear()
            deferredPerson?.let { failed = applyPerson(it) || failed }
            deferredSocial?.let { failed = applySocial(it) || failed }
            deferredPerson = null
            deferredSocial = null
        }

        fun hasFailures(): Boolean = failed || deferredPerson?.let { it !is AppResult.Failure || it.error != AppError.NotFound } == true ||
            deferredSocial?.let { it !is AppResult.Failure || !it.error.isSocialAbsence() } == true
    }

    private fun applyPerson(result: AppResult<Person>): Boolean = when (result) {
        is AppResult.Success -> { personPart.value = ProfilePart.Ready(result.value); false }
        is AppResult.Failure -> {
            if (result.error == AppError.NotFound || personPart.value !is ProfilePart.Ready) personPart.value = ProfilePart.Failed(result.error)
            result.error != AppError.NotFound
        }
    }

    private fun applySocial(result: AppResult<UserProfile>): Boolean = when (result) {
        is AppResult.Success -> { socialPart.value = ProfilePart.Ready(SocialBlock(result.value, isSelf, busy = false)); false }
        is AppResult.Failure -> {
            when {
                result.error.isSocialAbsence() -> socialPart.value = ProfilePart.Absent
                result.error == AppError.Forbidden || result.error == AppError.Unauthorized || socialPart.value !is ProfilePart.Ready ->
                    socialPart.value = ProfilePart.Failed(result.error)
            }
            !result.error.isSocialAbsence()
        }
    }

    private fun applyReviews(result: AppResult<TeacherReviews>): Boolean = when (result) {
        is AppResult.Success -> { reviewsPart.value = ProfilePart.Ready(result.value); false }
        is AppResult.Failure -> {
            if (result.error == AppError.CustomServicesDisabled || reviewsPart.value !is ProfilePart.Ready) reviewsPart.value = ProfilePart.Absent
            result.error != AppError.CustomServicesDisabled
        }
    }

    fun onPrimaryAction() {
        val block = socialBlock() ?: return
        if (block.busy) return
        when (block.profile.relationship) {
            RelationshipState.NONE -> act { social.sendRequest(isu) }
            RelationshipState.OUTGOING -> act { social.cancelRequest(isu) }
            RelationshipState.INCOMING -> act { social.acceptRequest(isu) }
            RelationshipState.FRIENDS -> (uiState.value as? UserProfileUiState.Content)?.let { content ->
                viewModelScope.launch { events.send(UserProfileEvent.ConfirmRemove(content.name)) }
            }
            RelationshipState.BLOCKED -> Unit
        }
    }

    fun onSecondaryAction() {
        if (socialBlock()?.profile?.relationship == RelationshipState.INCOMING) act { social.rejectRequest(isu) }
    }

    fun removeFriend() = act { social.removeFriend(isu) }

    private fun act(action: suspend () -> AppResult<UserProfile>) {
        val current = socialBlock() ?: return
        if (current.busy) return
        socialPart.value = ProfilePart.Ready(current.copy(busy = true))
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> socialPart.value = ProfilePart.Ready(SocialBlock(result.value, isSelf, busy = false))
                is AppResult.Failure -> {
                    events.send(UserProfileEvent.ActionFailed(result.error))
                    socialBlock()?.let { socialPart.value = ProfilePart.Ready(it.copy(busy = false)) }
                }
            }
        }
    }

    private fun partsState() = userProfileUiState(isu, personPart.value, socialPart.value, reviewsPart.value)
    private fun socialBlock() = (socialPart.value as? ProfilePart.Ready)?.value
    private fun AppError.isSocialAbsence() = this == AppError.NotFound || this == AppError.CustomServicesDisabled
    private enum class Part { PERSON, SOCIAL, REVIEWS }

    companion object {
        internal val PART_DEADLINE: Duration = 3.seconds
    }
}
