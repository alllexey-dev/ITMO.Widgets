package dev.alllexey.itmowidgets.di

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.update.ui.InstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.PlayInstallStateWatcher
import dev.alllexey.itmowidgets.feature.update.ui.PlayUpdateAction
import dev.alllexey.itmowidgets.feature.update.ui.ReleasePageOpener
import dev.alllexey.itmowidgets.feature.update.ui.UpdateAction
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateActionModule {

    @Provides
    @Singleton
    fun provideAppUpdateManager(@ApplicationContext context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context)

    @Provides
    fun provideUpdateAction(
        @ApplicationContext context: Context,
        manager: AppUpdateManager,
        pages: ReleasePageOpener
    ): UpdateAction = PlayUpdateAction(manager, pages, ContextCompat.getMainExecutor(context))

    @Provides
    fun provideInstallStateWatcher(
        @ApplicationContext context: Context,
        manager: AppUpdateManager
    ): InstallStateWatcher = PlayInstallStateWatcher(manager, ContextCompat.getMainExecutor(context))
}
