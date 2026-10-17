package dev.alllexey.itmowidgets.feature.settings.presentation.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.recordbook.BarsLoginPrompt
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.storage.MarkSourcePreferences
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.domain.SharingVisibility
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsPlurals
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * «Подключение и данные» (DECISIONS.md, Screens). The opt-in with what it gives, the audiences of «Кто видит» while
 * connected, and the state of BARS. Turning the connection on reaches here only after the consent sheet; the screen
 * opens it. Connecting reloads the audiences from Backend, masked for at least [MIN_PRIVACY_LOADING_MS] so a fast
 * answer does not flash; disconnecting forgets them at once.
 */
class ConnectionViewModel(
    private val customServices: CustomServicesRepository,
    private val settings: SettingsRepository,
    private val social: SocialRepository,
    markSources: MarkSourcePreferences,
    private val widgetRefreshRequester: WidgetRefreshRequester,
) : ViewModel() {

    private val changeable = MutableStateFlow(true)
    private val privacyRefreshing = MutableStateFlow(false)
    private val privacyUpdating = MutableStateFlow(false)
    private val privacyRefreshMutex = Mutex()
    private val eventQueue = EventQueue<ConnectionEvent>()
    val events: Flow<ConnectionEvent> = eventQueue.events

    private val bars: Flow<BarsConnection> = combine(
        markSources.observeBarsMarksEnabled(),
        markSources.observeBarsLoginPrompt(),
    ) { enabled, prompt ->
        when {
            prompt != BarsLoginPrompt.NONE -> BarsConnection.EXPIRED
            enabled != null -> BarsConnection.CONNECTED
            else -> BarsConnection.NOT_CONNECTED
        }
    }

    private val privacy: Flow<SharingSettingsState> = combine(
        settings.observeSharingSettings(),
        privacyRefreshing,
        privacyUpdating,
    ) { sharing, refreshing, updating ->
        when {
            refreshing -> SharingSettingsState.Loading
            updating && sharing is SharingSettingsState.Content -> sharing.copy(updating = true)
            else -> sharing
        }
    }

    private val friends: Flow<Pair<UiText?, Int>> = combine(social.observeFriends(), social.observeRequests()) { list, requests ->
        val friendCount = (list as? LoadState.Content)?.value?.size
        val incoming = (requests as? LoadState.Content)?.value?.incoming?.size ?: 0
        val text = friendCount?.let { count ->
            if (incoming > 0) {
                UiText.Joined(
                    listOf(
                        UiText.Plural(SettingsPlurals.connection_friends_count, count, listOf(count)),
                        UiText.Plural(SettingsPlurals.connection_friend_requests_count, incoming, listOf(incoming)),
                    ),
                    ", ",
                )
            } else {
                UiText.Plural(SettingsPlurals.connection_friends_count, count, listOf(count))
            }
        }
        text to incoming
    }

    val uiState: StateFlow<ConnectionUiState> = combine(
        customServices.observeEnabled(),
        changeable,
        privacy,
        bars,
        friends,
    ) { connected, canChange, sharing, barsState, (friendsText, requests) ->
        ConnectionUiState(
            loaded = true,
            connected = connected,
            changeable = canChange,
            friends = friendsText.takeIf { connected },
            friendRequests = if (connected) requests else 0,
            privacy = if (connected) sharing.toPrivacy() else ConnectionPrivacy.OnlyYou,
            bars = barsState,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionUiState())

    init {
        viewModelScope.launch { changeable.value = customServices.isChangeable() }
        viewModelScope.launch {
            customServices.observeEnabled().distinctUntilChanged().collectLatest { enabled ->
                if (enabled) {
                    refreshPrivacy()
                    social.refresh()
                } else {
                    privacyRefreshing.value = false
                    settings.disableSharingSettings()
                }
            }
        }
    }

    /** The switch: on only after the consent sheet's `Подключить`, off at once. */
    fun setConnected(enabled: Boolean) {
        viewModelScope.launch {
            if (!customServices.isChangeable()) {
                eventQueue.send(ConnectionEvent.ShowError(AppError.DemoUnavailable))
                return@launch
            }
            try {
                customServices.setEnabled(enabled)
                widgetRefreshRequester.refreshAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventQueue.send(ConnectionEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    /** A choice of «Кто видит»; a stale menu (not connected, still loading, already saving) changes nothing. */
    fun setAudience(topic: PrivacyTopic, visibility: SharingVisibility) {
        val audiences = uiState.value.privacy as? ConnectionPrivacy.Audiences ?: return
        if (audiences.updating || privacyUpdating.value) return
        val current = when (topic) {
            PrivacyTopic.SCHEDULE -> audiences.schedule
            PrivacyTopic.SPORT -> audiences.sport
            PrivacyTopic.FRIENDS -> audiences.friends
        }
        if (current == visibility) return
        privacyUpdating.value = true
        viewModelScope.launch {
            try {
                val result = when (topic) {
                    PrivacyTopic.SCHEDULE -> settings.setScheduleVisibility(visibility)
                    PrivacyTopic.SPORT -> settings.setSportVisibility(visibility)
                    PrivacyTopic.FRIENDS -> settings.setFriendsVisibility(visibility)
                }
                if (result is AppResult.Failure) eventQueue.send(ConnectionEvent.ShowError(result.error))
            } finally {
                privacyUpdating.value = false
            }
        }
    }

    /** `Повторить` of «Кто видит» after Backend failed. */
    fun retryPrivacy() {
        viewModelScope.launch { refreshPrivacy() }
    }

    /** `Удалить аккаунт ITMO.Widgets`: the deletion page of the site, shown with the connection off too. */
    fun deleteAccount() {
        viewModelScope.launch { eventQueue.send(ConnectionEvent.OpenWebPage(DELETE_ACCOUNT_PATH)) }
    }

    private suspend fun refreshPrivacy() {
        if (!privacyRefreshMutex.tryLock()) return
        privacyRefreshing.value = true
        try {
            coroutineScope {
                // Run together: a slow network response must not pay an extra 300 ms.
                launch { delay(MIN_PRIVACY_LOADING_MS) }
                settings.refreshSharingSettings()
            }
        } finally {
            privacyRefreshing.value = false
            privacyRefreshMutex.unlock()
        }
    }

    private fun SharingSettingsState.toPrivacy(): ConnectionPrivacy = when (this) {
        SharingSettingsState.Disabled, SharingSettingsState.Loading -> ConnectionPrivacy.Loading
        SharingSettingsState.Error -> ConnectionPrivacy.Failed
        is SharingSettingsState.Content -> ConnectionPrivacy.Audiences(
            schedule = this.settings.scheduleVisibility,
            sport = this.settings.sportVisibility,
            friends = this.settings.friendsVisibility,
            updating = updating,
        )
    }

    companion object {
        const val DELETE_ACCOUNT_PATH = "/delete-account"
        private const val MIN_PRIVACY_LOADING_MS = 300L
    }
}
