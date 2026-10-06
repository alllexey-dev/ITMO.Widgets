package dev.alllexey.itmowidgets.ios.bridge.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * A ViewModel on the screen contract (`uiState`, `events`) that `ITMOWidgetsTests/BridgeTests` drives through the
 * Swift adapter, as `IosStrings.compositionProbe` serves `StringsTests`; no screen uses it. [onCleared] gets whether
 * the `viewModelScope` was already cancelled when `onCleared` ran.
 */
class BridgeProbeViewModel(private val onCleared: (scopeCancelled: Boolean) -> Unit) : ViewModel() {

    private val state = MutableStateFlow(BridgeProbeState(count = 0))
    private val queue = EventQueue<BridgeProbeEvent>()
    private val scopeJob = viewModelScope.coroutineContext[Job]

    val uiState: StateFlow<BridgeProbeState> = state.asStateFlow()
    val events: Flow<BridgeProbeEvent> = queue.events

    fun increment() {
        val next = state.updateAndGet { it.copy(count = it.count + 1) }
        viewModelScope.launch { queue.send(BridgeProbeEvent.Reached(next.count)) }
    }

    fun reset() {
        state.value = BridgeProbeState(count = 0)
        viewModelScope.launch { queue.send(BridgeProbeEvent.Reset) }
    }

    override fun onCleared() {
        onCleared(scopeJob?.isActive == false)
    }
}
