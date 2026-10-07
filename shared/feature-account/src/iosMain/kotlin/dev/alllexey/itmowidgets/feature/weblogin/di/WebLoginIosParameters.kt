package dev.alllexey.itmowidgets.feature.weblogin.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginFixtureRepository
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel

/**
 * The Koin parameters of the web sign-in's ViewModel when SwiftUI owns it (IO-08b, recipe ios-swiftui-screen):
 * `ScreenViewModelStore` has no saved-state registry, so the sheet gets a fresh `SavedStateHandle` and a reopened
 * sheet starts with an empty field. Swift passes them to `store.resolve(type: WebLoginViewModel.self, parameters:)`;
 * Core 2.0's users area of the repository comes from `iosCoreModule`.
 */
object WebLoginIosParameters {

    fun viewModel(): List<Any> = listOf(SavedStateHandle())
}

/**
 * The web sign-in of a Debug build launched with `-itmoWebLoginFixture` (UI tests): the simulator has no camera, so
 * the scanner hands over [LINK] and the field takes [CODE]; [WebLoginFixtureRepository] answers without Backend.
 */
object WebLoginIosFixture {
    const val CODE = WebLoginFixtureRepository.CODE

    /** What the QR on the web version holds for [CODE]. */
    const val LINK = "https://dev.widgets.alllexey.dev/app/login?code=$CODE"

    fun viewModel(time: AcademicTimeProvider): WebLoginViewModel =
        WebLoginViewModel(SavedStateHandle(), WebLoginFixtureRepository(), time)
}
