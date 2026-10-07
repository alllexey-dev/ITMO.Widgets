package dev.alllexey.itmowidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.sport.data.debug.DefaultSportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import javax.inject.Singleton

/**
 * What sport keeps on Hilt: the debug lesson templates. The sport data is Koin's (`sportModule`), the FCM handlers
 * of the free and auto queue pushes too (`di/ComponentBindingsModule.kt`); `SportBridge` carries the types between
 * the graphs.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SportModule {

    @Binds
    @Singleton
    abstract fun bindSportLessonTemplateProvider(
        impl: DefaultSportLessonTemplateProvider
    ): SportLessonTemplateProvider
}
