package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrWidgetImages
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the widget bitmaps: `QrWidgetImages` draws through `QrToolkit`, whose renderer, bitmap cache and
 * colours stay on Hilt until KM-12b, and the widget and its worker read it from Koin.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QrBridgeEntryPoint {
    fun qrWidgetImages(): QrWidgetImages

    companion object {
        fun from(context: Context): QrBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, QrBridgeEntryPoint::class.java)
    }
}

/** Lazy: Koin starts before Hilt builds its component. Unscoped in Hilt and stateless, so Koin keeps the first one. */
val qrBridgeModule = module {
    single<QrWidgetImages> { QrBridgeEntryPoint.from(androidContext()).qrWidgetImages() }
}

/**
 * Koin to Hilt for the QR pass data, which `qrModule` constructs: `QrToolkit`, `QrColorResolver` and the session
 * effects still take them from Hilt. Unscoped on purpose: Koin owns the lifetime and returns its single every time,
 * so the widget and the screen share one repository and one cached pass. `ensureStarted`, because Hilt can build a
 * reader before `Application.onCreate()` has started Koin.
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
}
