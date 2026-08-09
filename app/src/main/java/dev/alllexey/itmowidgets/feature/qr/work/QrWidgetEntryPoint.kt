package dev.alllexey.itmowidgets.feature.qr.work

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrWidgetStateStore
import dev.alllexey.itmowidgets.feature.qr.ui.widget.QrWidgetImages

/**
 * Dependencies for the widget workers.
 *
 * Workers are built by WorkManager, so they cannot use constructor injection.
 * `androidx.hilt`'s `@HiltWorker` would solve that, but its annotation processor
 * cannot read Kotlin 2.0 metadata under kapt; an entry point needs no extra
 * dependency and no custom `WorkManager` configuration.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface QrWidgetEntryPoint {

    fun qrCodeRepository(): QrCodeRepository

    fun qrWidgetStateStore(): QrWidgetStateStore

    fun qrWidgetImages(): QrWidgetImages

    fun qrAppearancePreferences(): QrAppearancePreferences

    companion object {

        fun from(context: Context): QrWidgetEntryPoint {
            return EntryPointAccessors.fromApplication(
                context.applicationContext,
                QrWidgetEntryPoint::class.java
            )
        }
    }
}
