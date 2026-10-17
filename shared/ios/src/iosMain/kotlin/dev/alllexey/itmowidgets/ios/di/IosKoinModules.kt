package dev.alllexey.itmowidgets.ios.di

import dev.alllexey.itmowidgets.core.di.iosBackgroundModule
import dev.alllexey.itmowidgets.core.di.iosCoreModule
import dev.alllexey.itmowidgets.core.network.BackendOrigin
import dev.alllexey.itmowidgets.core.text.UiTextResolver
import dev.alllexey.itmowidgets.core.text.resolve
import dev.alllexey.itmowidgets.feature.auth.di.accountIosModule
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.auth.di.authModule
import dev.alllexey.itmowidgets.feature.home.di.homeModule
import dev.alllexey.itmowidgets.feature.me.di.meModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingDataModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.qr.di.qrIosModule
import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookIosModule
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.resources.di.resourcesIosModule
import dev.alllexey.itmowidgets.feature.resources.di.resourcesModule
import dev.alllexey.itmowidgets.feature.reviews.di.reviewsIosModule
import dev.alllexey.itmowidgets.feature.reviews.di.reviewsModule
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
import dev.alllexey.itmowidgets.feature.sport.di.sportWidgetIosModule
import dev.alllexey.itmowidgets.feature.update.di.updateIosModule
import dev.alllexey.itmowidgets.feature.update.di.updateModule
import dev.alllexey.itmowidgets.feature.update.domain.AppStoreListing
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginDataModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginIosModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
import dev.alllexey.itmowidgets.ios.IosPlatform
import dev.alllexey.itmowidgets.ios.widgets.widgetRefreshIosModule
import org.koin.core.module.Module
import platform.Foundation.NSBundle

/**
 * Every Koin module of the iOS app process, what `KoinModules` is on Android. One line per feature: its bindings live
 * in `shared/feature-<x>/src/iosMain/.../di/<Area>IosModule.kt`.
 */
object IosKoinModules {

    fun all(platform: IosPlatform): List<Module> = listOf(
        iosCoreModule(platform),
        // The shared session and demo switch on their iOS ports.
        authDataModule, accountIosModule,
        // The QR pass screen and the App Group snapshot of the QR widget.
        qrModule, qrIosModule,
        // The sign-in and first-run screens with the first-run flag.
        authModule, onboardingDataModule, onboardingModule,
        // The SwiftUI settings and diagnostics over the shared pages; the sign-in and first-run screens write the same
        // data.
        settingsDataModule, settingsModule, settingsIosModule,
        // The recordbook graph with the BARS session on WebKit and the Keychain; the tab, the subject page
        // and `Мои баллы`.
        recordbookModule, recordbookIosModule(platform),
        // The schedule and sport widgets' App Group timelines and the refresh port sport asks after a booking.
        scheduleWidgetIosModule, sportWidgetIosModule, widgetRefreshIosModule,
        // The web sign-in sheet, pairing's CryptoKit and LocalAuthentication ports and the update offer on their iOS
        // ports.
        webLoginDataModule, webLoginModule, webLoginIosModule(platform), updateModule, updateIosModule,
        // Сегодня and the bell over every feature's sources.
        homeModule,
        // The notifier with the catalog's text resolver, the app refresh task and the schedule change check's iOS ports
        // and step; the runner is `IosBackgroundRefresh`, started after the graph.
        iosBackgroundModule(UiTextResolver { it.resolve() }), scheduleChangesIosModule,
        // The me tab, friends, search, profiles and a user's friends; the invitation names the App Store page or the
        // site.
        socialModule, socialIosModule(inviteUrl()), meModule,
        // The schedule feed, another user's schedule, the lesson sheet, the month sheet and the friend picker (over
        // social's `FriendRepository`).
        scheduleModule, scheduleDataModule, scheduleIosModule(),
        // The sport tab, its details sheet and another user's sport; the lessons' friends come through social's
        // `FriendRepository`.
        sportModule, sportIosModule,
        // The calendar sync over EventKit with its refresh step and the `.ics` file.
        calendarIosModule,
        // The teacher reviews with the editor and the report, the teacher tones, and the subject links with their
        // sheets and report.
        reviewsModule, reviewsIosModule, resourcesModule, resourcesIosModule,
    )

    /** The App Store page of the bundle's `AppStoreID` once the app has a record, the build's site until then. */
    private fun inviteUrl(): String =
        AppStoreListing.url(NSBundle.mainBundle.objectForInfoDictionaryKey("AppStoreID") as? String)
            ?: BackendOrigin.fromMainBundle()
}
