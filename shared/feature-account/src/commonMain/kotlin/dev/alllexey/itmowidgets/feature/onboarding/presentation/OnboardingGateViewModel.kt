package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Answers which root graph a signed-in session starts with.
 *
 * `Unknown` is not "not required": it is the frame before the stored flag has been
 * read. Treating it as passed would flash Home before the flow replaces it.
 */
class OnboardingGateViewModel(
    sessionRepository: SessionRepository,
    onboardingRepository: OnboardingRepository
) : ViewModel() {

    val uiState: StateFlow<OnboardingGate> = combine(
        sessionRepository.state,
        onboardingRepository.observeCompleted()
    ) { session, completed ->
        when {
            session !is SessionState.SignedIn -> OnboardingGate.Unknown
            completed -> OnboardingGate.Passed
            else -> OnboardingGate.Required
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingGate.Unknown)
}
