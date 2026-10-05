package dev.alllexey.itmowidgets.feature.qr.di

import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.AppDirectories
import dev.alllexey.itmowidgets.feature.qr.data.QrWidgetStateStoreImpl
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSource
import dev.alllexey.itmowidgets.feature.qr.data.remote.QrCodeRemoteDataSourceImpl
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrAppearancePreferencesImpl
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrTilePreferencesImpl
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrTilePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import dev.alllexey.itmowidgets.feature.qr.presentation.QrCodeViewModel
import kotlin.time.Clock
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The QR pass, data and screen. Koin is the only graph for these types; the app's widget, tile and worker read them
 * through `QrBridge`. The core types (`MyItmoClient`, the `app_preferences` DataStore and its stores,
 * `AppDirectories`, the wall clock, `DemoMode`, `AppDispatchers`) come from the app's `CoreBridge`.
 */
val qrModule = module {
    // Type arguments pick the public constructor; the internal one takes a file on a test file system.
    singleOf<QrCodeLocalDataSourceImpl, AppDirectories, Clock>(::QrCodeLocalDataSourceImpl) {
        bind<QrCodeLocalDataSource>()
    }
    singleOf(::QrCodeRemoteDataSourceImpl) { bind<QrCodeRemoteDataSource>() }
    // One instance for the screen, the widget and sign-out; the cleaner contribution is qualified (an open set).
    singleOf(::QrCodeRepositoryImpl) { bind<QrCodeRepository>() }
    single<SessionDataCleaner>(named("qr")) { get<QrCodeRepositoryImpl>() }
    singleOf(::QrWidgetStateStoreImpl) { bind<QrWidgetStateStore>() }
    singleOf(::QrAppearancePreferencesImpl) { bind<QrAppearancePreferences>() }
    singleOf(::QrTilePreferencesImpl) { bind<QrTilePreferences>() }
    viewModelOf(::QrCodeViewModel)
}
