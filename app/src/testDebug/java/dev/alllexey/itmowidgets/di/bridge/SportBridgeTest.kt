package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.di.sportModule
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportActionRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

/**
 * The sport screens Koin builds read the repositories Hilt builds, and the booking and queue ones are the members of
 * Hilt's `Set<SessionDataCleaner>`, so sign-out clears what the screens show (one graph per binding). Read through
 * the debug-only identity entry point of the real graph, as `SessionCleanersBridgeTest` does; nothing is refreshed
 * or cleared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SportBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the sport screens read the repositories the session cleaners clear`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val cleaners = identity(application).sessionDataCleaners()

        assertSame(cleaners.filterIsInstance<SportBookingRepositoryImpl>().single(), koin.get<SportBookingRepository>())
        assertSame(cleaners.filterIsInstance<SportDataRepositoryImpl>().single(), koin.get<SportDataRepository>())
    }

    @Test
    fun `every sport repository resolves in Koin to the instance Hilt builds`() {
        val application = bootApplication()
        val hilt = SportBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.sportActionRepository(), koin.get<SportActionRepository>())
        assertSame(hilt.sportBookingRepository(), koin.get<SportBookingRepository>())
        assertSame(hilt.sportDataRepository(), koin.get<SportDataRepository>())
        assertSame(hilt.sportScheduleRepository(), koin.get<SportScheduleRepository>())
        assertSame(hilt.sportSignPreferencesRepository(), koin.get<SportSignPreferencesRepository>())
        assertSame(hilt.userSportRepository(), koin.get<UserSportRepository>())
    }

    @Test
    fun `MainActivity's bookings holder is the one Koin builds`() {
        val application = bootApplication()
        val holder = GlobalContext.get().get<SportBookingsHolder>()

        // The provider Hilt calls for `MainActivity.sportBookingsHolder`; unscoped, so every call asks Koin.
        assertSame(holder, SportKoinBridgeModule.sportBookingsHolder(application))
        assertSame(holder, SportKoinBridgeModule.sportBookingsHolder(application))
    }

    @Test
    fun `the sport module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(sportModule))
    }

    private fun identity(context: Context): SportSessionBindingsEntryPoint =
        EntryPointAccessors.fromApplication(context, SportSessionBindingsEntryPoint::class.java)

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
