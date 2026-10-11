package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.feature.home.data.AndroidHomeHintStatus
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.data.DataStoreHomeHintStore
import dev.alllexey.itmowidgets.feature.home.data.HintHomeCardSource
import dev.alllexey.itmowidgets.feature.home.di.hintCardsQualifier
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStatus
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import dev.alllexey.itmowidgets.feature.recordbook.data.home.MarksHomeCardSource
import dev.alllexey.itmowidgets.feature.recordbook.di.marksCardsQualifier
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleChangesHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleCardsQualifier
import dev.alllexey.itmowidgets.feature.schedule.di.scheduleChangesCardsQualifier
import dev.alllexey.itmowidgets.feature.social.data.home.SocialHomeCardSource
import dev.alllexey.itmowidgets.feature.social.di.socialCardsQualifier
import dev.alllexey.itmowidgets.feature.sport.data.home.SportHomeCardSource
import org.junit.Assert.assertEquals
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
 * The home feed sees every feature's source exactly once: each is a qualified Koin source (home's hints, social's
 * friend requests, the schedule's two cards, the recordbook's new marks, sport's score and queues).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class HomeSourcesGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `every home card source reaches the feed exactly once`() {
        bootApplication()

        val sources = GlobalContext.get().getAll<HomeCardSource>()

        assertEquals(
            listOf(
                HintHomeCardSource::class, ScheduleHomeCardSource::class, ScheduleChangesHomeCardSource::class,
                SportHomeCardSource::class, MarksHomeCardSource::class, SocialHomeCardSource::class,
            ).map { it.java.name }.sorted(),
            sources.map { it.javaClass.name }.sorted()
        )
    }

    @Test
    fun `the feed's stores resolve`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<DataStoreHomeCardPreferences>(), koin.get<HomeCardPreferences>())
        assertSame(koin.get<DataStoreHomeHintStore>(), koin.get<HomeHintStore>())
    }

    @Test
    fun `the hint source is one Koin single over the Android device status`() {
        bootApplication()
        val koin = GlobalContext.get()

        val hints = koin.get<HintHomeCardSource>()
        assertSame(hints, koin.get<HomeCardSource>(hintCardsQualifier))
        assertEquals(1, koin.getAll<HomeCardSource>().count { it === hints })
        assertTrue(koin.get<HomeHintStatus>() is AndroidHomeHintStatus)
    }

    @Test
    fun `the friend-requests source is one Koin single`() {
        bootApplication()
        val koin = GlobalContext.get()

        val social = koin.get<SocialHomeCardSource>()
        assertSame(social, koin.get<HomeCardSource>(socialCardsQualifier))
        assertEquals(1, koin.getAll<HomeCardSource>().count { it === social })
    }

    @Test
    fun `the schedule sources are Koin singles`() {
        bootApplication()
        val koin = GlobalContext.get()

        val schedule = koin.get<ScheduleHomeCardSource>()
        val changes = koin.get<ScheduleChangesHomeCardSource>()
        assertSame(schedule, koin.get<HomeCardSource>(scheduleCardsQualifier))
        assertSame(changes, koin.get<HomeCardSource>(scheduleChangesCardsQualifier))
        assertEquals(1, koin.getAll<HomeCardSource>().count { it === schedule })
        assertEquals(1, koin.getAll<HomeCardSource>().count { it === changes })
    }

    @Test
    fun `the new-marks source is one Koin single`() {
        bootApplication()
        val koin = GlobalContext.get()

        val marks = koin.get<MarksHomeCardSource>()
        assertSame(marks, koin.get<HomeCardSource>(marksCardsQualifier))
        assertEquals(1, koin.getAll<HomeCardSource>().count { it === marks })
    }

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin is up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }
}
