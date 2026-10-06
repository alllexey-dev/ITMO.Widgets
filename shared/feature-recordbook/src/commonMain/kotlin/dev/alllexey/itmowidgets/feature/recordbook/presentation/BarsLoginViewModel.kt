package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import javax.inject.Inject
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BarsLoginViewModel @Inject constructor(
    private val repository: BarsSessionRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val state = MutableStateFlow(BarsLoginUiState())
    val uiState: StateFlow<BarsLoginUiState> = state.asStateFlow()
    private val completed = EventQueue<Unit>()

    /** Emits once the session is stored: the page closes with a result. */
    val events: Flow<Unit> = completed.events
    private var oauthState: String
        get() = savedState.get<String>(KEY) ?: Uuid.random().toString().also { savedState[KEY] = it }
        set(value) { savedState[KEY] = value }
    val loginUrl: String get() = repository.loginUrl(oauthState)

    fun isCallback(url: String): Boolean = repository.isCallback(url)

    /** Any https page may show during sign-in; only the exact callback completes it. */
    fun isNavigable(url: String): Boolean = HttpsNavigationPolicy.isNavigable(url)

    fun complete(url: String) {
        if (state.value.completing) return
        state.value = BarsLoginUiState(completing = true)
        val expectedState = oauthState
        viewModelScope.launch {
            when (val result = repository.completeLogin(url, expectedState)) {
                is AppResult.Success -> completed.send(Unit)
                is AppResult.Failure -> state.value = BarsLoginUiState(error = result.error)
            }
        }
    }

    fun retry() {
        if (state.value.completing) return
        oauthState = Uuid.random().toString()
        state.value = BarsLoginUiState()
    }

    private companion object { const val KEY = "bars_oauth_state" }
}
