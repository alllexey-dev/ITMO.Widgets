package dev.alllexey.itmowidgets.feature.update.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateRepository
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * @property note Release notes from the backend; empty when there are none.
 * @property unsupported The installed build is below the minimum the backend
 * serves, so the offer cannot be skipped — there is no working version to skip to.
 */
data class AppUpdateUiState(
    val installed: String,
    val latest: String,
    val note: String,
    val unsupported: Boolean
)

/**
 * The screen renders what the check already found, so it never repeats the
 * request and has no loading or error state of its own.
 */
@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val uiState = AppUpdateUiState(
        installed = checkNotNull(savedStateHandle.get<String>(ARG_INSTALLED_VERSION)),
        latest = checkNotNull(savedStateHandle.get<String>(ARG_LATEST_VERSION)),
        note = savedStateHandle.get<String>(ARG_NOTE).orEmpty(),
        unsupported = savedStateHandle.get<Boolean>(ARG_UNSUPPORTED) == true
    )

    private val dismissals = Channel<Unit>(Channel.CONFLATED)

    /** Emitted once the choice is stored, so closing the screen cannot cancel the write. */
    val skipped: Flow<Unit> = dismissals.receiveAsFlow()

    fun skipVersion() {
        viewModelScope.launch {
            repository.skip(AppVersionName(uiState.latest))
            dismissals.send(Unit)
        }
    }

    companion object {
        const val ARG_INSTALLED_VERSION = "installed_version"
        const val ARG_LATEST_VERSION = "latest_version"
        const val ARG_NOTE = "note"
        const val ARG_UNSUPPORTED = "unsupported"
    }
}
