package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.notification.FcmPayloadDispatcher
import dev.alllexey.itmowidgets.core.notification.FcmPayloadHandler
import dev.alllexey.itmowidgets.feature.social.data.push.FriendshipPushHandler
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
import dev.alllexey.itmowidgets.feature.sport.data.debug.SportLessonTemplateProvider
import dev.alllexey.itmowidgets.feature.sport.data.home.SportHomeCardSource
import dev.alllexey.itmowidgets.feature.sport.data.push.SportSignPushHandler
import dev.alllexey.itmowidgets.feature.sport.data.repository.PendingSportBookingsRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportBookingRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportDataRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.repository.SportScoreRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.di.sportCardsQualifier
import dev.alllexey.itmowidgets.feature.sport.di.sportModule
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingsHolder
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
 * Koin builds the sport data once (`sportModule`): sign-out clears the instances the screens read, the home feed and
 * Hilt's readers get those instances too, and the app-only debug inputs come from Hilt. Read through the debug-only
 * identity entry point of the real graph, as `SessionCleanersBridgeTest` does; nothing is refreshed or cleared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SportBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `sign-out clears the booking and queue repositories the screens read, once each`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val cleaners = identity(application).sessionDataCleaners()

        assertSame(cleaners.filterIsInstance<SportBookingRepositoryImpl>().single(), koin.get<SportBookingRepository>())
        assertSame(cleaners.filterIsInstance<SportDataRepositoryImpl>().single(), koin.get<SportDataRepository>())
        assertEquals(cleaners.size, cleaners.toSet().size)
    }

    @Test
    fun `the sport home card reaches the feed once, from Koin`() {
        bootApplication()
        val koin = GlobalContext.get()
        val source = koin.get<SportHomeCardSource>()

        assertSame(source, koin.get<HomeCardSource>(sportCardsQualifier))
        assertEquals(1, koin.getAll<HomeCardSource>().count { it === source })
        val hiltSources = (koin.get<HomeCardSource>(hiltCardsQualifier) as CompositeHomeCardSource).parts
        assertTrue(hiltSources.none { it is SportHomeCardSource })
    }

    @Test
    fun `Hilt's readers get the instances Koin builds`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        // The providers Hilt calls; unscoped, so every call asks Koin.
        assertSame(koin.get<SportBookingsHolder>(), SportKoinBridgeModule.sportBookingsHolder(application))
        assertSame(
            koin.get<PendingSportBookingsRepositoryImpl>(),
            SportKoinBridgeModule.pendingSportBookingsRepository(application)
        )
        assertSame(koin.get<SportScoreRepositoryImpl>(), SportKoinBridgeModule.sportScoreRepository(application))
        assertSame(
            SportKoinBridgeModule.sportBookingsHolder(application),
            SportKoinBridgeModule.sportBookingsHolder(application)
        )
    }

    @Test
    fun `the push handlers build over Koin's booker and the debug inputs come from Hilt`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        // Koin builds the FCM dispatcher over the open handler set; a duplicate type would fail its construction.
        koin.get<FcmPayloadDispatcher>()
        val handlers = koin.getAll<FcmPayloadHandler>()
        assertEquals(2, handlers.count { it is SportSignPushHandler })
        assertEquals(1, handlers.count { it is FriendshipPushHandler })
        assertEquals(3, handlers.map { it.type }.toSet().size)
        assertSame(
            SportBridgeEntryPoint.from(application).sportLessonTemplateProvider(),
            GlobalContext.get().get<SportLessonTemplateProvider>()
        )
    }

    /** The data reads the opt-in (`settingsDataModule`) and the friends (`friendSelectorModule`, `socialModule`). */
    @Test
    fun `the sport module passes the graph check against the release bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(sportModule) + scheduleDataGraph)
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
