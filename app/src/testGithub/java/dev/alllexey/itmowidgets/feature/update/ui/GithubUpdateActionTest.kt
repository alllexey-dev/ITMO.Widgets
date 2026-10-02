package dev.alllexey.itmowidgets.feature.update.ui

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubUpdateActionTest {

    private val activity = Activity()

    @Test
    fun `the update button opens the latest GitHub release`() {
        val opened = mutableListOf<String>()
        var failed = false

        GithubUpdateAction { _, url -> opened += url; true }
            .start(activity, unsupported = false) { failed = true }

        assertEquals(listOf("https://github.com/alllexey-dev/ITMO.Widgets/releases/latest"), opened)
        assertFalse(failed)
    }

    @Test
    fun `an unsupported build goes to the same page`() {
        val opened = mutableListOf<String>()

        GithubUpdateAction { _, url -> opened += url; true }.start(activity, unsupported = true) {}

        assertEquals(listOf("https://github.com/alllexey-dev/ITMO.Widgets/releases/latest"), opened)
    }

    @Test
    fun `without a browser the screen is told`() {
        var failed = false

        GithubUpdateAction { _, _ -> false }.start(activity, unsupported = false) { failed = true }

        assertTrue(failed)
    }

    @Test
    fun `nothing is downloaded in the background`() {
        var reported = false
        val watcher = GithubInstallStateWatcher()

        watcher.start { reported = true }
        watcher.completeUpdate()
        watcher.stop()

        assertFalse(reported)
    }
}
