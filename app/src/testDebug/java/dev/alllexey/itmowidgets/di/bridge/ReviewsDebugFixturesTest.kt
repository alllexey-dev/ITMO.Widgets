package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeTeacherReviewsRepository
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
import dev.alllexey.itmowidgets.feature.schedule.data.TeacherLessonsGatewayImpl
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
class ReviewsDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    private val reviews: TeacherReviewsRepository = FakeTeacherReviewsRepository()
    private val lessons: TeacherLessonsGateway = FakeTeacherLessonsGateway()

    @Test
    fun `a fixture replaces the repository and the gateway until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val release = koin.get<TeacherReviewsRepository>()

        val fixture = ReviewsDebugFixtures.load(application, { reviews }, { lessons })
        assertSame(reviews, koin.get<TeacherReviewsRepository>())
        assertSame(lessons, koin.get<TeacherLessonsGateway>())

        ReviewsDebugFixtures.unload(application, fixture)
        // The module's own single again, not a second repository beside the one the session cleaner holds.
        assertSame(release, koin.get<TeacherReviewsRepository>())
        assertSame(koin.get<TeacherReviewsRepositoryImpl>(), koin.get<TeacherReviewsRepository>())
        assertSame(koin.get<TeacherLessonsGatewayImpl>(), koin.get<TeacherLessonsGateway>())
    }

    @Test
    fun `the fakes are read when a view model asks for them`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        var current: TeacherReviewsRepository = reviews
        val fixture = ReviewsDebugFixtures.load(application, { current }, { lessons })

        val next = FakeTeacherReviewsRepository()
        current = next
        assertSame(next, koin.get<TeacherReviewsRepository>())

        ReviewsDebugFixtures.unload(application, fixture)
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val first = ReviewsDebugFixtures.load(application, { reviews }, { lessons })
        val second = ReviewsDebugFixtures.load(application, { reviews }, { lessons })
        ReviewsDebugFixtures.unload(application, first)
        assertSame(reviews, koin.get<TeacherReviewsRepository>())

        ReviewsDebugFixtures.unload(application, second)
        assertNotSame(reviews, koin.get<TeacherReviewsRepository>())
        assertNotSame(lessons, koin.get<TeacherLessonsGateway>())
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
