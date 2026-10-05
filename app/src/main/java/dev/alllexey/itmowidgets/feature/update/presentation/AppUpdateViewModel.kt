package dev.alllexey.itmowidgets.feature.update.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The screen renders what the check already found, so it never repeats the
 * request and has no loading or error state of its own.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val uiState: StateFlow<AppUpdateUiState> = MutableStateFlow(
        AppUpdateUiState(
            installed = checkNotNull(savedStateHandle.get<String>(ARG_INSTALLED_VERSION)),
            latest = checkNotNull(savedStateHandle.get<String>(ARG_LATEST_VERSION)),
            note = savedStateHandle.get<String>(ARG_NOTE).orEmpty(),
            unsupported = savedStateHandle.get<Boolean>(ARG_UNSUPPORTED) == true
        )
    ).asStateFlow()

    private val queue = EventQueue<AppUpdateEvent>()

    /** [AppUpdateEvent.Skipped] comes once the choice is stored, so closing the screen cannot cancel the write. */
    val events: Flow<AppUpdateEvent> = queue.events

    private var skipping = false

    /** A second tap while the first is stored is ignored, so the screen closes once. */
    fun skipVersion() {
        if (skipping) return
        skipping = true
        viewModelScope.launch {
            repository.skip(AppVersionName(uiState.value.latest))
            queue.send(AppUpdateEvent.Skipped)
        }
    }

    companion object {
        const val ARG_INSTALLED_VERSION = "installed_version"
        const val ARG_LATEST_VERSION = "latest_version"
        const val ARG_NOTE = "note"
        const val ARG_UNSUPPORTED = "unsupported"
    }
}
