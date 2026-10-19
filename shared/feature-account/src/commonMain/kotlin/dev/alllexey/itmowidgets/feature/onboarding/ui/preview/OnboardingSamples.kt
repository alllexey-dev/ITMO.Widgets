package dev.alllexey.itmowidgets.feature.onboarding.ui.preview

import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingMe
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingStep
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingUiState

/** The first-run flow's two steps and their answered states, for both platforms' previews. */
object OnboardingSamples {
    /** [step] is `permissions` (granted), `permissions-none`, `connection` or `connected`. */
    fun state(step: String): OnboardingUiState = when (step) {
        "permissions" -> base.copy(notificationsGranted = true, notificationsAsked = true)
        "connection" -> base.copy(step = OnboardingStep.CONNECTION)
        "connected" -> base.copy(step = OnboardingStep.CONNECTION, servicesEnabled = true)
        else -> base
    }

    private val base = OnboardingUiState(
        step = OnboardingStep.PERMISSIONS,
        me = OnboardingMe("Александрова Мария Сергеевна", "АМ", null, 412345),
        calendarSynced = false,
    )
}
