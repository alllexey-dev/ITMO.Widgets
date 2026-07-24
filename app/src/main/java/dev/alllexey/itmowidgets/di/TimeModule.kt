package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.time.AcademicClock
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.FileAcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.WallClock
import java.time.Clock
import java.time.ZoneId
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    @AcademicClock
    fun provideAcademicClock(): Clock = Clock.system(ZoneId.of("Europe/Moscow"))

    @Provides
    @Singleton
    @WallClock
    fun provideWallClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideAcademicTimeOverrideStore(
        @ApplicationContext context: Context
    ): AcademicTimeOverrideStore = FileAcademicTimeOverrideStore(
        File(context.noBackupFilesDir, ACADEMIC_TIME_OVERRIDE_FILE)
    )

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

    private const val ACADEMIC_TIME_OVERRIDE_FILE = "debug/academic_date_override"
}
