package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsRepositoryImpl
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
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

/** The teacher reviews and levels data on the real graph: `reviewsModule` builds the one repository of each. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ReviewsGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `each contract and its session cleaner are the module's one repository`() {
        bootApplication()
        val koin = GlobalContext.get()
        val reviews = koin.get<TeacherReviewsRepositoryImpl>()
        val levels = koin.get<TeacherLevelsRepositoryImpl>()
        val cleaners = koin.getAll<SessionDataCleaner>()

        assertSame(reviews, koin.get<TeacherReviewsRepository>())
        assertSame(levels, koin.get<TeacherLevelsRepository>())
        assertSame(koin.get<TeacherReviewsRepository>(), koin.get<TeacherReviewsRepository>())
        assertEquals(1, cleaners.count { it === reviews })
        assertEquals(1, cleaners.count { it === levels })
    }

    /** `scheduleDataGraph` holds `reviewsModule` with `DemoMode` and the teacher lessons gateway it reads. */
    @Test
    fun `the reviews module resolves against the bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, scheduleDataGraph)
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
