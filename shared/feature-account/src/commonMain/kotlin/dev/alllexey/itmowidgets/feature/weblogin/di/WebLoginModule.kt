package dev.alllexey.itmowidgets.feature.weblogin.di

import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginRepositoryImpl
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The web sign-in repository over Core 2.0's users area; the gate, the demo switch and the dispatchers come from the
 * platform. A separate module from [webLoginModule], so a debug fixture that reloads the sheet never builds a second
 * repository.
 */
val webLoginDataModule = module {
    singleOf(::WebLoginRepositoryImpl) { bind<WebLoginRepository>() }
}

/**
 * The web sign-in sheet over [webLoginDataModule]; the academic time comes from the platform, and the typed code
 * survives process death in the `SavedStateHandle` under `web_login_code`.
 */
val webLoginModule = module {
    viewModelOf(::WebLoginViewModel)
}
