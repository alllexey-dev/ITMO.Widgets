package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.presentation.StableOrder
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
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val social: SocialRepository,
    private val people: PersonRepository,
    private val reviews: TeacherReviewsRepository,
    private val currentUserProvider: CurrentUserProvider
) : ViewModel() {

    private val isu: Int = checkNotNull(savedStateHandle.get<Int>(UserScreenArgs.ISU)) { "Profile needs an ISU" }
    private val personPart = MutableStateFlow<ProfilePart<Person>>(ProfilePart.Loading)
    private val socialPart = MutableStateFlow<ProfilePart<SocialBlock>>(ProfilePart.Loading)
    private val reviewsPart = MutableStateFlow<ProfilePart<TeacherReviews>>(ProfilePart.Loading)
    private val refreshes = RefreshTracker(viewModelScope)
    /**
     * A load of any mode is running. Unlike `refreshes.refreshing` it covers the silent load on entry too: while
     * it is set, a page on screen is not replaced by a skeleton or an error before the load has its answers.
     */
    private val loading = MutableStateFlow(true)
    /** The review whose vote or deletion is in flight; one at a time. */
    private val reviewBusy = MutableStateFlow<String?>(null)
    /** Whether the summary shows its scales; kept across configuration changes and process death. */
    private val summaryExpanded = savedStateHandle.getStateFlow(SUMMARY_EXPANDED, false)
    private val reviewsView = combine(reviewBusy, summaryExpanded, ::Pair)
    val uiState: StateFlow<UserProfileUiState> = combine(personPart, socialPart, reviewsPart, loading, reviewsView) { person, block, reviews, loading, (busy, expanded) ->
        userProfileUiState(isu, person, block, reviews, busy, expanded) to loading
    }.scan(partsState()) { previous, (next, loading) ->
        if (loading && next !is UserProfileUiState.Content && previous is UserProfileUiState.Content) previous else next
    }.stateIn(viewModelScope, SharingStarted.Eagerly, partsState())

    private val eventQueue = EventQueue<UserProfileEvent>()
    val events: Flow<UserProfileEvent> = eventQueue.events
    private var isSelf = false
    private val lateParts = mutableSetOf<Part>()
    /** The order of the shown reviews: a vote must not move a review under the finger. */
    private val reviewOrder = StableOrder()

    init {
        viewModelScope.launch {
            reviews.observeUpdates().filter { it.isu == isu }.collect {
                lateParts -= Part.REVIEWS
                reviewsPart.value = shown(it)
            }
        }
        refresh(RefreshMode.Silent)
    }

    /**
     * The load on entry is [RefreshMode.Silent]; a retry is [RefreshMode.Force], which cancels a load in flight and
     * accepts neither its answers nor its failure. Each load ranks the reviews afresh; votes and updates in between
     * keep the order.
     */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { load() }
    }

    private suspend fun load(): Unit = coroutineScope {
        reviewOrder.reset()
        val hadContent = partsState() is UserProfileUiState.Content
        loading.value = true
        val previouslyLate = lateParts.toSet()
        lateParts.clear()
        if (!hadContent) {
            if (personPart.value !is ProfilePart.Ready) personPart.value = people.cachedPerson(isu)?.let { ProfilePart.Ready(it) } ?: ProfilePart.Loading
            if (reviewsPart.value !is ProfilePart.Ready) reviewsPart.value = reviews.cachedReviews(isu)?.let(::shown) ?: ProfilePart.Loading
            if (socialPart.value !is ProfilePart.Ready) socialPart.value = ProfilePart.Loading
        }
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
        loading.value = false
        if (partsState() is UserProfileUiState.Content && attempt.hasFailures()) eventQueue.send(UserProfileEvent.LoadFailed)
    }

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
        is AppResult.Success -> { reviewsPart.value = shown(result.value); false }
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
                viewModelScope.launch { eventQueue.send(UserProfileEvent.ConfirmRemove(content.displayName)) }
            }
            RelationshipState.BLOCKED -> Unit
        }
    }

    fun onSecondaryAction() {
        if (socialBlock()?.profile?.relationship == RelationshipState.INCOMING) act { social.rejectRequest(isu) }
    }

    fun removeFriend() = act { social.removeFriend(isu) }

    /** The up or down arrow; tapping the arrow of the current vote takes it back. */
    fun vote(reviewId: String, up: Boolean) {
        if (reviewBusy.value != null) return
        val review = readyReviews()?.reviews?.firstOrNull { it.id == reviewId } ?: return
        val value = when {
            up && review.myVote > 0 || !up && review.myVote < 0 -> 0
            up -> 1
            else -> -1
        }
        actOnReview(reviewId) { reviews.vote(isu, reviewId, value) }
    }

    fun toggleSummaryScales() {
        savedStateHandle[SUMMARY_EXPANDED] = !summaryExpanded.value
    }

    fun requestDeleteOwnReview() {
        if (readyReviews()?.mine == null) return
        viewModelScope.launch { eventQueue.send(UserProfileEvent.ConfirmDeleteReview) }
    }

    fun deleteOwnReview() {
        if (reviewBusy.value != null) return
        val mine = readyReviews()?.mine ?: return
        actOnReview(mine.id) { reviews.delete(isu) }
    }

    private fun actOnReview(reviewId: String, action: suspend () -> AppResult<TeacherReviews>) {
        reviewBusy.value = reviewId
        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is AppResult.Success -> reviewsPart.value = shown(result.value)
                    is AppResult.Failure -> eventQueue.send(UserProfileEvent.ActionFailed(result.error))
                }
            } finally {
                reviewBusy.value = null
            }
        }
    }

    private fun act(action: suspend () -> AppResult<UserProfile>) {
        val current = socialBlock() ?: return
        if (current.busy) return
        socialPart.value = ProfilePart.Ready(current.copy(busy = true))
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> socialPart.value = ProfilePart.Ready(SocialBlock(result.value, isSelf, busy = false))
                is AppResult.Failure -> {
                    eventQueue.send(UserProfileEvent.ActionFailed(result.error))
                    socialBlock()?.let { socialPart.value = ProfilePart.Ready(it.copy(busy = false)) }
                }
            }
        }
    }

    private fun partsState() =
        userProfileUiState(isu, personPart.value, socialPart.value, reviewsPart.value, reviewBusy.value, summaryExpanded.value)
    private fun shown(value: TeacherReviews): ProfilePart<TeacherReviews> =
        ProfilePart.Ready(value.copy(reviews = reviewOrder.arrange(value.reviews) { it.id }))
    private fun readyReviews() = (reviewsPart.value as? ProfilePart.Ready)?.value
    private fun socialBlock() = (socialPart.value as? ProfilePart.Ready)?.value
    private fun AppError.isSocialAbsence() = this == AppError.NotFound || this == AppError.CustomServicesDisabled
    private enum class Part { PERSON, SOCIAL, REVIEWS }

    companion object {
        internal val PART_DEADLINE: Duration = 3.seconds
        internal const val SUMMARY_EXPANDED = "summary_expanded"
    }
}
