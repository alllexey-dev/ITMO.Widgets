package dev.alllexey.itmowidgets.feature.me.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** What the social card can say about friends before or after Backend answered. */
sealed interface MeFriendsSummary {
    data object Disabled : MeFriendsSummary
    data object Loading : MeFriendsSummary
    data object Error : MeFriendsSummary
    data class Content(val friends: Int, val incomingRequests: Int) : MeFriendsSummary
}

data class MeUiState(
    val user: CurrentUser? = null,
    /** Backend's view of the same account; carries the study group. */
    val backendUser: UserSummary? = null,
    val friends: MeFriendsSummary = MeFriendsSummary.Loading,
    val signOutInProgress: Boolean = false,
    /** Sign-in to the web version goes through Backend, so it needs the ITMO.Widgets connection. */
    val webLoginAvailable: Boolean = false
)

@HiltViewModel
class MeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val socialRepository: SocialRepository,
    private val customServices: CustomServicesRepository
) : ViewModel() {

    private val signOutInProgress = MutableStateFlow(false)

    private val mutableUiState = MutableStateFlow(MeUiState())
    val uiState: StateFlow<MeUiState> = mutableUiState

    init {
        combine(
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
        }.onEach { mutableUiState.value = it }.launchIn(viewModelScope)

        // Re-enabling services from settings must fill the card without reopening the tab.
        customServices.observeEnabled()
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch { socialRepository.refresh() }
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
        friends: SocialState<List<UserProfile>>,
        requests: SocialState<FriendRequests>
    ): MeFriendsSummary = when {
        friends == SocialState.Disabled -> MeFriendsSummary.Disabled
        friends is SocialState.Content -> MeFriendsSummary.Content(
            friends = friends.value.size,
            incomingRequests = (requests as? SocialState.Content)?.value?.incoming?.size ?: 0
        )
        friends is SocialState.Error -> MeFriendsSummary.Error
        else -> MeFriendsSummary.Loading
    }
}
