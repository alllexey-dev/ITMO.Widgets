package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrTilePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore

/**
 * Koin to Hilt for the QR pass data, which `qrModule` constructs: `QrWidgetEntryPoint`, `QrToolkit`,
 * `QrColorResolver`, `QrTileController`, `QrWidgetUpdateWorker` and the session effects still take them from Hilt.
 * Unscoped on purpose: Koin owns the lifetime and returns its single every time, so the widget and the screen share
 * one repository and one cached pass. `ensureStarted`, because a worker or a widget broadcast can run before
 * `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object QrBridge {

    @Provides
    fun qrCodeRepository(@ApplicationContext context: Context): QrCodeRepository =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun qrWidgetStateStore(@ApplicationContext context: Context): QrWidgetStateStore =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun qrAppearancePreferences(@ApplicationContext context: Context): QrAppearancePreferences =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun qrTilePreferences(@ApplicationContext context: Context): QrTilePreferences =
        KoinStarter.ensureStarted(context).get()
}
