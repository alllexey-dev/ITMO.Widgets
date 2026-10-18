package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherNowGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherNowLesson
import kotlinx.coroutines.async
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import dev.alllexey.itmowidgets.feature.social.domain.model.Person
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
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class UserProfileViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val social: SocialRepository,
    private val people: PersonRepository,
    private val reviews: TeacherReviewsRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val lessons: TeacherLessonsGateway,
    /** MyITMO's teacher timetable; null until the app binds one, and the card shows no `Сейчас ведёт` then. */
    private val teacherNow: TeacherNowGateway? = null,
) : ViewModel() {

    private val isu: Int = checkNotNull(savedStateHandle.get<Int>(UserScreenArgs.ISU)) { "Profile needs an ISU" }
    private val personPart = MutableStateFlow<ProfilePart<Person>>(ProfilePart.Loading)
    private val socialPart = MutableStateFlow<ProfilePart<SocialBlock>>(ProfilePart.Loading)
    private val reviewsPart = MutableStateFlow<ProfilePart<TeacherReviews>>(ProfilePart.Loading)
    /** What the viewer's own lessons say the person teaches them; asked only for someone My ITMO lists a position for. */
    private val teachesPart = MutableStateFlow<ProfilePart<TeacherLessons>>(ProfilePart.Loading)
    private val absence = MutableStateFlow<SocialAbsence?>(null)
    /** The teacher's lesson now or next today; it settles together with [teachesPart]. */
    private val nowLesson = MutableStateFlow<TeacherNowLesson?>(null)
    private val refreshes = RefreshTracker(viewModelScope)

    /** A pull's or a retry's refresh is in flight; the iOS host's pull-to-refresh reads it while its spinner shows. */
    val isRefreshing: Boolean get() = refreshes.refreshing.value

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
    private val teacher = combine(teachesPart, nowLesson, ::Pair)
    private val parts = combine(personPart, socialPart, reviewsPart, teacher, absence) { person, block, reviews, (teaches, now), absence ->
        Parts(person, block, reviews, teaches, absence, now)
    }
    val uiState: StateFlow<UserProfileUiState> = combine(parts, loading, reviewsView) { parts, loading, (busy, expanded) ->
        parts.state(busy, expanded) to loading
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
            // A cached person who teaches nothing needs no lessons, so the cached page shows at once.
            if (teachesPart.value !is ProfilePart.Ready) {
                val cached = (personPart.value as? ProfilePart.Ready)?.value
                teachesPart.value = if (cached != null && cached.positions.isEmpty()) ProfilePart.Absent else ProfilePart.Loading
            }
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
            launch {
                val result = people.person(isu)
                currentCoroutineContext().ensureActive()
                attempt.onPerson(result)
                val teacher = result is AppResult.Success && result.value.positions.isNotEmpty() && !isSelf
                val (taught, now) = if (teacher) teacherFacts() else null to null
                currentCoroutineContext().ensureActive()
                attempt.onTeaches(taught, now)
            },
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
        if (teachesPart.value is ProfilePart.Loading) { lateParts += Part.TEACHES; teachesPart.value = ProfilePart.Absent }
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

        /** A late answer would move the blocks and the buttons of a shown page, so it waits for the next load. */
        fun onTeaches(result: TeacherLessons?, now: TeacherNowLesson?) {
            if (Part.TEACHES in lateParts && partsState() is UserProfileUiState.Content) return
            lateParts -= Part.TEACHES
            nowLesson.value = now
            teachesPart.value = result?.let { ProfilePart.Ready(it) } ?: ProfilePart.Absent
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
        is AppResult.Success -> {
            absence.value = null
            socialPart.value = ProfilePart.Ready(SocialBlock(result.value, isSelf, busy = false))
            false
        }
        is AppResult.Failure -> {
            when {
                result.error.isSocialAbsence() -> {
                    absence.value = if (result.error == AppError.NotFound) SocialAbsence.NOT_REGISTERED else SocialAbsence.NOT_CONNECTED
                    socialPart.value = ProfilePart.Absent
                }
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

    /**
     * A friendship button of the header. Writing a review and the invitation leave the screen, so the host handles
     * them; a removal asks first ([requestRemoveFriend]).
     */
    fun onAction(action: CardAction) {
        if (socialBlock()?.busy == true) return
        if (action !in (uiState.value as? UserProfileUiState.Content)?.actions.orEmpty()) return
        when (action) {
            CardAction.ADD_FRIEND -> act { social.sendRequest(isu) }
            CardAction.CANCEL_REQUEST -> act { social.cancelRequest(isu) }
            CardAction.ACCEPT_REQUEST -> act { social.acceptRequest(isu) }
            CardAction.REJECT_REQUEST -> act { social.rejectRequest(isu) }
            CardAction.WRITE_REVIEW, CardAction.INVITE -> Unit
        }
    }

    /** `Удалить из друзей`: the host confirms, then calls [removeFriend]. */
    fun requestRemoveFriend() {
        if (socialBlock()?.profile?.relationship != RelationshipState.FRIENDS) return
        val content = uiState.value as? UserProfileUiState.Content ?: return
        viewModelScope.launch { eventQueue.send(UserProfileEvent.ConfirmRemove(content.displayName)) }
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
        Parts(personPart.value, socialPart.value, reviewsPart.value, teachesPart.value, absence.value, nowLesson.value)
            .state(reviewBusy.value, summaryExpanded.value)

    /** The lessons with the viewer and the lesson now, asked at once; a failed lesson now is simply not shown. */
    private suspend fun teacherFacts(): Pair<TeacherLessons?, TeacherNowLesson?> = coroutineScope {
        val now = async { (teacherNow?.lessonOf(isu) as? AppResult.Success)?.value }
        taughtBy() to now.await()
    }

    /** The subjects the person teaches the viewer: the first positive answer, else all weeks; null when every week failed. */
    private suspend fun taughtBy(): TeacherLessons? {
        val last = lessons.taughtBy(isu)
            .transformWhile { result ->
                emit(result)
                !(result is AppResult.Success && result.value.subjects.isNotEmpty())
            }
            .lastOrNull()
        return when (last) {
            is AppResult.Success -> last.value
            is AppResult.Failure -> null
            null -> TeacherLessons(emptySet(), emptyList())
        }
    }

    private inner class Parts(
        val person: ProfilePart<Person>,
        val social: ProfilePart<SocialBlock>,
        val reviews: ProfilePart<TeacherReviews>,
        val teaches: ProfilePart<TeacherLessons>,
        val absence: SocialAbsence?,
        val now: TeacherNowLesson?,
    ) {
        fun state(busyId: String?, expanded: Boolean) =
            userProfileUiState(isu, person, social, reviews, teaches, absence, now, busyId, expanded)
    }
    private fun shown(value: TeacherReviews): ProfilePart<TeacherReviews> =
        ProfilePart.Ready(value.copy(reviews = reviewOrder.arrange(value.reviews) { it.id }))
    private fun readyReviews() = (reviewsPart.value as? ProfilePart.Ready)?.value
    private fun socialBlock() = (socialPart.value as? ProfilePart.Ready)?.value
    private fun AppError.isSocialAbsence() = this == AppError.NotFound || this == AppError.CustomServicesDisabled
    private enum class Part { PERSON, SOCIAL, REVIEWS, TEACHES }

    companion object {
        internal val PART_DEADLINE: Duration = 3.seconds
        internal const val SUMMARY_EXPANDED = "summary_expanded"
    }
}
