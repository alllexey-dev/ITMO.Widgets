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
 * The Wi-Fi sheet: `ITMO-Corp`, the login and the password (hidden until the user shows it). Every [start] reads ISU
 * afresh and shows loading until it answers; no earlier answer stays on screen, so a failed read is an error, never an
 * old password.
 */
class WifiViewModel(private val repository: WifiRepository) : ViewModel() {
    private val state = MutableStateFlow(WifiUiState())
    val uiState: StateFlow<WifiUiState> = state.asStateFlow()
    private var request: Job? = null

    /** Reads ISU again; a read in progress is joined. */
    fun start() {
        if (request?.isActive == true) return
        state.update { it.copy(status = WifiStatus.Loading) }
        request = viewModelScope.launch {
            val status = repository.load().toStatus()
            state.update { it.copy(status = status) }
        }
    }

    fun togglePassword() = state.update { it.copy(passwordShown = !it.passwordShown) }

    private fun AppResult<WifiAccess>.toStatus(): WifiStatus = when (this) {
        is AppResult.Success -> when (val access = value) {
            is WifiAccess.Ready -> WifiStatus.Ready(access.credentials)
            WifiAccess.NoPassword -> WifiStatus.NoPassword
            WifiAccess.SignInRequired -> WifiStatus.SignInRequired
        }
        is AppResult.Failure -> WifiStatus.Failed(error)
    }
}
