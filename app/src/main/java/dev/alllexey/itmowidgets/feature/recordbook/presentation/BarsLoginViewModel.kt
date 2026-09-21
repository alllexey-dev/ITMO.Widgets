package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.util.HttpsNavigationPolicy
import api.bars.utils.BarsAuthHelper
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class BarsLoginState(val completing: Boolean = false, val error: AppError? = null)

@HiltViewModel
class BarsLoginViewModel @Inject constructor(
    private val repository: BarsSessionRepository,
    private val auth: BarsAuthHelper,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val state = MutableStateFlow(BarsLoginState())
    val uiState = state.asStateFlow()
    private val completed = Channel<Unit>(Channel.BUFFERED)
    val events = completed.receiveAsFlow()
    private var oauthState: String
        get() = savedState.get<String>(KEY) ?: UUID.randomUUID().toString().also { savedState[KEY] = it }
        set(value) { savedState[KEY] = value }
    val loginUrl: String get() = auth.getLoginUrl(oauthState)

    fun isCallback(url: String): Boolean = auth.isCallback(url)

    /** Any https page may show during sign-in; only the exact callback completes it. */
    fun isNavigable(url: String): Boolean = HttpsNavigationPolicy.isNavigable(url)

    fun complete(url: String) {
        if (state.value.completing) return
        state.value = BarsLoginState(completing = true)
        val expectedState = oauthState
        viewModelScope.launch {
            when (val result = repository.completeLogin(url, expectedState)) {
                is AppResult.Success -> completed.send(Unit)
                is AppResult.Failure -> state.value = BarsLoginState(error = result.error)
            }
        }
    }

    fun retry() {
        if (state.value.completing) return
        oauthState = UUID.randomUUID().toString()
        state.value = BarsLoginState()
    }

    private companion object { const val KEY = "bars_oauth_state" }
}
