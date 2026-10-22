package dev.alllexey.itmowidgets.feature.connections.di

import dev.alllexey.itmoapi.itmoid.ItmoIdSessions
import dev.alllexey.itmowidgets.core.bell.BellSource
import dev.alllexey.itmowidgets.core.connections.DevicesOverview
import dev.alllexey.itmowidgets.feature.connections.data.ClientSignInImpl
import dev.alllexey.itmowidgets.feature.connections.data.DevicesOverviewImpl
import dev.alllexey.itmowidgets.feature.connections.data.DevicesRepositoryImpl
import dev.alllexey.itmowidgets.feature.connections.data.ItmoLoginsRepositoryImpl
import dev.alllexey.itmowidgets.feature.connections.data.PairingRepositoryImpl
import dev.alllexey.itmowidgets.feature.connections.data.bell.ConnectionsBellSource
import dev.alllexey.itmowidgets.feature.connections.domain.ClientSignIn
import dev.alllexey.itmowidgets.feature.connections.domain.DevicesRepository
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoLoginsRepository
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRepository
import dev.alllexey.itmowidgets.feature.connections.domain.PairingSealer
import dev.alllexey.itmowidgets.feature.connections.presentation.ConnectionViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.DevicesViewModel
import dev.alllexey.itmowidgets.feature.connections.presentation.PairingViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The 7-day reminder's place in the open set of `BellSource`s that Сегодня reads. */
val connectionsQualifier: Qualifier = named("connections")

/**
 * «Устройства» and pairing over Core 2.0's connections area and MyItmoApi's ITMO.ID sessions; the gate, the demo
 * switch, the dispatchers, the clock, the engine, the phone's ITMO.ID cookies (`ItmoIdCookies`), the label switch
 * (`SignInLabelPreferences`) and the notifier come from the platform, as does `HpkeSender` (Tink on Android,
 * CryptoKit on iOS).
 */
val connectionsDataModule = module {
    single { ItmoIdSessions(get(), get()) }
    singleOf(::DevicesRepositoryImpl) { bind<DevicesRepository>() }
    singleOf(::PairingRepositoryImpl) { bind<PairingRepository>() }
    singleOf(::ItmoLoginsRepositoryImpl) { bind<ItmoLoginsRepository>() }
    singleOf(::ClientSignInImpl) { bind<ClientSignIn>() }
    singleOf(::PairingSealer)
    singleOf(::DevicesOverviewImpl) { bind<DevicesOverview>() }
    single<BellSource>(connectionsQualifier) { ConnectionsBellSource(get(), get(), get(), get()) }
}

/** «Устройства», a client and the pairing sheet; the pairing link field survives in the `SavedStateHandle`. */
val connectionsModule = module {
    viewModelOf(::DevicesViewModel)
    viewModelOf(::ConnectionViewModel)
    viewModelOf(::PairingViewModel)
}
