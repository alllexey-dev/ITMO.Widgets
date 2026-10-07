package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for what the update check reads from Android: the installed version (`R.string.app_version`, bound in
 * `di/UpdateModule.kt`) and Core 2.0's app area, which only the update check reads. `updateModule` builds the
 * repository, the offer and both ViewModels over them (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccountUpdateBridgeEntryPoint {
    fun installedVersion(): AppVersionName

    /** Unscoped in Hilt over the one `BackendClient`, so each call returns the same API. */
    fun appApi(): AppApi

    companion object {
        fun from(context: Context): AccountUpdateBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, AccountUpdateBridgeEntryPoint::class.java)
    }
}

/**
 * Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. The app asks
 * Backend for the Android release (`version-info?platform=ANDROID`).
 */
val accountUpdateBridgeModule = module {
    single<AppVersionName> { AccountUpdateBridgeEntryPoint.from(androidContext()).installedVersion() }
    single<AppApi> { AccountUpdateBridgeEntryPoint.from(androidContext()).appApi() }
    single<DevicePlatform> { DevicePlatform.ANDROID }
}
