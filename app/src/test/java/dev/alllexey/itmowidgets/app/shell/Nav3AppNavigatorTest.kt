package dev.alllexey.itmowidgets.app.shell

import androidx.compose.runtime.saveable.SaverScope
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.OpenDecision
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Nav3AppNavigatorTest {

    private val navigator = Nav3AppNavigator()

    @Test
    fun theWholeBackStackRoundTripsThroughTheSaver() {
        navigator.select(AppTab.SPORT)
        navigator.open(AppRoutes.UserProfile(ShellSamples.ISU))
        navigator.open(AppRoutes.SubjectLinks(ShellSamples.links))
        navigator.consume(AppTab.SPORT)
        val saver = Nav3AppNavigator.saver()

        val saved = with(saver) { SaverScope { true }.save(navigator) }
        val restored = saver.restore(checkNotNull(saved))

        assertEquals(navigator.state, restored?.state)
    }

    @Test
    fun anIgnoredOpenChangesNothingAndCallsNobody() {
        var refusals = 0
        navigator.guard = { OpenDecision.IGNORE }
        navigator.onRefusedInDemo = { refusals++ }

        assertEquals(OpenDecision.IGNORE, navigator.open(AppRoutes.Settings()))

        assertTrue(navigator.state.overlays.isEmpty())
        assertEquals(0, refusals)
    }

    @Test
    fun aRepeatedScreenUnderASheetStillReplacesTheSheet() {
        navigator.open(AppRoutes.Settings())
        navigator.open(AppRoutes.IcsExport)

        navigator.open(AppRoutes.Settings())

        assertEquals(2, navigator.state.overlays.size)
        assertTrue(navigator.state.floating.isEmpty())
    }

    @Test
    fun backReportsWhenItWouldLeaveTheApp() {
        navigator.select(AppTab.ME)

        assertTrue(navigator.back())
        assertEquals(AppTab.START, navigator.tab)
        assertFalse(navigator.back())
    }

    @Test
    fun aTabRequestWaitsForItsRootUntilConsumed() {
        navigator.apply(EntryRoute(tab = AppTab.SCHEDULE, request = TabRequest.ScheduleToday))

        assertEquals(AppTab.SCHEDULE, navigator.tab)
        assertEquals(TabRequest.ScheduleToday, navigator.pendingRequest(AppTab.SCHEDULE))
        navigator.consume(AppTab.SCHEDULE)
        assertNull(navigator.pendingRequest(AppTab.SCHEDULE))
    }
}
