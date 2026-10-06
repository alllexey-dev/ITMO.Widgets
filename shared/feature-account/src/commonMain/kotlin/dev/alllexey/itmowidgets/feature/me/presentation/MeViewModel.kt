package dev.alllexey.itmowidgets.feature.me.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeViewModel(
    private val sessionRepository: SessionRepository,
    private val socialRepository: SocialRepository,
    private val customServices: CustomServicesRepository
) : ViewModel() {

    private val signOutInProgress = MutableStateFlow(false)
    private val refreshes = RefreshTracker(viewModelScope)

    val uiState: StateFlow<MeUiState> = combine(
        combine(sessionRepository.state, customServices.observeEnabled(), ::Pair),
        socialRepository.observeCurrentUser(),
        socialRepository.observeFriends(),
        socialRepository.observeRequests(),
        signOutInProgress
    ) { (session, servicesEnabled), backendUser, friends, requests, signingOut ->
        MeUiState(
            user = (session as? SessionState.SignedIn)?.user,
            backendUser = backendUser,
            friends = summarize(friends, requests),
            signOutInProgress = signingOut,
            webLoginAvailable = servicesEnabled
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MeUiState())

    init {
        refresh(RefreshMode.Silent)
        // Re-enabling services from settings must fill the card without reopening the tab; a refresh still in
        // flight began under the old setting, so this one replaces it instead of joining it.
        customServices.observeEnabled()
            .drop(1)
            .onEach { refresh(RefreshMode.Force) }
            .launchIn(viewModelScope)
    }

    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { socialRepository.refresh() }
    }

    fun signOut() {
        if (signOutInProgress.value) return

        signOutInProgress.value = true
        viewModelScope.launch {
            sessionRepository.signOut()
            signOutInProgress.value = false
        }
    }

    private fun summarize(
        friends: LoadState<List<UserProfile>>,
        requests: LoadState<FriendRequests>
    ): MeFriendsSummary = when {
        friends == LoadState.Disabled -> MeFriendsSummary.Disabled
        friends is LoadState.Content -> MeFriendsSummary.Content(
            friends = friends.value.size,
            incomingRequests = (requests as? LoadState.Content)?.value?.incoming?.size ?: 0
        )
        friends is LoadState.Error -> MeFriendsSummary.Error
        else -> MeFriendsSummary.Loading
    }
}
