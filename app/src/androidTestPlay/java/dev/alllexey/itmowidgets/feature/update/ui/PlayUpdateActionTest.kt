package dev.alllexey.itmowidgets.feature.update.ui

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import dev.alllexey.itmowidgets.testing.TestUi
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Play's own fake: its update info needs real PendingIntents, so the test runs on a device. */
@RunWith(AndroidJUnit4::class)
class PlayUpdateActionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = FakeAppUpdateManager(context)
    private val direct = Executor { it.run() }
    private val opened = mutableListOf<String>()
    private var openable: (String) -> Boolean = { true }
    private val pages = ReleasePageOpener { _, url -> opened += url; openable(url) }
    private var failed = false
    private val action = PlayUpdateAction(manager, pages, direct)

    @Test
    fun anAvailableUpdateDownloadsInTheBackground() {
        manager.setUpdateAvailable(NEWER)

        start(unsupported = false)

        assertTrue(manager.isConfirmationDialogVisible)
        assertFalse(manager.isImmediateFlowVisible)
        assertEquals(AppUpdateType.FLEXIBLE, manager.typeForUpdateInProgress)
        assertTrue(opened.isEmpty())
        assertFalse(failed)
    }

    @Test
    fun anUnsupportedBuildUpdatesImmediately() {
        manager.setUpdateAvailable(NEWER)

        start(unsupported = true)

        assertTrue(manager.isImmediateFlowVisible)
        assertFalse(manager.isConfirmationDialogVisible)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun withoutAnUpdateInPlayTheCardOpens() {
        manager.setUpdateNotAvailable()

        start(unsupported = false)

        assertEquals(listOf("market://details?id=dev.alllexey.itmowidgets"), opened)
        assertFalse(manager.isConfirmationDialogVisible || manager.isImmediateFlowVisible)
        assertFalse(failed)
    }

    @Test
    fun anUpdateOfAnotherTypeOnlyOpensTheCard() {
        manager.setUpdateAvailable(NEWER, AppUpdateType.IMMEDIATE)

        start(unsupported = false)

        assertFalse(manager.isConfirmationDialogVisible || manager.isImmediateFlowVisible)
        assertEquals(listOf("market://details?id=dev.alllexey.itmowidgets"), opened)
    }

    @Test
    fun withoutPlayTheWebCardOpensAndThenTheScreenIsTold() {
        manager.setUpdateNotAvailable()
        openable = { !it.startsWith("market:") }

        start(unsupported = false)

        assertEquals(
            listOf(
                "market://details?id=dev.alllexey.itmowidgets",
                "https://play.google.com/store/apps/details?id=dev.alllexey.itmowidgets"
            ),
            opened
        )
        assertFalse(failed)

        opened.clear()
        openable = { false }
        start(unsupported = false)

        assertEquals(2, opened.size)
        assertTrue(failed)
    }

    @Test
    fun aFinishedDownloadIsReportedOnceAndNotAfterStop() {
        val watcher = PlayInstallStateWatcher(manager, direct)
        var downloaded = 0
        watcher.start { downloaded++ }
        manager.setUpdateAvailable(NEWER)
        start(unsupported = false)

        manager.userAcceptsUpdate()
        manager.downloadStarts()
        TestUi.settle(300)
        assertEquals(0, downloaded)
        manager.downloadCompletes()
        TestUi.eventually(attempts = 20, delayMillis = 50) { assertEquals(1, downloaded) }

        // Back in the app after the download: the state is read again, there is no event left.
        watcher.stop()
        watcher.start { downloaded++ }
        assertEquals(2, downloaded)

        watcher.stop()
        manager.installCompletes()
        TestUi.settle(300)
        assertEquals(2, downloaded)
    }

    private fun start(unsupported: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            action.start(Activity(), unsupported) { failed = true }
        }
    }

    private companion object {
        const val NEWER = 100
    }
}
