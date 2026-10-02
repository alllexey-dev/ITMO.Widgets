package dev.alllexey.itmowidgets.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.navigation.ShareLinkFactory

@Module
@InstallIn(SingletonComponent::class)
object LinksModule {

    @Provides
    fun provideShareLinkFactory(): ShareLinkFactory = ShareLinkFactory(BuildConfig.WIDGETS_BASE_URL)
}
