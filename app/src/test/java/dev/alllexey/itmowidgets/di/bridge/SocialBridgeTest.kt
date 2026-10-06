package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.home.HomeCardSource
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.friendselector.data.DataStoreFriendSelectionHistory
import dev.alllexey.itmowidgets.feature.friendselector.data.FriendRepositoryImpl
import dev.alllexey.itmowidgets.feature.friendselector.di.friendSelectorModule
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.reviews.di.reviewsModule
import dev.alllexey.itmowidgets.feature.settings.di.settingsDataModule
import dev.alllexey.itmowidgets.feature.social.data.PeopleSearchRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.PersonRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.SocialRepositoryImpl
import dev.alllexey.itmowidgets.feature.social.data.home.SocialHomeCardSource
import dev.alllexey.itmowidgets.feature.social.di.socialCardsQualifier
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class SocialBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `each social port is the one single its Koin module builds`() {
        bootApplication()
        val koin = GlobalContext.get()

        assertSame(koin.get<SocialRepositoryImpl>(), koin.get<SocialRepository>())
        assertSame(koin.get<PersonRepositoryImpl>(), koin.get<PersonRepository>())
        assertSame(koin.get<PeopleSearchRepositoryImpl>(), koin.get<PeopleSearchRepository>())
        assertSame(koin.get<FriendRepositoryImpl>(), koin.get<FriendRepository>())
        assertSame(koin.get<DataStoreFriendSelectionHistory>(), koin.get<FriendSelectionHistory>())
        assertSame(koin.get<SocialRepository>(), koin.get<SocialRepository>())
    }

    @Test
    fun `the cleaners and the home card forward to the same singles`() {
        bootApplication()
        val koin = GlobalContext.get()
        val cleaners = koin.getAll<SessionDataCleaner>()
        val cards = koin.getAll<HomeCardSource>()

        assertEquals(1, cleaners.count { it === koin.get<SocialRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<PersonRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<DataStoreFriendSelectionHistory>() })
        assertSame(koin.get<SocialHomeCardSource>(), koin.get<HomeCardSource>(socialCardsQualifier))
        assertEquals(1, cards.count { it === koin.get<SocialHomeCardSource>() })
    }

    /** The repository reads the services opt-in, which `settingsDataModule` constructs since KM-11e. */
    @Test
    fun `the social and picker modules resolve against the bridges`() {
        KoinGraphCheck.assertValid(
            KoinModules.bridges,
            listOf(authDataModule, settingsDataModule, reviewsModule, socialModule, friendSelectorModule),
        )
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
