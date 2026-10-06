package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeRepository
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.feature.social.domain.PersonRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.emptyFlow
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
class SocialDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `a fixture replaces the provided types until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val social = FakeSocialRepository()
        val reviews = FakeTeacherReviewsRepository()

        val fixture = SocialDebugFixtures.load(
            application,
            SocialDebugFixtures.Fakes(social = { social }, reviews = { reviews }, currentUser = { Visitor }),
        )
        assertSame(social, koin.get<SocialRepository>())
        assertSame(reviews, koin.get<TeacherReviewsRepository>())
        assertSame(Visitor, koin.get<CurrentUserProvider>())
        val bridged = SocialBridgeEntryPoint.from(application)
        assertSame(bridged.personRepository(), koin.get<PersonRepository>())

        SocialDebugFixtures.unload(application, fixture)
        assertSame(bridged.socialRepository(), koin.get<SocialRepository>())
        assertSame(bridged.peopleSearchRepository(), koin.get<PeopleSearchRepository>())
        assertSame(bridged.friendRepository(), koin.get<FriendRepository>())
        assertSame(bridged.personRepository(), koin.get<PersonRepository>())
        assertSame(bridged.friendSelectionHistory(), koin.get<FriendSelectionHistory>())
        assertSame(
            ReviewsBridgeEntryPoint.from(application).teacherReviewsRepository(),
            koin.get<TeacherReviewsRepository>(),
        )
        assertSame(CoreBridgeEntryPoint.from(application).currentUserProvider(), koin.get<CurrentUserProvider>())
    }

    @Test
    fun `each new ViewModel reads the host's fake at that moment`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        var current = FakeSocialRepository()

        val fixture = SocialDebugFixtures.load(application, SocialDebugFixtures.Fakes(social = { current }))
        val first = koin.get<SocialRepository>()
        current = FakeSocialRepository()
        assertSame(current, koin.get<SocialRepository>())
        assertNotSame(first, koin.get<SocialRepository>())

        SocialDebugFixtures.unload(application, fixture)
    }

    @Test
    fun `unloading leaves the wall clock another fixture of the host overrides`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val qr = QrDebugFixtures.load(application, NoQr, EpochClock)
        val social = SocialDebugFixtures.load(application, SocialDebugFixtures.Fakes(currentUser = { Visitor }))

        SocialDebugFixtures.unload(application, social)
        assertSame(EpochClock, koin.get<Clock>())

        QrDebugFixtures.unload(application, qr)
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val social = FakeSocialRepository()

        val first = SocialDebugFixtures.load(application, SocialDebugFixtures.Fakes(social = { social }))
        val second = SocialDebugFixtures.load(application, SocialDebugFixtures.Fakes(social = { social }))
        SocialDebugFixtures.unload(application, first)
        assertSame(social, koin.get<SocialRepository>())

        SocialDebugFixtures.unload(application, second)
        assertNotSame(social, koin.get<SocialRepository>())
    }

    private object Visitor : CurrentUserProvider {
        override suspend fun getCurrentUser() = CurrentUser(1, "Гость", null)
    }

    private object EpochClock : Clock {
        override fun now() = Instant.fromEpochMilliseconds(0)
    }

    private object NoQr : QrCodeRepository {
        override suspend fun currentQr(): QrCodeSnapshot? = null
        override fun observeQrHex() = emptyFlow<String>()
        override suspend fun currentQrHex(allowExpired: Boolean): String? = null
        override suspend fun refreshQrHex(force: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override fun clearCache() = Unit
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
