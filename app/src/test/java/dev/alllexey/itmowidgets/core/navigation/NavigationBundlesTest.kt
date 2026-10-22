package dev.alllexey.itmowidgets.core.navigation

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class NavigationBundlesTest {
    @Test fun `subject bundle keeps assessment identity and accepts old arguments`() {
        val old = RecordbookSubjectArgs(42, 1, 3, "2026/2027")
        val assessment = old.copy(disciplineId = 77, controlType = "Курсовая работа")
        for (args in listOf(old, assessment)) {
            assertEquals(args, RecordbookSubjectArgs.from(args.toBundle()))
        }
    }
}
