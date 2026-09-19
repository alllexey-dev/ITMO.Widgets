package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Whether the signed-in user still has to pass the first-run flow. */
enum class OnboardingGate { Unknown, Required, Passed }

/**
 * Answers which root graph a signed-in session starts with.
 *
 * `Unknown` is not "not required": it is the frame before the stored flag has been
 * read. Treating it as passed would flash Home before the flow replaces it.
 */
@HiltViewModel
class OnboardingGateViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    onboardingRepository: OnboardingRepository
) : ViewModel() {

    val state: StateFlow<OnboardingGate> = combine(
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
