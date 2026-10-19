package dev.alllexey.itmowidgets.feature.sport.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs

/**
 * The Koin parameters of another user's sport (`UserSportViewModel`, `AppRoutes.UserSport`): a SavedStateHandle with
 * what Android's shell seeds from the route, since a SwiftUI store has no saved-state registry.
 */
fun userSportParameters(isu: Int, name: String, givenName: String?): List<Any> = listOf(
    SavedStateHandle(
        mapOf(UserScreenArgs.ISU to isu, UserScreenArgs.NAME to name, UserScreenArgs.GIVEN_NAME to givenName),
    ),
)
