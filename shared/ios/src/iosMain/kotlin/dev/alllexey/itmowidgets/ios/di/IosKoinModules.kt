package dev.alllexey.itmowidgets.ios.di

import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.feature.auth.di.accountIosModule
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.auth.di.authModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingDataModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.qr.di.qrIosModule
import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookIosModule
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleWidgetIosModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsIosModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsModule
import dev.alllexey.itmowidgets.feature.update.di.updateIosModule
import dev.alllexey.itmowidgets.feature.update.di.updateModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginDataModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
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
        // The sign-in and first-run screens with the first-run flag (IO-07b).
        authModule, onboardingDataModule, onboardingModule,
        // The SwiftUI settings and diagnostics over the shared pages (IO-08a); IO-07b's screens write the same data.
        settingsDataModule, settingsModule, settingsIosModule,
        // The recordbook graph with the BARS session on WebKit and the Keychain (IO-09d1); no screen yet (IO-09d2).
        recordbookModule, recordbookIosModule(platform),
        // The schedule widgets' App Group timeline and the schedule widget refresh port (IO-10b).
        scheduleWidgetIosModule,
        // The web sign-in sheet and the update offer on their iOS ports (IO-08b).
        webLoginDataModule, webLoginModule, updateModule, updateIosModule,
    )
}
