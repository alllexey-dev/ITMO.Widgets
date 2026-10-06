package dev.alllexey.itmowidgets.feature.onboarding.di

import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGateViewModel
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The first-run flow and its gate. The onboarding flag, the session, the opt-in and the widget appearance and spoiler
 * contracts come from the app's `CoreBridge`. The flow's step survives process death in its `SavedStateHandle` under
 * `onboarding_step`.
 */
val onboardingModule = module {
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::OnboardingGateViewModel)
}
