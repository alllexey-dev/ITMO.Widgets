package dev.alllexey.itmowidgets.di

import androidx.core.content.ContextCompat
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dev.alllexey.itmowidgets.feature.update.data.PlayUpdateAvailability
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdateAvailability
import dev.alllexey.itmowidgets.feature.update.ui.AppUpdateCopy
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.PlayInstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.PlayUpdateAction
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The play flavor's update, all of it through Google Play over one `AppUpdateManager`: the offer opens only when
 * Play has the update, reads about Google Play and starts Play's in-app update flow.
 */
val updateActionModule = module {
    single<AppUpdateManager> { AppUpdateManagerFactory.create(androidContext()) }
    factory<AppUpdateAvailability> {
        PlayUpdateAvailability(get(), ContextCompat.getMainExecutor(androidContext()), get())
    }
    single { AppUpdateCopy.GooglePlay }
    factory<UpdateAction> {
        PlayUpdateAction(get(), get(), ContextCompat.getMainExecutor(androidContext()))
    }
    factory<InstallStateWatcher> {
        PlayInstallStateWatcher(get(), ContextCompat.getMainExecutor(androidContext()))
    }
}
