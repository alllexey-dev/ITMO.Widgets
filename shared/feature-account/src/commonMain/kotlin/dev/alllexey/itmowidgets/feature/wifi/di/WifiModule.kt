package dev.alllexey.itmowidgets.feature.wifi.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.wifi.data.WifiRepositoryImpl
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiRepository
import dev.alllexey.itmowidgets.feature.wifi.presentation.WifiViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The Wi-Fi sheet over ISU's Wi-Fi page; the ISU client (`IsuWebClient`), `SecureStore`, the current user, the demo
 * switch and the dispatchers come from the platform. One repository serves the sheet and sign-out.
 */
val wifiModule = module {
    singleOf(::WifiRepositoryImpl) { bind<WifiRepository>() }
    single<SessionDataCleaner>(named("wifi")) { get<WifiRepositoryImpl>() }
    viewModelOf(::WifiViewModel)
}
