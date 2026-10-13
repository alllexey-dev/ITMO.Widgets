package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.auth.di.authModule
import dev.alllexey.itmowidgets.feature.home.di.homeModule
import dev.alllexey.itmowidgets.feature.me.di.meModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingDataModule
import dev.alllexey.itmowidgets.feature.onboarding.di.onboardingModule
import dev.alllexey.itmowidgets.feature.qr.di.qrModule
import dev.alllexey.itmowidgets.feature.recordbook.di.recordbookModule
import dev.alllexey.itmowidgets.feature.resources.di.resourcesModule
import dev.alllexey.itmowidgets.feature.reviews.di.reviewsModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleDataModule
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsModule
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.sport.di.sportModule
import dev.alllexey.itmowidgets.feature.update.di.updateModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginDataModule
import dev.alllexey.itmowidgets.feature.weblogin.di.webLoginModule
import org.koin.core.module.Module

/**
 * The Koin modules of the release graph, the app's only graph, which [KoinStarter] loads and `KoinGraphTest` checks.
 * One line per module; debug fixture overrides stay out.
 */
object KoinModules {

    val all: List<Module> = listOf(
        // The app's own Android bindings (`di`, the flavor's `updateActionModule` included).
        coroutinesModule,
        timeModule,
        storageModule,
        diagnosticsModule,
        networkModule,
        sessionModule,
        notificationModule,
        locationModule,
        linksModule,
        webModule,
        androidQrModule,
        androidRecordbookModule,
        androidScheduleModule,
        androidSettingsModule,
        androidSportModule,
        androidUpdateModule,
        updateActionModule,
        debugModule,
        componentBindingsModule,
        // The shared modules' definitions (`feature-<x>/di`).
        qrModule,
        homeModule,
        resourcesModule,
        settingsDataModule,
        settingsModule,
        recordbookModule,
        socialModule,
        scheduleModule,
        scheduleDataModule,
        sportModule,
        authDataModule,
        authModule,
        onboardingDataModule,
        onboardingModule,
        meModule,
        reviewsModule,
        webLoginDataModule,
        webLoginModule,
        updateModule,
    )
}
