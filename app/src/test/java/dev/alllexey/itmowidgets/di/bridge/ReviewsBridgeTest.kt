package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelsRepository
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import dev.alllexey.itmowidgets.feature.reviews.di.reviewsModule
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.experimental.LazyApplication
import org.robolectric.annotation.experimental.LazyApplication.LazyLoad

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ReviewsBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the teacher reviews repository resolves in Koin to the instance Hilt builds`() {
        val application = bootApplication()
        val hilt = ReviewsBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.teacherReviewsRepository(), koin.get<TeacherReviewsRepository>())
        assertSame(koin.get<TeacherReviewsRepository>(), koin.get<TeacherReviewsRepository>())
    }

    @Test
    fun `the teacher levels repository resolves in Koin to the instance Hilt builds`() {
        val application = bootApplication()
        val hilt = ReviewsBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.teacherLevelsRepository(), koin.get<TeacherLevelsRepository>())
        assertSame(koin.get<TeacherLevelsRepository>(), koin.get<TeacherLevelsRepository>())
    }

    @Test
    fun `a constructed definition may depend on the bridged reviews repositories`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(module { singleOf(::NeedsReviews) }))
    }

    @Test
    fun `the review editor's and report's module resolves over the bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(reviewsModule) + scheduleDataGraph)
    }

    class NeedsReviews(
        @Suppress("unused") val reviews: TeacherReviewsRepository,
        @Suppress("unused") val levels: TeacherLevelsRepository,
    )

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
