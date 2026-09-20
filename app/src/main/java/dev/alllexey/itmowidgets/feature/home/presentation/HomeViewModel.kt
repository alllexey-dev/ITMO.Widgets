package dev.alllexey.itmowidgets.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Merges every registered [HomeCardSource]: cards sort by kind, hidden kinds
 * drop out, and a refresh asks all sources at once. Errors never replace the
 * feed; the first one becomes a single event.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards HomeCardSource>,
    preferences: HomeCardPreferences,
    private val hintStore: HomeHintStore,
    @param:WallClock private val clock: Clock
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    private var loaded = false
    private var lastRefreshMillis = 0L

    val events: Flow<HomeEvent> = eventChannel.receiveAsFlow()

    private val cards: Flow<List<HomeCard>> =
        combine(sources.map { it.observe() }) { lists -> lists.flatMap { it } }

    val uiState: StateFlow<HomeUiState> = combine(cards, preferences.observeHidden(), refreshing) { all, hidden, busy ->
        HomeUiState.Content(all.filterNot { it.kind in hidden }.sortedBy { it.kind.ordinal }, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Loading)

    /** The first show refreshes once; later visits go through [onScreenResumed]. */
    fun ensureDataLoaded() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val failure = supervisorScope {
                    sources.map { source -> async { source.refresh() } }
                }.mapNotNull { (it.await() as? AppResult.Failure)?.error }.firstOrNull()
                lastRefreshMillis = clock.millis()
                failure?.let { eventChannel.send(HomeEvent.RefreshFailed(it)) }
            } finally {
                refreshing.value = false
            }
        }
    }

    /** Cheap checks always; the network only when the feed is stale. */
    fun onScreenResumed() {
        viewModelScope.launch { sources.forEach { it.revalidate() } }
        if (loaded && clock.millis() - lastRefreshMillis >= STALE_AFTER_MILLIS) refresh()
    }

    fun dismissHint(hint: HomeHint) {
        viewModelScope.launch { hintStore.dismiss(hint) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val STALE_AFTER_MILLIS = 5 * 60_000L
    }
}
