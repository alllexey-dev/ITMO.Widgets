package dev.alllexey.itmowidgets.di.bridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.alllexey.itmowidgets.app.ItmoWidgetsApplication
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
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
class ResourcesBridgeTest {

    @get:Rule
    val stopKoin = StopKoinRule()

    @Test
    fun `the subject links repository resolves in Koin to the instance Hilt builds`() {
        val application = bootApplication()
        val hilt = ResourcesBridgeEntryPoint.from(application)
        val koin = GlobalContext.get()

        assertSame(hilt.subjectLinksRepository(), koin.get<SubjectLinksRepository>())
        assertSame(koin.get<SubjectLinksRepository>(), koin.get<SubjectLinksRepository>())
    }

    @Test
    fun `a constructed definition may depend on the bridged subject links repository`() {
        KoinGraphCheck.assertValid(KoinModules.bridges, listOf(module { singleOf(::NeedsSubjectLinks) }))
    }

    class NeedsSubjectLinks(@Suppress("unused") val links: SubjectLinksRepository)

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
