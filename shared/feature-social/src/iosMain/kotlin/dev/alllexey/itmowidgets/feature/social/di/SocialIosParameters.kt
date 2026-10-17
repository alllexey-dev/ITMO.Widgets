package dev.alllexey.itmowidgets.feature.social.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs

// The Koin parameters of the social SwiftUI screens: a SavedStateHandle with what Android's shell seeds from the
// route, since a SwiftUI store has no saved-state registry.

/** The person card (`UserProfileViewModel`) of [isu]. */
fun userProfileParameters(isu: Int): List<Any> = listOf(SavedStateHandle(mapOf(UserScreenArgs.ISU to isu)))

/** Another user's friends (`UserFriendsViewModel`): the owner [isu] named [name]. */
fun userFriendsParameters(isu: Int, name: String): List<Any> =
    listOf(SavedStateHandle(mapOf(UserScreenArgs.ISU to isu, UserScreenArgs.NAME to name)))
