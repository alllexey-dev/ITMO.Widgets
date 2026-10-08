package dev.alllexey.itmowidgets.ios.di

import dev.alllexey.itmowidgets.core.di.iosBackgroundModule
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.network.BackendOrigin
import dev.alllexey.itmowidgets.feature.auth.di.accountIosModule
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.auth.di.authModule
import dev.alllexey.itmowidgets.feature.friendselector.di.friendSelectorModule
import dev.alllexey.itmowidgets.feature.home.data.PlacedWidgetKinds
import dev.alllexey.itmowidgets.feature.home.di.homeIosModule
import dev.alllexey.itmowidgets.feature.home.di.homeModule
import dev.alllexey.itmowidgets.feature.me.di.meModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingDataModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.qr.di.qrIosModule
import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookIosModule
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.schedule.di.calendar.calendarIosModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleChangesIosModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleDataModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleIosModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleWidgetIosModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsIosModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsModule
import dev.alllexey.itmowidgets.feature.social.di.socialIosModule
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.sport.di.sportIosModule
import dev.alllexey.itmowidgets.feature.sport.di.sportModule
import dev.alllexey.itmowidgets.feature.update.di.updateIosModule
import dev.alllexey.itmowidgets.feature.update.di.updateModule
import dev.alllexey.itmowidgets.feature.update.domain.AppStoreListing
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginDataModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
import dev.alllexey.itmowidgets.ios.IosPlatform
import org.koin.core.module.Module
import platform.Foundation.NSBundle

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
        // The recordbook graph with the BARS session on WebKit and the Keychain (IO-09d1); the tab, the subject page
        // and `Мои баллы` with the subject links left out until IO-09f (IO-09d2).
        recordbookModule, recordbookIosModule(platform),
        // The schedule widgets' App Group timeline and the schedule widget refresh port (IO-10b).
        scheduleWidgetIosModule,
        // The web sign-in sheet and the update offer on their iOS ports (IO-08b).
        webLoginDataModule, webLoginModule, updateModule, updateIosModule,
        // The home feed with the hints over WidgetKit and UNUserNotificationCenter (IO-09a).
        homeModule, homeIosModule(PlacedWidgetKinds { completion -> platform.installedWidgetKinds(completion) }),
        // The notifier, the app refresh task and the schedule change check's iOS ports and step (IO-14); the runner
        // is `IosBackgroundRefresh`, started after the graph.
        iosBackgroundModule, scheduleChangesIosModule,
        // The me tab, friends, search, profiles and a user's friends; the invitation names the App Store page or the
        // site (IO-09e).
        socialModule, socialIosModule(inviteUrl()), meModule,
        // The schedule tab, another user's schedule, the changes, the lesson and pending sport sheets and the friend
        // picker; the iOS ports keep the teacher tones empty until their card (IO-09b).
        scheduleModule, scheduleDataModule, scheduleIosModule(), friendSelectorModule,
        // The sport tab, its details sheet and another user's sport; the lessons' friends come through the friend
        // picker's `FriendRepository` above (IO-09c).
        sportModule, sportIosModule,
        // The calendar sync over EventKit with its refresh step and the `.ics` file (IO-15b).
        calendarIosModule,
    )

    /** The App Store page of the bundle's `AppStoreID` once the app has a record (T13), the build's site until then. */
    private fun inviteUrl(): String =
        AppStoreListing.url(NSBundle.mainBundle.objectForInfoDictionaryKey("AppStoreID") as? String)
            ?: BackendOrigin.fromMainBundle()
}
