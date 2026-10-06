package dev.alllexey.itmowidgets.feature.sport.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SportSessionBindingsTest {

    @Test
    fun sessionCleanupTargetsTheExactSportRepositoryInstancesUsedByScreens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val koin = KoinStarter.ensureStarted(context)
        val bookings = koin.get<SportBookingRepository>()
        val data = koin.get<SportDataRepository>()
        val cleaners = EntryPointAccessors.fromApplication(context, SportSessionBindingsEntryPoint::class.java)
            .sessionDataCleaners()

        // Identity only: never call refresh or clear against the device's real session.
        assertSame(bookings, cleaners.filterIsInstance<SportBookingRepositoryImpl>().single())
        assertSame(data, cleaners.filterIsInstance<SportDataRepositoryImpl>().single())
        assertSame(bookings, koin.get<SportBookingRepository>())
        assertSame(data, koin.get<SportDataRepository>())
    }
}
