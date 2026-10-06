package dev.alllexey.itmowidgets.feature.weblogin.di

import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The web sign-in sheet. The repository comes from the app's `AccountWebLoginBridge`, the academic time from its
 * `CoreBridge`; the typed code survives process death in the `SavedStateHandle` under `web_login_code`.
 */
val webLoginModule = module {
    viewModelOf(::WebLoginViewModel)
}
