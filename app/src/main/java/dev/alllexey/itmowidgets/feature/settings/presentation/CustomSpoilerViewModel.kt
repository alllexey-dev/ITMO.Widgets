package dev.alllexey.itmowidgets.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.feature.settings.domain.CustomSpoilerRepository
import dev.alllexey.itmowidgets.feature.settings.domain.WidgetRefreshRequester
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class CustomSpoilerUiState(val configured: Boolean? = null, val busy: Boolean = false)

enum class CustomSpoilerEvent { SAVED, RESET, FAILED }

@HiltViewModel
class CustomSpoilerViewModel @Inject constructor(
    private val repository: CustomSpoilerRepository,
    private val widgetRefreshRequester: WidgetRefreshRequester
) : ViewModel() {

    private val mutableState = MutableStateFlow(CustomSpoilerUiState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<CustomSpoilerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

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
                    widgetRefreshRequester.refreshAll()
                    eventChannel.send(if (configured) CustomSpoilerEvent.SAVED else CustomSpoilerEvent.RESET)
                } else {
                    eventChannel.send(CustomSpoilerEvent.FAILED)
                }
            } finally {
                val imageExists = mutableState.value.configured ?: repository.hasImage()
                mutableState.value = CustomSpoilerUiState(configured = imageExists)
            }
        }
    }
}
