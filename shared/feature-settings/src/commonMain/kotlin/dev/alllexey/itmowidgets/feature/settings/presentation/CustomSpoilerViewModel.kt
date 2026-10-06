package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomSpoilerViewModel(
    private val repository: CustomSpoilerRepository
) : ViewModel() {

    private val mutableState = MutableStateFlow(CustomSpoilerUiState())
    val uiState: StateFlow<CustomSpoilerUiState> = mutableState.asStateFlow()
    private val eventQueue = EventQueue<CustomSpoilerEvent>()
    val events: Flow<CustomSpoilerEvent> = eventQueue.events

    init {
        viewModelScope.launch {
            val configured = repository.hasImage()
            // A picker result may arrive before the initial disk read finishes.
            if (!mutableState.value.busy && mutableState.value.configured == null) {
                mutableState.value = mutableState.value.copy(configured = configured)
            }
        }
    }

    fun saveImage(sourceUri: String) = updateImage(configured = true) {
        repository.saveImage(sourceUri)
    }

    fun resetImage() = updateImage(configured = false) { repository.resetImage() }

    private fun updateImage(configured: Boolean, update: suspend () -> Boolean) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            try {
                if (update()) {
                    mutableState.value = mutableState.value.copy(configured = configured)
                    eventQueue.send(if (configured) CustomSpoilerEvent.SAVED else CustomSpoilerEvent.RESET)
                } else {
                    eventQueue.send(CustomSpoilerEvent.FAILED)
                }
            } finally {
                val imageExists = mutableState.value.configured ?: repository.hasImage()
                mutableState.value = CustomSpoilerUiState(configured = imageExists)
            }
        }
    }
}
