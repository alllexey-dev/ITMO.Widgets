package dev.alllexey.itmowidgets.feature.debug.presentation

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
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface DebugToolsUiState {
    data class Content(
        val effectiveDate: LocalDate,
        val dateOverride: LocalDate?,
        val scoreOverride: SportScoreOverride?,
        val lessonTemplatesEnabled: Boolean,
        val refreshTokenConfigured: Boolean,
        val refreshTokenUpdateInProgress: Boolean,
        val customServicesEnabled: Boolean
    ) : DebugToolsUiState
}

sealed interface DebugToolsEvent {
    data object RecreateActivity : DebugToolsEvent
    data object RefreshTokenUpdated : DebugToolsEvent
    data class RefreshTokenUpdateFailed(val error: AppError) : DebugToolsEvent
}

@HiltViewModel
class DebugToolsViewModel @Inject constructor(
    private val timeProvider: AcademicTimeProvider,
    private val timeOverrideController: AcademicTimeOverrideController,
    private val sportScoreOverrideController: SportScoreOverrideController,
    private val sportLessonTemplateController: SportLessonTemplateController,
    private val refreshTokenController: DebugRefreshTokenController,
    private val customServicesRepository: CustomServicesRepository
) : ViewModel() {

    private val mutableUiState =
        MutableStateFlow<DebugToolsUiState>(readState(customServicesEnabled = false))
    val uiState: StateFlow<DebugToolsUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<DebugToolsEvent>(Channel.BUFFERED)
    val events: Flow<DebugToolsEvent> = eventChannel.receiveAsFlow()

    init {
        customServicesRepository.observeEnabled()
            .onEach { enabled ->
                mutableUiState.value = currentState().copy(customServicesEnabled = enabled)
            }
            .launchIn(viewModelScope)
    }

    fun setCustomServicesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            customServicesRepository.setEnabled(enabled)
        }
    }

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
        val state = mutableUiState.value as DebugToolsUiState.Content
        if (state.refreshTokenUpdateInProgress) return

        mutableUiState.value = state.copy(refreshTokenUpdateInProgress = true)
        viewModelScope.launch {
            when (val result = refreshTokenController.replaceRefreshToken(refreshToken)) {
                is AppResult.Success -> {
                    mutableUiState.value = readState()
                    eventChannel.send(DebugToolsEvent.RefreshTokenUpdated)
                }
                is AppResult.Failure -> {
                    mutableUiState.value = readState()
                    eventChannel.send(DebugToolsEvent.RefreshTokenUpdateFailed(result.error))
                }
            }
        }
    }

    private fun publishAndRecreate() {
        mutableUiState.value = readState()
        eventChannel.trySend(DebugToolsEvent.RecreateActivity)
    }

    private fun currentState(): DebugToolsUiState.Content = mutableUiState.value as DebugToolsUiState.Content

    private fun readState(
        customServicesEnabled: Boolean = currentState().customServicesEnabled
    ): DebugToolsUiState.Content {
        return DebugToolsUiState.Content(
            effectiveDate = timeProvider.today(),
            dateOverride = timeOverrideController.getOverrideDate(),
            scoreOverride = sportScoreOverrideController.getOverride(),
            lessonTemplatesEnabled = sportLessonTemplateController.isEnabled(),
            refreshTokenConfigured = refreshTokenController.hasRefreshToken(),
            refreshTokenUpdateInProgress = false,
            customServicesEnabled = customServicesEnabled
        )
    }
}
