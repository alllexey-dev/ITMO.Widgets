package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import dev.alllexey.itmowidgets.feature.update.ui.ActivityReleasePageOpener
import dev.alllexey.itmowidgets.feature.update.ui.ReleasePageOpener

/** The Android side of the update: the release page and the installed version; Koin's `updateModule` does the rest. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    abstract fun bindReleasePageOpener(opener: ActivityReleasePageOpener): ReleasePageOpener

    companion object {

        @Provides
        fun provideInstalledVersion(
            @ApplicationContext context: Context
        ): AppVersionName = AppVersionName(context.getString(R.string.app_version))
    }
}
