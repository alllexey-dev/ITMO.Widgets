package dev.alllexey.itmowidgets.feature.wifi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiAccess
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Wi-Fi sheet: `ITMO-Corp`, the login and the password (hidden until the user shows it). It shows the stored
 * credentials at once and reads ISU again on every [start], since the password changes only when the user issues a
 * new one in ISU. A failed read keeps the stored credentials on screen.
 */
class WifiViewModel(private val repository: WifiRepository) : ViewModel() {
    private val state = MutableStateFlow(WifiUiState())
    val uiState: StateFlow<WifiUiState> = state.asStateFlow()
    private var request: Job? = null

    /** Shows the stored credentials and reads ISU again; a read in progress is joined. */
    fun start() {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            val cached = state.value.credentials ?: repository.cached()
            state.update {
                it.copy(credentials = cached, status = if (cached == null) WifiStatus.Loading else WifiStatus.Ready(refreshing = true))
            }
            val result = repository.refresh()
            state.update { current -> current.after(result) }
        }
    }

    fun togglePassword() = state.update { it.copy(passwordShown = !it.passwordShown) }

    private fun WifiUiState.after(result: AppResult<WifiAccess>): WifiUiState = when (result) {
        is AppResult.Success -> when (val access = result.value) {
            is WifiAccess.Ready -> copy(credentials = access.credentials, status = WifiStatus.Ready())
            WifiAccess.NoPassword -> copy(credentials = null, passwordShown = false, status = WifiStatus.NoPassword)
            // ISU could not be read: the stored credentials, if any, are still the best answer.
            WifiAccess.SignInRequired ->
                if (credentials != null) copy(status = WifiStatus.Ready()) else copy(status = WifiStatus.SignInRequired)
        }
        is AppResult.Failure ->
            if (credentials != null) copy(status = WifiStatus.Ready()) else copy(status = WifiStatus.Failed(result.error))
    }
}
