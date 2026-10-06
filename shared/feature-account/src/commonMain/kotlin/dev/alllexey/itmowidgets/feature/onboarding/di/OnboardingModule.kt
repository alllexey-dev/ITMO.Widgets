package dev.alllexey.itmowidgets.feature.onboarding.di

import dev.alllexey.itmowidgets.core.onboarding.OnboardingRepository
import dev.alllexey.itmowidgets.feature.onboarding.data.OnboardingRepositoryImpl
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingGateViewModel
import dev.alllexey.itmowidgets.feature.onboarding.presentation.OnboardingViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The first-run flag, one single over the platform's `UtilityStorage`; Hilt-built code reads it through the app's
 * `AccountOnboardingBridge`. A separate module from [onboardingModule], so a debug fixture that reloads the flow never
 * builds a second repository.
 */
val onboardingDataModule = module {
    singleOf(::OnboardingRepositoryImpl) { bind<OnboardingRepository>() }
}

/**
 * The first-run flow and its gate. The onboarding flag comes from [onboardingDataModule]; the session, the opt-in and
 * the widget appearance and spoiler contracts from the platform. The flow's step survives process death in its
 * `SavedStateHandle` under `onboarding_step`.
 */
val onboardingModule = module {
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::OnboardingGateViewModel)
}
