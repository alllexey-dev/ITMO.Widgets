package dev.alllexey.itmowidgets.feature.qr.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.WallClock
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface QrCodeUiState {
    data object Loading : QrCodeUiState
    data object Empty : QrCodeUiState
    data class Error(val error: AppError) : QrCodeUiState
    data class Content(val code: QrCodeSnapshot, val refreshing: Boolean = false) : QrCodeUiState
}

@HiltViewModel
class QrCodeViewModel @Inject constructor(
    private val repository: QrCodeRepository,
    @param:WallClock private val clock: Clock
) : ViewModel() {
    private val state = MutableStateFlow<QrCodeUiState>(QrCodeUiState.Loading)
    val uiState = state.asStateFlow()
    private val errors = Channel<AppError>(Channel.BUFFERED)
    val refreshErrors = errors.receiveAsFlow()
    private var request: Job? = null
    private var expiration: Job? = null
    private var visible = false

    fun start() {
        visible = true
        val current = state.value as? QrCodeUiState.Content
        if (current != null && current.code.expiresAtMillis <= clock.millis()) state.value = QrCodeUiState.Loading
        refresh(force = false)
    }

    fun stop() {
        visible = false
        request?.cancel()
        request = null
        expiration?.cancel()
        expiration = null
    }

    fun refresh(force: Boolean = true) {
        if (!visible || request?.isActive == true) return
        val previous = (state.value as? QrCodeUiState.Content)?.takeIf { it.code.expiresAtMillis > clock.millis() }
        if (previous != null) state.value = previous.copy(refreshing = true)
        request = viewModelScope.launch {
            if (previous == null) {
                // A fresh screen shows the cached pass at once; only an absent or expired cache waits.
                val cached = repository.currentQr()?.takeIf { it.expiresAtMillis > clock.millis() && it.hex.isNotBlank() }
                state.value = cached?.let { QrCodeUiState.Content(it, refreshing = true) } ?: QrCodeUiState.Loading
            }
            val result = repository.refreshQrHex(force)
            val code = repository.currentQr()?.takeIf { it.expiresAtMillis > clock.millis() && it.hex.isNotBlank() }
            state.value = when {
                code != null -> QrCodeUiState.Content(code)
                result is AppResult.Failure -> QrCodeUiState.Error(result.error)
                else -> QrCodeUiState.Empty
            }
            if (code != null) {
                if (result is AppResult.Failure) errors.send(result.error)
                expireAt(code)
            }
        }
    }

    private fun expireAt(code: QrCodeSnapshot) {
        expiration?.cancel()
        expiration = viewModelScope.launch {
            delay((code.expiresAtMillis - clock.millis()).coerceAtLeast(0))
            // Hide at the cache deadline even when a manual network refresh is still pending.
            state.value = QrCodeUiState.Loading
            refresh(force = false)
        }
    }
}
