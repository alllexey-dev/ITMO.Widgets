package dev.alllexey.itmowidgets.ios.di

import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.feature.auth.di.accountIosModule
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.qr.di.qrIosModule
import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import dev.alllexey.itmowidgets.ios.IosPlatform
import org.koin.core.module.Module

/**
 * Every Koin module of the iOS app process, what `KoinModules` is on Android. One line per feature: its bindings live
 * in `shared/feature-<x>/src/iosMain/.../di/<Area>IosModule.kt`, and the feature's IO card appends the line.
 */
object IosKoinModules {

    fun all(platform: IosPlatform): List<Module> = listOf(
        iosCoreModule(platform),
        // The shared session and demo switch (KM-11h1) on their iOS ports (IO-21).
        authDataModule, accountIosModule,
        // The QR pass screen and the App Group snapshot of the QR widget (IO-21).
        qrModule, qrIosModule,
    )
}
