package dev.alllexey.itmowidgets.feature.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Merges every registered [HomeCardSource]: cards sort by kind, hidden kinds
 * drop out, and a refresh asks all sources at once. Errors never replace the
 * feed; the first one becomes a single event. Staleness follows the wall [clock]. Each feature draws its own cards
 * (`HomeCardRenderer`), so the state holds the sources' cards as they are.
 */
class HomeViewModel(
    private val sources: List<HomeCardSource>,
    preferences: HomeCardPreferences,
    private val hintStore: HomeHintStore,
    private val clock: Clock
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private var inFlight = false
    private val queue = EventQueue<HomeEvent>()
    private var loaded = false
    private var lastRefreshMillis = 0L

    val events: Flow<HomeEvent> = queue.events

    private val cards: Flow<List<HomeCard>> =
        combine(sources.map { it.observe() }) { lists -> lists.flatMap { it } }

    val uiState: StateFlow<HomeUiState> = combine(cards, preferences.observeHidden(), refreshing) { all, hidden, busy ->
        HomeUiState.Content(all.filterNot { it.kind in hidden }.sortedBy { it.kind.ordinal }, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Loading)

    /** The first show refreshes once; later visits go through [onScreenResumed]. */
    fun ensureDataLoaded() {
        if (loaded) return
        loaded = true
        refresh(RefreshMode.Silent)
    }

    /**
     * Only a [RefreshMode.Pull] shows the indicator; an automatic refresh on entry or resume stays silent behind the
     * cards. A call while a refresh runs is dropped.
     */
    fun refresh(mode: RefreshMode) {
        if (inFlight) return
        inFlight = true
        if (mode == RefreshMode.Pull) refreshing.value = true
        viewModelScope.launch {
            try {
                val failure = supervisorScope {
                    sources.map { source -> async { source.refresh() } }
                }.mapNotNull { (it.await() as? AppResult.Failure)?.error }.firstOrNull()
                lastRefreshMillis = nowMillis()
                failure?.let { queue.send(HomeEvent.RefreshFailed(it)) }
            } finally {
                inFlight = false
                refreshing.value = false
            }
        }
    }

    /** Cheap checks always; the network only when the feed is stale. */
    fun onScreenResumed() {
        viewModelScope.launch { sources.forEach { it.revalidate() } }
        if (loaded && nowMillis() - lastRefreshMillis >= STALE_AFTER_MILLIS) refresh(RefreshMode.Silent)
    }

    fun dismissHint(hint: HomeHint) {
        viewModelScope.launch { hintStore.dismiss(hint) }
    }

    fun dismissCard(kind: HomeCardKind) {
        viewModelScope.launch { sources.forEach { it.dismiss(kind) } }
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val STALE_AFTER_MILLIS = 5 * 60_000L
    }
}
