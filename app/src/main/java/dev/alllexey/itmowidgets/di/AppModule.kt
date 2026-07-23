package dev.alllexey.itmowidgets.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.debug.DefaultSportLessonTemplateProvider
import dev.alllexey.itmowidgets.core.debug.DefaultSportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.SharedPreferencesSportLessonTemplateStore
import dev.alllexey.itmowidgets.core.debug.SharedPreferencesSportScoreOverrideStore
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateStore
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideStore
import dev.alllexey.itmowidgets.core.time.AcademicClock
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.SharedPreferencesAcademicTimeOverrideStore
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    @Provides
    @Singleton
    @AcademicClock
    fun provideAcademicClock(): Clock = Clock.system(ZoneId.of("Europe/Moscow"))

    @Provides
    @Singleton
    fun provideAcademicTimeOverrideStore(
        preferences: SharedPreferences
    ): AcademicTimeOverrideStore = SharedPreferencesAcademicTimeOverrideStore(preferences)

    @Provides
    @Singleton
    fun provideDefaultAcademicTimeProvider(
        @AcademicClock clock: Clock,
        overrideStore: AcademicTimeOverrideStore
    ): DefaultAcademicTimeProvider = DefaultAcademicTimeProvider(clock, overrideStore)

    @Provides
    fun provideAcademicTimeProvider(
        provider: DefaultAcademicTimeProvider
    ): AcademicTimeProvider = provider

    @Provides
    fun provideAcademicTimeOverrideController(
        provider: DefaultAcademicTimeProvider
    ): AcademicTimeOverrideController = provider

    @Provides
    @Singleton
    fun provideSportScoreOverrideStore(
        preferences: SharedPreferences
    ): SportScoreOverrideStore = SharedPreferencesSportScoreOverrideStore(preferences)

    @Provides
    @Singleton
    fun provideDefaultSportScoreOverrideProvider(
        overrideStore: SportScoreOverrideStore
    ): DefaultSportScoreOverrideProvider = DefaultSportScoreOverrideProvider(overrideStore)

    @Provides
    fun provideSportScoreOverrideProvider(
        provider: DefaultSportScoreOverrideProvider
    ): SportScoreOverrideProvider = provider

    @Provides
    fun provideSportScoreOverrideController(
        provider: DefaultSportScoreOverrideProvider
    ): SportScoreOverrideController = provider

    @Provides
    @Singleton
    fun provideSportLessonTemplateStore(
        preferences: SharedPreferences
    ): SportLessonTemplateStore = SharedPreferencesSportLessonTemplateStore(preferences)

    @Provides
    @Singleton
    fun provideDefaultSportLessonTemplateProvider(
        timeProvider: AcademicTimeProvider,
        templateStore: SportLessonTemplateStore
    ): DefaultSportLessonTemplateProvider =
        DefaultSportLessonTemplateProvider(timeProvider, templateStore)

    @Provides
    fun provideSportLessonTemplateProvider(
        provider: DefaultSportLessonTemplateProvider
    ): SportLessonTemplateProvider = provider

    @Provides
    fun provideSportLessonTemplateController(
        provider: DefaultSportLessonTemplateProvider
    ): SportLessonTemplateController = provider
}
