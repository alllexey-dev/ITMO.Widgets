package dev.alllexey.itmowidgets.feature.auth.di

import dev.alllexey.itmowidgets.feature.auth.presentation.AuthViewModel
import dev.alllexey.itmowidgets.feature.auth.presentation.InteractiveLoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The sign-in screens; the session comes from the app's `CoreBridge`. */
val authModule = module {
    viewModelOf(::AuthViewModel)
    viewModelOf(::InteractiveLoginViewModel)
}
