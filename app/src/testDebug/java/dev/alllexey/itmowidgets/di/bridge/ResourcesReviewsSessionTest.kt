package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.EntryPointAccessors
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.storage.DemoPreferences
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksRepositoryImpl
import dev.alllexey.itmowidgets.feature.reviews.data.StoredLevel
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsFileStore
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherLevelsRepositoryImpl
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
import dev.alllexey.itmowidgets.feature.sport.data.SportSessionBindingsEntryPoint
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * The links, reviews and levels data on the real graph: sign-out (Hilt's `Set<SessionDataCleaner>`, which holds
 * Koin's qualified cleaners through `SessionCleanersBridge`) reaches each of the three repositories once and forgets
 * the device-only links (file and memory), the reviews cache and the levels file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ResourcesReviewsSessionTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the cleaner set holds each of the three repositories once`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val cleaners = cleaners(application)

        assertEquals(1, cleaners.count { it === koin.get<SubjectLinksRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<TeacherReviewsRepositoryImpl>() })
        assertEquals(1, cleaners.count { it === koin.get<TeacherLevelsRepositoryImpl>() })
    }

    @Test
    fun `sign-out clears the device-only links from the file and from memory`() = runBlocking {
        val application = bootApplication()
        val links = GlobalContext.get().get<SubjectLinksRepositoryImpl>()
        val directory = File(application.filesDir, "subject_links")
        // Without the opt-in a private link stays on the device.
        val saved = links.save(
            SCOPE, LINK_ID, LinkCategory.MATERIALS, "https://example.org/notes", null, LinkVisibility.PRIVATE, null,
        )
        assertTrue(saved.toString(), saved is AppResult.Success)
        assertTrue(File(directory, "cache.json").exists())
        assertEquals(listOf(LINK_ID), content(links).mine.map { it.id })

        cleaners(application).forEach { it.clearSessionData() }

        assertFalse(directory.exists())
        assertEquals(emptyList<String>(), content(links).mine.map { it.id })
    }

    @Test
    fun `sign-out clears the reviews cache and the levels file`() = runBlocking {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val reviews = koin.get<TeacherReviewsRepositoryImpl>()
        val teacher = DemoPeople.MATH_TEACHER.isu
        // The demo session fills the reviews cache without Backend.
        DemoPreferences(koin.get<DataStore<Preferences>>()).setDemoActive(true)
        withTimeout(TIMEOUT_MS) {
            while (reviews.cachedReviews(teacher) == null) {
                reviews.reviews(teacher)
                delay(10)
            }
        }
        koin.get<TeacherLevelsFileStore>().write(mapOf(teacher to StoredLevel("POSITIVE", 1L)))
        val levels = File(application.filesDir, "teacher_levels")
        assertTrue(File(levels, "levels.json").exists())

        cleaners(application).forEach { it.clearSessionData() }

        assertNull(reviews.cachedReviews(teacher))
        assertFalse(levels.exists())
    }

    private suspend fun content(links: SubjectLinksRepositoryImpl) = withTimeout(TIMEOUT_MS) {
        (links.observe(SCOPE).first { it is SubjectLinksState.Content } as SubjectLinksState.Content).snapshot
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
        const val LINK_ID = "0b6c1d5e-7a35-4f0e-9a8e-1f2d3c4b5a60"
        val SCOPE = ResourceScope(42, "Тестовый предмет", "2026-1")
    }
}
