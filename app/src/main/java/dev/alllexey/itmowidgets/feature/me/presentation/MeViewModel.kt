package dev.alllexey.itmowidgets.feature.me.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.debug.DebugRefreshTokenController
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.SportScoreOverride
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface MeUiState {
    data class Content(
        val effectiveDate: LocalDate,
        val dateOverride: LocalDate?,
        val scoreOverride: SportScoreOverride?,
        val lessonTemplatesEnabled: Boolean,
        val refreshTokenConfigured: Boolean,
        val refreshTokenUpdateInProgress: Boolean
    ) : MeUiState
}

sealed interface MeEvent {
    data object RecreateActivity : MeEvent
    data object RefreshTokenUpdated : MeEvent
    data class RefreshTokenUpdateFailed(val error: AppError) : MeEvent
}

@HiltViewModel
class MeViewModel @Inject constructor(
    private val timeProvider: AcademicTimeProvider,
    private val timeOverrideController: AcademicTimeOverrideController,
    private val sportScoreOverrideController: SportScoreOverrideController,
    private val sportLessonTemplateController: SportLessonTemplateController,
    private val refreshTokenController: DebugRefreshTokenController
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<MeUiState>(readState())
    val uiState: StateFlow<MeUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<MeEvent>(Channel.BUFFERED)
    val events: Flow<MeEvent> = eventChannel.receiveAsFlow()

    fun setDateOverride(date: LocalDate?) {
        timeOverrideController.setOverrideDate(date)
        publishAndRecreate()
    }

    fun setScoreOverride(attendances: Int, bonus: Int) {
        sportScoreOverrideController.setOverride(
            SportScoreOverride(attendances = attendances, bonus = bonus)
        )
        publishAndRecreate()
    }

    fun clearScoreOverride() {
        sportScoreOverrideController.setOverride(null)
        publishAndRecreate()
    }

    fun setLessonTemplatesEnabled(enabled: Boolean) {
        sportLessonTemplateController.setEnabled(enabled)
        publishAndRecreate()
    }

    fun replaceRefreshToken(refreshToken: String) {
        val state = mutableUiState.value as MeUiState.Content
        if (state.refreshTokenUpdateInProgress) return

        mutableUiState.value = state.copy(refreshTokenUpdateInProgress = true)
        viewModelScope.launch {
            when (val result = refreshTokenController.replaceRefreshToken(refreshToken)) {
                is AppResult.Success -> {
                    mutableUiState.value = readState()
                    eventChannel.send(MeEvent.RefreshTokenUpdated)
                }
                is AppResult.Failure -> {
                    mutableUiState.value = readState()
                    eventChannel.send(MeEvent.RefreshTokenUpdateFailed(result.error))
                }
            }
        }
    }

    private fun publishAndRecreate() {
        mutableUiState.value = readState()
        eventChannel.trySend(MeEvent.RecreateActivity)
    }

    private fun readState(): MeUiState.Content {
        return MeUiState.Content(
            effectiveDate = timeProvider.today(),
            dateOverride = timeOverrideController.getOverrideDate(),
            scoreOverride = sportScoreOverrideController.getOverride(),
            lessonTemplatesEnabled = sportLessonTemplateController.isEnabled(),
            refreshTokenConfigured = refreshTokenController.hasRefreshToken(),
            refreshTokenUpdateInProgress = false
        )
    }
}
