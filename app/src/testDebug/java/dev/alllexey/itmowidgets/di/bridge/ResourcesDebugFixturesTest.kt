package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.debug.MemorySubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
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
class ResourcesDebugFixturesTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    private val fake: SubjectLinksRepository = MemorySubjectLinksRepository()

    @Test
    fun `a fixture replaces the repository until its host unloads it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        val release = koin.get<SubjectLinksRepository>()

        val fixture = ResourcesDebugFixtures.load(application) { fake }
        assertSame(fake, koin.get<SubjectLinksRepository>())

        ResourcesDebugFixtures.unload(application, fixture)
        assertSame(release, koin.get<SubjectLinksRepository>())
        assertSame(ResourcesBridgeEntryPoint.from(application).subjectLinksRepository(), koin.get<SubjectLinksRepository>())
    }

    @Test
    fun `the repository is read when a view model asks for it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()
        var current: SubjectLinksRepository = fake
        val fixture = ResourcesDebugFixtures.load(application) { current }

        val next = MemorySubjectLinksRepository()
        current = next
        assertSame(next, koin.get<SubjectLinksRepository>())

        ResourcesDebugFixtures.unload(application, fixture)
    }

    @Test
    fun `a replaced fixture is left to the host that replaced it`() {
        val application = bootApplication()
        val koin = GlobalContext.get()

        val first = ResourcesDebugFixtures.load(application) { fake }
        val second = ResourcesDebugFixtures.load(application) { fake }
        ResourcesDebugFixtures.unload(application, first)
        assertSame(fake, koin.get<SubjectLinksRepository>())

        ResourcesDebugFixtures.unload(application, second)
        assertNotSame(fake, koin.get<SubjectLinksRepository>())
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
