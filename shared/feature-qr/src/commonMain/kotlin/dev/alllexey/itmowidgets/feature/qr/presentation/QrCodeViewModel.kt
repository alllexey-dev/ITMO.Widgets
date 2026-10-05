package dev.alllexey.itmowidgets.feature.qr.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The QR pass screen. Expiry follows the wall [clock], never the academic time, so the debug academic override
 * cannot keep an expired pass on the screen. The colour setting is read on every request, so a change made in the
 * settings shows when the screen comes back.
 */
class QrCodeViewModel(
    private val repository: QrCodeRepository,
    private val appearance: QrAppearancePreferences,
    private val clock: Clock
) : ViewModel() {
    private val state = MutableStateFlow<QrCodeUiState>(QrCodeUiState.Loading)
    val uiState: StateFlow<QrCodeUiState> = state.asStateFlow()
    private val queue = EventQueue<QrCodeEvent>()
    val events: Flow<QrCodeEvent> = queue.events
    private var request: Job? = null
    private var expiration: Job? = null
    private var visible = false

    fun start() {
        visible = true
        val current = state.value as? QrCodeUiState.Content
        if (current != null && current.code.expiresAtMillis <= nowMillis()) state.value = QrCodeUiState.Loading
        refresh(RefreshMode.Silent)
    }

    fun stop() {
        visible = false
        request?.cancel()
        request = null
        expiration?.cancel()
        expiration = null
    }

    fun refresh(mode: RefreshMode) {
        if (!visible || request?.isActive == true) return
        val previous = (state.value as? QrCodeUiState.Content)?.takeIf { it.code.expiresAtMillis > nowMillis() }
        if (previous != null) state.value = previous.copy(refreshing = true)
        request = viewModelScope.launch {
            val dynamicColors = appearance.useDynamicColors()
            if (previous == null) {
                // A fresh screen shows the cached pass at once; only an absent or expired cache waits.
                val cached = repository.currentQr()?.takeIf { it.expiresAtMillis > nowMillis() && it.hex.isNotBlank() }
                state.value = cached
                    ?.let { QrCodeUiState.Content(it, refreshing = true, useDynamicColors = dynamicColors) }
                    ?: QrCodeUiState.Loading
            } else if (previous.useDynamicColors != dynamicColors) {
                state.value = previous.copy(refreshing = true, useDynamicColors = dynamicColors)
            }
            val result = repository.refreshQrHex(force = mode == RefreshMode.Force)
            val code = repository.currentQr()?.takeIf { it.expiresAtMillis > nowMillis() && it.hex.isNotBlank() }
            state.value = when {
                code != null -> QrCodeUiState.Content(code, useDynamicColors = dynamicColors)
                result is AppResult.Failure -> QrCodeUiState.Error(result.error)
                else -> QrCodeUiState.Empty
            }
            if (code != null) {
                if (result is AppResult.Failure) queue.send(QrCodeEvent.RefreshFailed(result.error))
                expireAt(code)
            }
        }
    }

    private fun expireAt(code: QrCodeSnapshot) {
        expiration?.cancel()
        expiration = viewModelScope.launch {
            delay((code.expiresAtMillis - nowMillis()).coerceAtLeast(0))
            // Hide at the cache deadline even when a manual network refresh is still pending.
            state.value = QrCodeUiState.Loading
            refresh(RefreshMode.Silent)
        }
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()
}
