package dev.alllexey.itmowidgets.feature.social.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.social.SocialRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class UserFriendsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SocialRepository
) : ViewModel() {
    private val isu: Int = checkNotNull(savedStateHandle[UserScreenArgs.ISU])
    val name: String = savedStateHandle[UserScreenArgs.NAME] ?: ""
    private val refreshes = RefreshTracker(viewModelScope)
    private val eventQueue = EventQueue<UserFriendsEvent>()

    /** The last answer for this person renders at once; the network only updates the list. */
    private val friends = MutableStateFlow(cachedFriends() ?: LoadState.Loading)

    val uiState: StateFlow<UserFriendsUiState> = combine(friends, refreshes.refreshing, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toUiState(friends.value, refreshing = false))

    val events: Flow<UserFriendsEvent> = eventQueue.events

    init {
        refresh(RefreshMode.Silent)
    }

    /** A pull or a retry shows the indicator; the load on entry stays silent behind the cached list. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { load() }
    }

    private suspend fun load() {
        if (friends.value !is LoadState.Content) cachedFriends()?.let { friends.value = it }
        when (val result = repository.userFriends(isu)) {
            is AppResult.Success -> friends.value = LoadState.Content(result.value)
            is AppResult.Failure -> {
                // A revoked permission or session must discard content, not keep a private stale list.
                if (friends.value is LoadState.Content && result.error !in REVOKING) {
                    eventQueue.send(UserFriendsEvent.RefreshFailed(result.error))
                } else {
                    friends.value = LoadState.Error(result.error)
                }
            }
        }
    }

    private fun cachedFriends(): LoadState<List<UserProfile>>? = repository.cachedUserFriends(isu)?.let { LoadState.Content(it) }

    private fun toUiState(friends: LoadState<List<UserProfile>>, refreshing: Boolean): UserFriendsUiState = when (friends) {
        LoadState.Loading -> UserFriendsUiState.Loading
        LoadState.Disabled -> UserFriendsUiState.Disabled
        // A retry over a failure shows progress, not the failure it may replace.
        is LoadState.Error -> if (refreshing) UserFriendsUiState.Loading else when (friends.error) {
            AppError.Forbidden -> UserFriendsUiState.Hidden
            AppError.CustomServicesDisabled -> UserFriendsUiState.Disabled
            else -> UserFriendsUiState.Error(friends.error)
        }
        is LoadState.Content -> UserFriendsUiState.Content(friends.value.toItems(), refreshing)
    }

    private fun List<UserProfile>.toItems(): List<UserListItem> = map { profile ->
        UserListItem.User(UserRowUi(
            isu = profile.isu,
            name = profile.user.name,
            pictureUrl = profile.user.pictureUrl,
            subtitle = profile.user.subtitleText(),
            status = profile.relationship.statusText()
        ))
    }

    private companion object {
        val REVOKING = setOf(AppError.Forbidden, AppError.Unauthorized, AppError.CustomServicesDisabled, AppError.NotFound)
    }
}
