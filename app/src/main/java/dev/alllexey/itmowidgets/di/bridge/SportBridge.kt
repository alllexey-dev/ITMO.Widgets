package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.sport.PendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportScoreOverridePoints
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportScoreOverrideSource
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushBooker
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the app-only inputs of the sport data, which `sportModule` constructs: the debug lesson templates
 * (`DefaultSportLessonTemplateProvider`) and the debug score override of `core/debug`, which stay in `:app` behind the
 * sport ports `SportLessonTemplateProvider` and `SportScoreOverrideSource`. Release builds read no override.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SportBridgeEntryPoint {
    fun sportLessonTemplateProvider(): SportLessonTemplateProvider
    fun sportScoreOverrideProvider(): SportScoreOverrideProvider

    companion object {
        fun from(context: Context): SportBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, SportBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val sportBridgeModule = module {
    single<SportLessonTemplateProvider> { SportBridgeEntryPoint.from(androidContext()).sportLessonTemplateProvider() }
    single<SportScoreOverrideSource> {
        val provider = SportBridgeEntryPoint.from(androidContext()).sportScoreOverrideProvider()
        SportScoreOverrideSource {
            provider.getOverride()?.let { SportScoreOverridePoints(attendances = it.attendances, bonus = it.bonus) }
        }
    }
}

/**
 * Koin to Hilt for what `sportModule` constructs and Hilt-built code still injects: the bookings holder
 * (`MainActivity`, until L17 moves the sport block), the pending queues (the schedule's home card and widget), the
 * score as other features read it and the push booking decision (the FCM handlers in `SportModule`). Unscoped on
 * purpose: Koin owns the lifetime and returns its single every time. `ensureStarted`, because a worker or a widget
 * broadcast can run before `Application.onCreate()`.
 */
@Module
@InstallIn(SingletonComponent::class)
object SportKoinBridgeModule {

    @Provides
    fun sportBookingsHolder(@ApplicationContext context: Context): SportBookingsHolder =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun pendingSportBookingsRepository(@ApplicationContext context: Context): PendingSportBookingsRepository =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun sportScoreRepository(@ApplicationContext context: Context): SportScoreRepository =
        KoinStarter.ensureStarted(context).get()

    @Provides
    fun sportSignPushBooker(@ApplicationContext context: Context): SportSignPushBooker =
        KoinStarter.ensureStarted(context).get()
}
