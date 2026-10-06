package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Hilt to Koin for the sport repositories. Hilt constructs each `@Singleton` until the sport data moves to Koin, and
 * the booking and queue repositories are the same instances its `SessionDataCleaner` set clears, so sign-out reaches
 * what the Koin-built screens read (one graph per binding).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SportBridgeEntryPoint {
    fun sportActionRepository(): SportActionRepository
    fun sportBookingRepository(): SportBookingRepository
    fun sportDataRepository(): SportDataRepository
    fun sportScheduleRepository(): SportScheduleRepository
    fun sportSignPreferencesRepository(): SportSignPreferencesRepository
    fun userSportRepository(): UserSportRepository

    companion object {
        fun from(context: Context): SportBridgeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, SportBridgeEntryPoint::class.java)
    }
}

/** Lazy singles: Koin starts before Hilt builds its component, so each one reads Hilt on first use. */
val sportBridgeModule = module {
    single<SportActionRepository> { SportBridgeEntryPoint.from(androidContext()).sportActionRepository() }
    single<SportBookingRepository> { SportBridgeEntryPoint.from(androidContext()).sportBookingRepository() }
    single<SportDataRepository> { SportBridgeEntryPoint.from(androidContext()).sportDataRepository() }
    single<SportScheduleRepository> { SportBridgeEntryPoint.from(androidContext()).sportScheduleRepository() }
    single<SportSignPreferencesRepository> {
        SportBridgeEntryPoint.from(androidContext()).sportSignPreferencesRepository()
    }
    single<UserSportRepository> { SportBridgeEntryPoint.from(androidContext()).userSportRepository() }
}

/**
 * Koin to Hilt for the bookings holder, which `sportModule` constructs: `MainActivity` still takes it from Hilt
 * until L17 moves the sport block. Unscoped on purpose: Koin owns the lifetime and returns its single every time.
 * `ensureStarted`, because Hilt may ask before `Application.onCreate()` finished.
 */
@Module
@InstallIn(SingletonComponent::class)
object SportKoinBridgeModule {

    @Provides
    fun sportBookingsHolder(@ApplicationContext context: Context): SportBookingsHolder =
        KoinStarter.ensureStarted(context).get()
}
