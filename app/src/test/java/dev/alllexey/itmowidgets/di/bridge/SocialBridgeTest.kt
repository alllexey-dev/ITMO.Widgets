package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.feature.friendselector.di.friendSelectorModule
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.social.di.socialModule
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
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
    fun `the social repositories resolve in Koin to the instances Hilt builds`() {
        val application = bootApplication()
        val hilt = SocialBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.socialRepository(), koin.get<SocialRepository>())
        assertSame(hilt.peopleSearchRepository(), koin.get<PeopleSearchRepository>())
        assertSame(hilt.friendRepository(), koin.get<FriendRepository>())
        assertSame(hilt.personRepository(), koin.get<PersonRepository>())
        assertSame(hilt.friendSelectionHistory(), koin.get<FriendSelectionHistory>())
        assertSame(koin.get<SocialRepository>(), koin.get<SocialRepository>())
    }

    @Test
    fun `the social and picker modules resolve against the bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(socialModule, friendSelectorModule))
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
