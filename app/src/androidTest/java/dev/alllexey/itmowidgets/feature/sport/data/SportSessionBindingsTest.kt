package dev.alllexey.itmowidgets.feature.sport.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SportSessionBindingsTest {

    @Test
    fun sessionCleanupTargetsTheExactSportRepositoryInstancesUsedByScreens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val bindings = EntryPointAccessors.fromApplication(context, SportSessionBindingsEntryPoint::class.java)
        val bookings = bindings.sportBookings()
        val data = bindings.sportData()
        val cleaners = bindings.sessionDataCleaners()

        // Identity only: never call refresh or clear against the device's real session.
        assertSame(bookings, cleaners.filterIsInstance<SportBookingRepositoryImpl>().single())
        assertSame(data, cleaners.filterIsInstance<SportDataRepositoryImpl>().single())
        assertSame(bookings, bindings.sportBookings())
        assertSame(data, bindings.sportData())
    }
}
