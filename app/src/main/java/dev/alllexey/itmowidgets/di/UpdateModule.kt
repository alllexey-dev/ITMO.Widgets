package dev.alllexey.itmowidgets.di

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.client.device.DevicePlatform
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.ui.ActivityReleasePageOpener
import dev.alllexey.itmowidgets.feature.update.ui.ReleasePageOpener
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * The Android side of the update: the release page, the installed version and the platform the app asks Backend
 * about (`version-info?platform=ANDROID`); `updateModule` does the rest, the flavor's `updateActionModule` installs.
 */
val androidUpdateModule = module {
    factoryOf(::ActivityReleasePageOpener) { bind<ReleasePageOpener>() }
    single { AppVersionName(androidContext().getString(R.string.app_version)) }
    single { DevicePlatform.ANDROID }
}
