package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.testing.FakeHomeCardSource
import dev.alllexey.itmowidgets.feature.home.domain.HomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.domain.HomeHintStore
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class HomeDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a fixture replaces the feed's bindings and the clock until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val fixture = HomeDebugFixtures.load(application, Fakes, EpochClock)
        assertEquals(listOf(Fakes.source), koin.getAll<HomeCardSource>())
        assertSame(Fakes.preferences, koin.get<HomeCardPreferences>())
        assertSame(Fakes.hintStore, koin.get<HomeHintStore>())
        assertSame(EpochClock, koin.get<Clock>())

        HomeDebugFixtures.unload(application, fixture)
        val composite = koin.get<HomeCardSource>() as CompositeHomeCardSource
        assertEquals(HomeBridgeEntryPoint.from(application).homeCardSources().size, composite.parts.size)
        assertNotSame(Fakes.preferences, koin.get<HomeCardPreferences>())
        assertNotSame(Fakes.hintStore, koin.get<HomeHintStore>())
        assertSame(CoreBridgeEntryPoint.from(application).clock(), koin.get<Clock>())
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val first = HomeDebugFixtures.load(application, Fakes, EpochClock)
        val second = HomeDebugFixtures.load(application, Fakes, EpochClock)
        HomeDebugFixtures.unload(application, first)
        assertSame(Fakes.source, koin.get<HomeCardSource>())

        HomeDebugFixtures.unload(application, second)
        assertNotSame(Fakes.source, koin.get<HomeCardSource>())
    }

    private object Fakes : HomeDebugFixtures.Fakes {
        val source = FakeHomeCardSource()
        val preferences = object : HomeCardPreferences {
            override fun observeHidden(): Flow<Set<HomeCardKind>> = flowOf(emptySet())
        }
        val hintStore = object : HomeHintStore {
            override fun observeDismissed(): Flow<Set<HomeHint>> = flowOf(emptySet())
            override suspend fun dismiss(hint: HomeHint) = Unit
        }

        override fun source(): HomeCardSource = source
        override fun preferences(): HomeCardPreferences = preferences
        override fun hintStore(): HomeHintStore = hintStore
    }

    private object EpochClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
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
