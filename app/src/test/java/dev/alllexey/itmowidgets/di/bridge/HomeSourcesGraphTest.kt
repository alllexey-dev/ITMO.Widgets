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
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleChangesHomeCardSource
import dev.alllexey.itmowidgets.feature.schedule.data.home.ScheduleHomeCardSource
import dev.alllexey.itmowidgets.feature.social.data.home.SocialHomeCardSource
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
 * The home feed sees every feature's source exactly once while the sources sit in two graphs: Hilt's `@IntoSet`
 * set behind the composite, and the qualified sources a lane has already moved to Koin (home's hints).
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
            .flatMap { source -> (source as? CompositeHomeCardSource)?.parts ?: listOf(source) }

        assertEquals(
            listOf(
                HintHomeCardSource::class, ScheduleHomeCardSource::class, ScheduleChangesHomeCardSource::class,
                SportHomeCardSource::class, MarksHomeCardSource::class, SocialHomeCardSource::class,
            ).map { it.java.name }.sorted(),
            sources.map { it.javaClass.name }.sorted()
        )
    }

    @Test
    fun `the composite forwards the sources Hilt builds and the feed's stores resolve`() {
        val application = bootApplication()
        val hilt = HomeBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        val composite = koin.get<HomeCardSource>(hiltCardsQualifier) as CompositeHomeCardSource
        assertEquals(hilt.homeCardSources().size, composite.parts.size)
        hilt.homeCardSources().forEach { source -> assertTrue(composite.parts.any { it === source }) }
        assertSame(composite, koin.get<HomeCardSource>(hiltCardsQualifier))
        assertSame(koin.get<DataStoreHomeCardPreferences>(), koin.get<HomeCardPreferences>())
        assertSame(koin.get<DataStoreHomeHintStore>(), koin.get<HomeHintStore>())
    }

    @Test
    fun `the hint source is one Koin single over the Android device status`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val hints = koin.get<HintHomeCardSource>()
        assertSame(hints, koin.get<HomeCardSource>(hintCardsQualifier))
        assertEquals(1, koin.getAll<HomeCardSource>().count { it === hints })
        assertTrue(HomeBridgeEntryPoint.from(application).homeCardSources().none { it is HintHomeCardSource })
        assertTrue(koin.get<HomeHintStatus>() is AndroidHomeHintStatus)
    }

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
