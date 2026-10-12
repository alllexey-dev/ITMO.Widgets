package dev.alllexey.itmowidgets.feature.weblogin.di

import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import dev.alllexey.itmowidgets.feature.weblogin.data.PairingRepositoryImpl
import dev.alllexey.itmowidgets.feature.weblogin.data.TokenManagerHandoffSession
import dev.alllexey.itmowidgets.feature.weblogin.data.WebLoginRepositoryImpl
import dev.alllexey.itmowidgets.feature.weblogin.domain.HandoffSession
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingHandoff
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingSealer
import dev.alllexey.itmowidgets.feature.weblogin.presentation.ConnectionsViewModel
import dev.alllexey.itmowidgets.feature.weblogin.presentation.WebLoginViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * The web sign-in repository over Core 2.0's users area and the pairing repository over its pairing area; the gate,
 * the demo switch and the dispatchers come from the platform. A separate module from [webLoginModule], so a debug
 * fixture that reloads the sheet never builds a second repository. Pairing's hand-off and seal take MyItmoApi's
 * client and token storage, the clock, the signed-in user and the platform's `HpkeSender` (Tink on Android,
 * CryptoKit on iOS).
 */
val webLoginDataModule = module {
    singleOf(::WebLoginRepositoryImpl) { bind<WebLoginRepository>() }
    singleOf(::PairingRepositoryImpl) { bind<PairingRepository>() }
    singleOf(::TokenManagerHandoffSession) { bind<HandoffSession>() }
    singleOf(::PairingSealer)
    singleOf(::PairingHandoff)
}

/**
 * The «Подключить устройство» sheet and «Подключённые устройства» over [webLoginDataModule]; the academic time comes
 * from the platform, and the typed code survives process death in the `SavedStateHandle` under `web_login_code`.
 */
val webLoginModule = module {
    viewModelOf(::WebLoginViewModel)
    viewModelOf(::ConnectionsViewModel)
}
