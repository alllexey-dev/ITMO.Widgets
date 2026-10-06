package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.feature.friendselector.data.DataStoreFriendSelectionHistory
import dev.alllexey.itmowidgets.feature.social.data.PersonRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * The social data on the real graph: Hilt's remaining readers get Koin's singles through `SocialBridge`, and
 * sign-out (Hilt's `Set<SessionDataCleaner>`, which holds Koin's qualified cleaners through `SessionCleanersBridge`)
 * forgets every social cache and the picker's recent friends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SocialSessionBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `Hilt and Koin hand out the same social repository and friend list`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val hilt = EntryPointAccessors.fromApplication(application, SocialHiltBindingsEntryPoint::class.java)

        assertSame(koin.get<SocialRepository>(), hilt.socialRepository())
        assertSame(hilt.socialRepository(), hilt.socialRepository())
        assertSame(koin.get<FriendRepository>(), hilt.friendRepository())
    }

    @Test
    fun `the cleaner set holds each social cleaner once`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val cleaners = cleaners(application)

        assertEquals(1, cleaners.count { it === koin.get<SocialRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<PersonRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<DataStoreFriendSelectionHistory>() })
    }

    @Test
    fun `sign-out clears every social cache and the recent friends`() = runBlocking {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val dataStore = koin.get<DataStore<Preferences>>()
        val social = koin.get<SocialRepositoryImpl>()
        val people = koin.get<PersonRepositoryImpl>()
        val history = koin.get<DataStoreFriendSelectionHistory>()
        // The demo session fills the caches without Backend or My ITMO.
        DemoPreferences(dataStore).setDemoActive(true)
        withTimeout(TIMEOUT_MS) { while (social.currentFriends == null) { social.refresh(); delay(10) } }
        social.profile(DemoPeople.ARTEM.isu)
        social.userFriends(DemoPeople.IVAN.isu)
        people.person(DemoPeople.MARIA.isu)
        history.record(DemoPeople.IVAN.isu)
        assertNotNull(social.cachedProfile(DemoPeople.ARTEM.isu))
        assertNotNull(social.cachedUserFriends(DemoPeople.IVAN.isu))
        assertNotNull(people.cachedPerson(DemoPeople.MARIA.isu))
        assertEquals(listOf(DemoPeople.IVAN.isu), history.getRecentIsu())

        cleaners(application).forEach { it.clearSessionData() }

        assertNull(social.cachedProfile(DemoPeople.ARTEM.isu))
        assertNull(social.cachedUserFriends(DemoPeople.IVAN.isu))
        assertNull(social.currentFriends)
        // Loading, or Disabled once a cleaner ends the demo session and the opt-in follower runs.
        assertTrue(social.observeFriends().first() !is LoadState.Content)
        assertTrue(social.observeRequests().first() !is LoadState.Content)
        assertNull(social.observeCurrentUser().first())
        assertNull(people.cachedPerson(DemoPeople.MARIA.isu))
        assertEquals(emptyList<Int>(), history.getRecentIsu())
        assertNull(dataStore.data.first()[stringPreferencesKey("recent_schedule_friends")])
    }

    private fun cleaners(context: Context): Set<SessionDataCleaner> =
        EntryPointAccessors.fromApplication(context, SportSessionBindingsEntryPoint::class.java).sessionDataCleaners()

    /** As in `KoinStartTest`: Robolectric's `onCreate()` stops at `FcmWork.syncToken` after Koin and Hilt are up. */
    private fun bootApplication(): ItmoWidgetsApplication {
        val failure = runCatching { ApplicationProvider.getApplicationContext<Context>() }.exceptionOrNull()
        if (failure != null) {
            val causes = generateSequence(failure) { it.cause }
            assertTrue(failure.stackTraceToString(), causes.any { "WorkManager" in it.message.orEmpty() })
        }
        return GlobalContext.get().get<Context>() as ItmoWidgetsApplication
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
