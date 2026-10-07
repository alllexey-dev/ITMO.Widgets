package dev.alllexey.itmowidgets.feature.update.di

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.app.AppApi
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.presentation.AppUpdateArgs
import org.koin.dsl.module
import platform.Foundation.NSBundle

/**
 * The iOS side of [updateModule], what `AccountUpdateBridge` gives Android: the installed version is the bundle's
 * `CFBundleShortVersionString` (`MARKETING_VERSION`), Core 2.0's app area comes from the one `BackendClient`, and the
 * check asks Backend for the iOS release (`version-info?platform=IOS`, BK-17). Load it with [updateModule].
 */
val updateIosModule = module {
    single { AppVersionName(installedVersion()) }
    single<AppApi> { get<BackendClient>().app }
    single { DevicePlatform.IOS }
}

/**
 * The Koin parameters of the update screen's ViewModel when SwiftUI owns it (IO-08b): the offer the gate found, as
 * the `SavedStateHandle` keys `AppUpdateArgs` reads. Swift passes them to
 * `store.resolve(type: AppUpdateViewModel.self, parameters:)`.
 */
object AppUpdateIosParameters {

    fun viewModel(update: AppUpdate): List<Any> = AppUpdateArgs.of(update).let { args ->
        listOf(
            SavedStateHandle(
                mapOf(
                    AppUpdateArgs.KEY_INSTALLED_VERSION to args.installed,
                    AppUpdateArgs.KEY_LATEST_VERSION to args.latest,
                    AppUpdateArgs.KEY_NOTE to args.note,
                    AppUpdateArgs.KEY_UNSUPPORTED to args.unsupported,
                ),
            ),
        )
    }
}

private fun installedVersion(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
