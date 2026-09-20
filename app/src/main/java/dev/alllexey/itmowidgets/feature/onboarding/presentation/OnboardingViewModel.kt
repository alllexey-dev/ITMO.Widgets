package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.settings.CustomSpoilerRepository
import dev.alllexey.itmowidgets.core.settings.WidgetAppearanceRepository
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The first-run flow.
 *
 * Every choice is written through its own repository the moment it is made, so
 * leaving at any step leaves a consistent app and nothing has to be replayed at
 * the end. The step itself lives in the saved state: process death in the middle
 * of the flow returns to the same page.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val customServicesRepository: CustomServicesRepository,
    private val widgetAppearanceRepository: WidgetAppearanceRepository,
    private val customSpoilerRepository: CustomSpoilerRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mutableState = MutableStateFlow(OnboardingUiState(step = restoredStep()))
    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events: Flow<OnboardingEvent> = eventChannel.receiveAsFlow()

    init {
        customServicesRepository.observeEnabled()
            .onEach { enabled ->
                mutableState.update { it.copy(servicesEnabled = enabled) }
                // The notifications step exists only behind the opt-in; losing it lands on the opt-in.
                if (!enabled && mutableState.value.step == OnboardingStep.NOTIFICATIONS) {
                    moveTo(OnboardingStep.SERVICES)
                }
            }
            .launchIn(viewModelScope)
        widgetAppearanceRepository.observeAppearance()
            .onEach { appearance -> mutableState.update { it.copy(appearance = appearance) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            val configured = customSpoilerRepository.hasImage()
            // A picked image may be saved before the initial disk read answers.
            mutableState.update {
                if (it.customSpoiler == null && !it.spoilerBusy) it.copy(customSpoiler = configured) else it
            }
        }
    }

    /** The footer's primary action: one step forward, or the end of the flow on its last step. */
    fun next() {
        val current = mutableState.value
        if (current.isLastStep) complete() else moveTo(current.steps[current.stepIndex + 1])
    }

    fun back() {
        val current = mutableState.value
        if (current.stepIndex > 0) moveTo(current.steps[current.stepIndex - 1])
    }

    /** Leaving early is a valid answer: what was chosen is already stored. */
    fun skip() {
        complete()
    }

    fun setOption(option: WidgetOption, enabled: Boolean) {
        viewModelScope.launch {
            try {
                with(widgetAppearanceRepository) {
                    when (option) {
                        WidgetOption.COMPACT_NEXT_LESSON_EARLY -> setCompactNextLessonEarly(enabled)
                        WidgetOption.COMPACT_HIDE_TEACHER -> setCompactTeacherHidden(enabled)
                        WidgetOption.FULL_HIDE_TEACHER -> setFullTeacherHidden(enabled)
                        WidgetOption.FULL_HIDE_PAST_LESSONS -> setFullPastLessonsHidden(enabled)
                        WidgetOption.FULL_SHOW_TOMORROW -> setFullTomorrowEnabled(enabled)
                        WidgetOption.QR_DYNAMIC_COLORS -> setQrDynamicColorsEnabled(enabled)
                        WidgetOption.QR_SPOILER -> setQrSpoilerEnabled(enabled)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventChannel.send(OnboardingEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    fun setTextSize(kind: WidgetKind, size: WidgetTextSize) {
        viewModelScope.launch {
            try {
                when (kind) {
                    WidgetKind.SINGLE_LESSON -> widgetAppearanceRepository.setCompactTextSize(size)
                    WidgetKind.DAY_SCHEDULE -> widgetAppearanceRepository.setFullTextSize(size)
                    WidgetKind.QR -> Unit
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventChannel.send(OnboardingEvent.ShowError(AppError.Unknown(error)))
            }
        }
    }

    /** The picked and cropped image; the repository refreshes the pinned widgets itself. */
    fun saveSpoilerImage(sourceUri: String) = updateSpoilerImage(configured = true) {
        customSpoilerRepository.saveImage(sourceUri)
    }

    fun resetSpoilerImage() = updateSpoilerImage(configured = false) {
        customSpoilerRepository.resetImage()
    }

    private fun updateSpoilerImage(configured: Boolean, write: suspend () -> Boolean) {
        if (mutableState.value.spoilerBusy) return
        mutableState.update { it.copy(spoilerBusy = true) }
        viewModelScope.launch {
            val changed = try {
                write()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            mutableState.update {
                if (changed) {
                    it.copy(spoilerBusy = false, customSpoiler = configured, spoilerRevision = it.spoilerRevision + 1)
                } else {
                    it.copy(spoilerBusy = false)
                }
            }
            if (!changed) eventChannel.send(OnboardingEvent.SpoilerImageFailed)
        }
    }

    fun setServicesEnabled(enabled: Boolean) {
        val current = mutableState.value
        if (current.servicesEnabled == enabled || current.servicesBusy) return
        mutableState.update { it.copy(servicesBusy = true) }
        viewModelScope.launch {
            try {
                customServicesRepository.setEnabled(enabled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                eventChannel.send(OnboardingEvent.ShowError(AppError.Unknown(error)))
            } finally {
                mutableState.update { it.copy(servicesBusy = false) }
            }
        }
    }

    fun pinWidget(kind: WidgetKind) {
        if (kind in mutableState.value.pinnedWidgets) return
        eventChannel.trySend(OnboardingEvent.RequestPinWidget(kind))
    }

    fun onWidgetPinned(kind: WidgetKind) {
        mutableState.update { it.copy(pinnedWidgets = it.pinnedWidgets + kind) }
    }

    fun onPinSupportChanged(supported: Boolean) {
        mutableState.update { it.copy(pinSupported = supported) }
    }

    /**
     * Granted: the system page is where channels live. Denied once: Android will not
     * ask again, so the second tap has to reach the same page.
     */
    fun requestNotifications() {
        val current = mutableState.value
        if (current.notificationsGranted || current.notificationsAsked) {
            eventChannel.trySend(OnboardingEvent.OpenNotificationSettings)
            return
        }
        mutableState.update { it.copy(notificationsAsked = true) }
        eventChannel.trySend(OnboardingEvent.RequestNotificationPermission)
    }

    fun onNotificationPermission(granted: Boolean) {
        mutableState.update { it.copy(notificationsGranted = granted) }
    }

    private fun complete() {
        if (mutableState.value.finished) return
        mutableState.update { it.copy(finished = true) }
        viewModelScope.launch { onboardingRepository.complete() }
    }

    private fun moveTo(step: OnboardingStep) {
        savedStateHandle[KEY_STEP] = step.name
        mutableState.update { it.copy(step = step) }
    }

    private fun restoredStep(): OnboardingStep {
        val stored = savedStateHandle.get<String>(KEY_STEP)
        return OnboardingStep.entries.firstOrNull { it.name == stored } ?: OnboardingStep.COMPACT_WIDGET
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
    }
}
