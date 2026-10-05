package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the QR pass screen. Hilt constructs the one `@Singleton` repository that the widget, the worker
 * and its `SessionDataCleaner` contribution also hold, so the screen's Koin ViewModel shares it (one graph per
 * binding); the colour setting is the one the widget reads. The data moves to `:shared:feature-qr` in KM-11g1.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QrBridgeEntryPoint {
    fun qrCodeRepository(): QrCodeRepository

    fun qrAppearancePreferences(): QrAppearancePreferences

    companion object {
        fun from(context: Context): QrBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, QrBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so they read Hilt on first use. */
val qrBridgeModule = module {
    single<QrCodeRepository> { QrBridgeEntryPoint.from(androidContext()).qrCodeRepository() }
    single<QrAppearancePreferences> { QrBridgeEntryPoint.from(androidContext()).qrAppearancePreferences() }
}
