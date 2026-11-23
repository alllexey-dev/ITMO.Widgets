package dev.alllexey.itmowidgets.feature.update.data

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.feature.update.domain.AppUpdate
import dev.alllexey.itmowidgets.feature.update.domain.AppVersionName
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The offer of the Google Play build against Play's own fake: what Play reports decides, not Backend's version.
 *
 * The fake reports an update in progress without the intents that start it, so an immediate update left halfway
 * (offered again on a phone) has no case here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayUpdateAvailabilityTest {

    private val manager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext<Context>())
    private val diagnostics = RecordingDiagnostics()
    private val direct = Executor { it.run() }

    @Test
    fun `an update Play serves is offered`() = runTest {
        manager.setUpdateAvailable(NEWER)

        assertTrue(isAvailable(unsupported = false))
        assertTrue(isAvailable(unsupported = true))
        assertTrue(diagnostics.messages.isEmpty())
    }

    @Test
    fun `a release Play does not have yet is not offered, also below the minimum version`() = runTest {
        manager.setUpdateNotAvailable()

        assertFalse(isAvailable(unsupported = false))
        // A forced screen that Play cannot update from would be a dead end.
        assertFalse(isAvailable(unsupported = true))
        assertTrue(diagnostics.messages.isEmpty())
    }

    @Test
    fun `the update has to be of the type the build needs`() = runTest {
        manager.setUpdateAvailable(NEWER, AppUpdateType.FLEXIBLE)
        assertTrue(isAvailable(unsupported = false))
        assertFalse(isAvailable(unsupported = true))

        manager.setUpdateAvailable(NEWER, AppUpdateType.IMMEDIATE)
        assertFalse(isAvailable(unsupported = false))
        assertTrue(isAvailable(unsupported = true))
    }

    @Test
    fun `a flexible update already downloading is not offered again`() = runTest {
        manager.setUpdateAvailable(NEWER)
        startFlow(AppUpdateType.FLEXIBLE)
        manager.userAcceptsUpdate()
        manager.downloadStarts()

        assertFalse(isAvailable(unsupported = false))

        // Downloaded: the restart snackbar of InstallStateWatcher takes over.
        manager.downloadCompletes()
        assertFalse(isAvailable(unsupported = false))
    }

    @Test
    fun `an install Play cannot answer for is offered nothing and the failure is recorded`() = runTest {
        val failing = object : AppUpdateManager by manager {
            override fun getAppUpdateInfo(): Task<AppUpdateInfo> = Tasks.forException(IllegalStateException("no Play"))
        }

        assertFalse(PlayUpdateAvailability(failing, direct, diagnostics).isAvailable(update(unsupported = false)))
        assertEquals(listOf("WARNING:AppUpdate:Google Play did not report an update state"), diagnostics.messages)
    }

    private suspend fun isAvailable(unsupported: Boolean): Boolean =
        PlayUpdateAvailability(manager, direct, diagnostics).isAvailable(update(unsupported))

    private fun startFlow(type: Int) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val info = manager.appUpdateInfo.result
        assertTrue(manager.startUpdateFlowForResult(info, activity, AppUpdateOptions.defaultOptions(type), 1))
    }

    // Backend's versions do not matter here: Play knows only its own version codes.
    private fun update(unsupported: Boolean) = AppUpdate(
        installed = AppVersionName("2.3.0"),
        latest = AppVersionName("2.4.0"),
        note = "",
        unsupported = unsupported
    )

    private companion object {
        const val NEWER = 100
    }
}
