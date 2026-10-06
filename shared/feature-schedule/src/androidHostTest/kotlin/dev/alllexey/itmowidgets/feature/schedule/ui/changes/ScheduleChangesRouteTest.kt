package dev.alllexey.itmowidgets.feature.schedule.ui.changes

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.presentation.changes.ScheduleChangesViewModel
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

/** The route reports visibility from its lifecycle, as `ScheduleChangesFragment`'s `onStart`/`onStop` did. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleChangesRouteTest {

    @Test
    fun theHistoryIsMarkedReadOnStartAndNotWhileStopped() = runComposeUiTest {
        val repository = FakeScheduleChangesRepository(scheduleChange(id = "first"))
        val viewModel = ScheduleChangesViewModel(repository, Time, SavedStateHandle())
        val owner = TestOwner()
        owner.registry.currentState = Lifecycle.State.CREATED
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ItmoTheme { ScheduleChangesRoute(onBack = {}, viewModel = viewModel) }
            }
        }
        waitForIdle()
        assertEquals(0, repository.markAllReadCalls, "a created but not started screen marked the history read")

        runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        waitForIdle()
        assertEquals(1, repository.markAllReadCalls)
        assertTrue(repository.changes.value.all { it.read })
        assertTrue(description("first").startsWith("Новое. "), "the row read on start keeps its dot")

        runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        waitForIdle()
        runOnIdle { repository.changes.value += scheduleChange(id = "second") }
        waitForIdle()
        assertEquals(1, repository.markAllReadCalls, "a stopped screen marked a new change read")

        runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        waitForIdle()
        assertEquals(2, repository.markAllReadCalls)
    }

    private fun SemanticsNodeInteractionsProvider.description(id: String): String =
        onNodeWithTag(ScheduleChangesTestTags.row(id)).fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        val Time = FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 0))
    }
}
