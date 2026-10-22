package dev.alllexey.itmowidgets.feature.connections.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.feature.connections.data.IosDeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.data.IosHpkeSender
import dev.alllexey.itmowidgets.feature.connections.data.PairingHost
import dev.alllexey.itmowidgets.feature.connections.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.connections.domain.HpkeSender
import org.koin.dsl.module

/**
 * The iOS ports of pairing on the app's [PairingHost]: CryptoKit's HPKE and LocalAuthentication; the text resolver
 * comes from `iosBackgroundModule`.
 */
fun connectionsIosModule(host: PairingHost) = module {
    single<HpkeSender> { IosHpkeSender(host) }
    single<DeviceUnlock> { IosDeviceUnlock(host, get()) }
}

/**
 * The Koin parameters of the connections ViewModels when SwiftUI owns them (`ScreenViewModelStore` has no
 * saved-state registry): Swift passes them to `store.resolve(type:parameters:)`.
 */
object ConnectionsIosParameters {

    /** The pairing sheet: a fresh `SavedStateHandle`, so a reopened sheet starts with an empty field. */
    fun pairing(): List<Any> = listOf(SavedStateHandle())

    /** A connected client's screen by its connection id. */
    fun connection(id: String): List<Any> = listOf(id)
}
