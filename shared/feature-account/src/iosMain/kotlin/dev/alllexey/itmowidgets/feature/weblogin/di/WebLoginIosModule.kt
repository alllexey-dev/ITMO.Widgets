package dev.alllexey.itmowidgets.feature.weblogin.di

import dev.alllexey.itmowidgets.feature.weblogin.data.IosDeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.data.IosHpkeSender
import dev.alllexey.itmowidgets.feature.weblogin.data.PairingHost
import dev.alllexey.itmowidgets.feature.weblogin.domain.DeviceUnlock
import dev.alllexey.itmowidgets.feature.weblogin.domain.HpkeSender
import org.koin.dsl.module

/** The iOS ports of pairing on the app's [PairingHost]: CryptoKit's HPKE and LocalAuthentication. */
fun webLoginIosModule(host: PairingHost) = module {
    single<HpkeSender> { IosHpkeSender(host) }
    single<DeviceUnlock> { IosDeviceUnlock(host) }
}
