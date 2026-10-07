package dev.alllexey.itmowidgets.feature.onboarding.di

import androidx.lifecycle.SavedStateHandle

/**
 * The Koin parameters of the first-run flow's ViewModel when SwiftUI owns it (IO-07b, recipe ios-swiftui-screen):
 * `ScreenViewModelStore` has no saved-state registry, so the flow gets a fresh `SavedStateHandle`, and a relaunch
 * starts at the first step. Swift passes them to `store.resolve(type: OnboardingViewModel.self, parameters:)`.
 */
object OnboardingIosParameters {

    fun viewModel(): List<Any> = listOf(SavedStateHandle())
}
