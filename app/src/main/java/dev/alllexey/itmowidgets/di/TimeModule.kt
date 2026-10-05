package dev.alllexey.itmowidgets.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.storage.AtomicTextFile
import dev.alllexey.itmowidgets.core.time.AcademicClock
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideController
import dev.alllexey.itmowidgets.core.time.AcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.DefaultAcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.FileAcademicTimeOverrideStore
import dev.alllexey.itmowidgets.core.time.WallClock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaZoneId
import java.io.File
import javax.inject.Singleton
import kotlin.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    /** The wall clock; [AcademicTimeProvider] reads it in the academic zone. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System

    @Provides
    @Singleton
    fun provideAcademicTimeZone(): TimeZone = TimeZone.of(ACADEMIC_TIME_ZONE)

    @Provides
    @Singleton
    @AcademicClock
    fun provideAcademicClock(zone: TimeZone): java.time.Clock = java.time.Clock.system(zone.toJavaZoneId())

    @Provides
    @Singleton
    @WallClock
    fun provideWallClock(): java.time.Clock = java.time.Clock.systemUTC()

    @Provides
    @Singleton
    fun provideAcademicTimeOverrideStore(
        @ApplicationContext context: Context
    ): AcademicTimeOverrideStore = FileAcademicTimeOverrideStore(
        AtomicTextFile(File(context.noBackupFilesDir, ACADEMIC_TIME_OVERRIDE_FILE))
    )

    @Provides
    @Singleton
    fun provideDefaultAcademicTimeProvider(
        clock: Clock,
        zone: TimeZone,
        overrideStore: AcademicTimeOverrideStore
    ): DefaultAcademicTimeProvider = DefaultAcademicTimeProvider(clock, zone, overrideStore)

    @Provides
    fun provideAcademicTimeProvider(
        provider: DefaultAcademicTimeProvider
    ): AcademicTimeProvider = provider

    @Provides
    fun provideAcademicTimeOverrideController(
        provider: DefaultAcademicTimeProvider
    ): AcademicTimeOverrideController = provider

    private const val ACADEMIC_TIME_ZONE = "Europe/Moscow"
    private const val ACADEMIC_TIME_OVERRIDE_FILE = "debug/academic_date_override"
}
