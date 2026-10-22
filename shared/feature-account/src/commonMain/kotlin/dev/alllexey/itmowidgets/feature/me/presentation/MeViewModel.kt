package dev.alllexey.itmowidgets.feature.me.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.connections.DevicesOverview
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.personInitials
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Me tab's state. The social summary refreshes once, on creation (the tab's first show), and again on every
 * opt-in change; nothing in composition triggers it, so returning from a pushed screen or another tab costs nothing.
 */
class MeViewModel(
    private val sessionRepository: SessionRepository,
    private val socialRepository: SocialRepository,
    private val customServices: CustomServicesRepository,
    private val devicesOverview: DevicesOverview,
) : ViewModel() {

    private val signOutInProgress = MutableStateFlow(false)
    private val refreshes = RefreshTracker(viewModelScope)

    /** A pull's or a retry's refresh is in flight; the iOS host's pull-to-refresh reads it while its spinner shows. */
    val isRefreshing: Boolean get() = refreshes.refreshing.value

    val uiState: StateFlow<MeUiState> = combine(
        sessionRepository.state,
        socialRepository.observeCurrentUser(),
        socialRepository.observeFriends(),
        socialRepository.observeRequests(),
        combine(signOutInProgress, devicesOverview.observe(), ::Pair),
    ) { session, backendUser, friends, requests, (signingOut, devices) ->
        MeUiState(
            header = header((session as? SessionState.SignedIn)?.user, backendUser),
            friends = summarize(friends, requests),
            signOutInProgress = signingOut,
            devices = devices,
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

    /** Name and avatar from the ID token, else from Backend; the group only from Backend; the ISU from either. */
    private fun header(user: CurrentUser?, backend: UserSummary?): MeHeader {
        val name = user?.name ?: backend?.name?.takeIf { it.isNotBlank() }
        val group = backend?.primaryGroup()
        return MeHeader(
            name = name?.let(UiText::Dynamic) ?: UiText.Res(AccountStrings.me_unknown_user),
            initials = name?.let {
                personInitials(it, user?.familyName ?: backend?.familyName, user?.givenName ?: backend?.givenName)
            },
            pictureUrl = user?.pictureUrl ?: backend?.pictureUrl,
            group = group?.let {
                UiText.Res(AccountStrings.me_group_format, listOf(it.name, it.course, it.facultyShortName))
            },
            isu = user?.isu ?: backend?.isu,
        )
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
