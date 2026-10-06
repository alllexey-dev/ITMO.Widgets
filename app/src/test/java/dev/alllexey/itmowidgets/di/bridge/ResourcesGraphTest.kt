package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.auth.di.authDataModule
import dev.alllexey.itmowidgets.feature.resources.data.SubjectLinksRepositoryImpl
import dev.alllexey.itmowidgets.feature.resources.di.resourcesModule
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

/** The subject links data on the real graph: `resourcesModule` builds the one repository every reader shares. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ItmoWidgetsApplication::class)
@LazyApplication(LazyLoad.ON)
class ResourcesGraphTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the contract and the session cleaner are the module's one repository`() {
        bootApplication()
        val koin = GlobalContext.get()
        val repository = koin.get<SubjectLinksRepositoryImpl>()

        assertSame(repository, koin.get<SubjectLinksRepository>())
        assertSame(koin.get<SubjectLinksRepository>(), koin.get<SubjectLinksRepository>())
        assertEquals(1, koin.getAll<SessionDataCleaner>().count { it === repository })
    }

    /** `DemoMode` is `authDataModule`'s since KM-11h1. */
    @Test
    fun `the links module resolves against the bridges`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(authDataModule, resourcesModule))
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
