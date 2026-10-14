package dev.alllexey.itmowidgets.app.shell

import android.app.Dialog
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.OpenDecision
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** The Compose shell with fake entries: chrome, Back, predictive Back, tab state, sheets, arguments and gates. */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class AppShellTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val probe = ShellProbe()
    private val registry = fakeEntries(probe)
    private val navigator = Nav3AppNavigator()

    private fun show(surface: ShellSurface = ShellSurface.Tabs(demoBanner = false), onDemoSignIn: () -> Unit = {}) {
        compose.setContent { ItmoTheme { ShellContent(navigator, registry, surface, onDemoSignIn) } }
        compose.waitForIdle()
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    private fun pressBack() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun shownDialogs(): List<Dialog> = ShadowDialog.getShownDialogs().filter { it.isShowing }

    /** Back on the window of the top sheet or dialog, where the system delivers it. */
    private fun pressBackOnTopWindow() {
        compose.runOnIdle { (shownDialogs().last() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun sheet(route: AppRoute) = compose.onNodeWithTag(BottomSheetSceneStrategy.tag(route.toString()))

    @Test
    fun aPushedScreenCoversTheChromeAndBackBringsTheRootBackAtItsBounds() {
        show()
        val before = probe.tabBounds.getValue(AppTab.HOME)
        val settings = AppRoutes.Settings()

        act { open(settings) }

        compose.onNodeWithTag("screen:settings:ROOT").assertIsDisplayed()
        assertEquals(
            compose.onRoot().getBoundsInRoot(),
            compose.onNodeWithTag(ShellTags.overlay(settings.toString())).getBoundsInRoot(),
        )
        assertFalse("NavDisplay composes only the shown screen", AppTab.HOME in probe.composedTabs)
        compose.onNodeWithTag(ShellTags.overlay(settings.toString())).performClick()
        assertEquals("a tap on the screen never reaches the chrome", AppTab.HOME, navigator.tab)

        pressBack()
        compose.onNodeWithTag(ShellTags.BAR).assertIsDisplayed()
        assertEquals(before, probe.tabBounds.getValue(AppTab.HOME))
        val chrome = compose.onNodeWithTag(ShellTags.CHROME).fetchSemanticsNode().boundsInRoot
        assertEquals("the root ends above the chrome", chrome.top, before.bottom, 0.5f)
    }

    @Test
    fun theCoveredChromeIsHiddenFromTalkBack() {
        show()
        compose.onNodeWithTag(ShellTags.BAR).assertExists()
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).assertExists()

        act { open(AppRoutes.Friends) }

        compose.onNodeWithTag(ShellTags.BAR).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.CHROME, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        compose.onNodeWithTag("screen:friends").assertIsDisplayed()
    }

    @Test
    fun selectingAndReselectingATabClosesOverlaysSheetsAndDialogs() {
        show()
        act { open(AppRoutes.SubjectLinks(ShellSamples.links)) }
        assertEquals(1, shownDialogs().size)

        compose.onNodeWithTag(ShellTags.tab(AppTab.HOME)).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.isEmpty())
        assertEquals(0, shownDialogs().size)

        act {
            open(AppRoutes.Settings())
            open(AppRoutes.LinkUnavailable)
        }
        assertEquals(1, shownDialogs().size)

        act { select(AppTab.SCHEDULE) }
        assertEquals(AppTab.SCHEDULE, navigator.tab)
        assertTrue(navigator.state.overlays.isEmpty() && navigator.state.floating.isEmpty())
        assertEquals(0, shownDialogs().size)
        compose.onNodeWithTag(ShellTags.BAR).assertIsDisplayed()
        compose.onNodeWithTag("sub:SCHEDULE").assertIsDisplayed()
    }

    @Test
    fun eachTabKeepsScrollSubTabAndViewModelAcrossSwitches() {
        show()
        compose.onNodeWithTag("list:HOME").performScrollToIndex(30)
        repeat(2) { compose.onNodeWithTag("nextSub:HOME").performClick() }
        val model = compose.onNodeWithTag("vm:HOME").fetchText()

        compose.onNodeWithTag(ShellTags.tab(AppTab.SPORT)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("sub:SPORT").assertTextEquals("sub:0")
        assertFalse(AppTab.HOME in probe.composedTabs)

        compose.onNodeWithTag(ShellTags.tab(AppTab.HOME)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("item:HOME:30").assertIsDisplayed()
        compose.onNodeWithTag("sub:HOME").assertTextEquals("sub:2")
        compose.onNodeWithTag("vm:HOME").assertTextEquals(model)
    }

    @Test
    fun tabsOverlaysAndTabStateSurviveASavedStateRestore() {
        val restoration = StateRestorationTester(compose)
        lateinit var restored: Nav3AppNavigator
        restoration.setContent {
            restored = rememberNav3AppNavigator()
            ItmoTheme { ShellContent(restored, registry, ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {}) }
        }
        compose.onNodeWithTag("list:HOME").performScrollToIndex(30)
        compose.onNodeWithTag("nextSub:HOME").performClick()
        compose.onNodeWithTag(ShellTags.tab(AppTab.SCHEDULE)).performClick()
        compose.runOnIdle { restored.open(AppRoutes.Settings("PRIVACY")) }

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            assertEquals(AppTab.SCHEDULE, restored.tab)
            assertEquals(listOf<AppRoute>(AppRoutes.Settings("PRIVACY")), restored.state.overlays)
        }
        compose.onNodeWithTag("screen:settings:PRIVACY").assertIsDisplayed()
        compose.runOnIdle { restored.select(AppTab.HOME) }
        compose.onNodeWithTag("item:HOME:30").assertIsDisplayed()
        compose.onNodeWithTag("sub:HOME").assertTextEquals("sub:1")
    }

    @Test
    fun backClosesTheSheetThenEachOverlayThenGoesToTheStartTabThenLeaves() {
        show()
        act {
            select(AppTab.SPORT)
            open(AppRoutes.Settings())
            open(AppRoutes.Friends)
            open(AppRoutes.SubjectLinks(ShellSamples.links))
        }
        sheet(AppRoutes.SubjectLinks(ShellSamples.links)).assertExists()

        pressBackOnTopWindow()
        assertTrue(navigator.state.floating.isEmpty())
        assertEquals(0, shownDialogs().size)
        compose.onNodeWithTag("screen:friends").assertIsDisplayed()

        pressBack()
        compose.onNodeWithTag("screen:friends").assertDoesNotExist()
        compose.onNodeWithTag("screen:settings:ROOT").assertIsDisplayed()

        pressBack()
        compose.onNodeWithTag("screen:settings:ROOT").assertDoesNotExist()
        compose.onNodeWithTag("sub:SPORT").assertIsDisplayed()

        pressBack()
        assertEquals(AppTab.START, navigator.tab)
        assertFalse(compose.activity.isFinishing)

        pressBack()
        assertTrue("Back on the start tab leaves the app", compose.activity.isFinishing)
    }

    @Test
    fun aFormSheetIgnoresBackAndDragAndHandsBackToItsEntry() {
        show()
        val editor = AppRoutes.ReviewEditor(ShellSamples.teacher)
        act { open(editor) }

        pressBackOnTopWindow()
        assertEquals(1, probe.formBacks)
        sheet(editor).performTouchInput { swipeDown() }
        compose.waitForIdle()

        sheet(editor).assertExists()
        assertEquals(listOf<AppRoute>(editor), navigator.state.floating)
    }

    @Test
    fun aFreeSheetClosesOnDrag() {
        show()
        act { open(AppRoutes.IcsExport) }

        sheet(AppRoutes.IcsExport).performTouchInput { swipeDown() }
        compose.waitForIdle()

        assertTrue(navigator.state.floating.isEmpty())
        assertEquals(0, shownDialogs().size)
    }

    /**
     * A profile opened from a sheet (the sheet dismisses first) comes in on the shared X axis from the end and, on Back,
     * leaves towards it, full height on every frame.
     */
    @Test
    fun theOnlyScreenOpenedFromASheetMovesAlongTheXAxisAtFullHeight() {
        show()
        act { open(AppRoutes.IcsExport) }
        assertEquals(1, shownDialogs().size)
        val profile = AppRoutes.UserProfile(ShellSamples.ISU)

        val push = framesOf(profile) { navigator.open(profile) }

        assertTrue(navigator.state.floating.isEmpty())
        assertEquals(0, shownDialogs().size)
        assertMovesAtFullHeight(push, towardsEnd = false)

        val pop = framesOf(profile) { navigator.back() }

        assertMovesAtFullHeight(pop, towardsEnd = true)
        compose.onNodeWithTag(ShellTags.overlay(profile.toString())).assertDoesNotExist()
        sheet(AppRoutes.IcsExport).assertDoesNotExist()
        assertEquals(ShellBackStack(), navigator.state)
        compose.onNodeWithTag(ShellTags.BAR).assertIsDisplayed()
    }

    /**
     * The predictive Back on the second of two screens: the first one is composed under it while the gesture runs (not
     * the tab root) and stands still at the window's size, the top one shrinks; a cancel restores both, a commit
     * leaves the first one in place.
     */
    @Test
    fun aPredictiveBackShowsThePreviousScreenUnderTheTopOne() {
        show()
        act {
            open(AppRoutes.Settings())
            open(AppRoutes.Friends)
        }
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val top = { compose.onNodeWithTag(ShellTags.overlay(AppRoutes.Friends.toString())).fetchSemanticsNode() }
        compose.onNodeWithTag("screen:settings:ROOT").assertDoesNotExist()

        dragBack(HALF)
        compose.onNodeWithTag("screen:settings:ROOT").assertExists()
        assertFalse("the tab root stays out of the peek", AppTab.HOME in probe.composedTabs)
        val shrunk = top().boundsInRoot
        assertTrue("the top screen shrinks: $shrunk", shrunk.width < window.width && shrunk.width > window.width * 0.85f)
        assertTrue("towards the far side, away from the left edge: $shrunk", shrunk.center.x > window.center.x)
        val under = compose.onNodeWithTag(ShellTags.overlay(AppRoutes.Settings().toString())).fetchSemanticsNode()
        assertEquals("the screen under it stands still", window, under.boundsInRoot)

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(window.width, top().boundsInRoot.width, 0.5f)
        compose.onNodeWithTag("screen:settings:ROOT").assertDoesNotExist()
        assertEquals(listOf<AppRoute>(AppRoutes.Settings(), AppRoutes.Friends), navigator.state.overlays)

        dragBack(HALF)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(listOf<AppRoute>(AppRoutes.Settings()), navigator.state.overlays)
        compose.onNodeWithTag("screen:friends").assertDoesNotExist()
        compose.onNodeWithTag("screen:settings:ROOT").assertIsDisplayed()
    }

    /**
     * A commit at the end of the gesture (a long drag or a fling, progress about 1) still dissolves the top screen for
     * the whole [ShellBack.DISSOLVE_MILLIS] from where the finger left it, instead of the rest of the gesture's timeline.
     */
    @Test
    fun aBackCommittedAtTheEndOfTheGestureStillDissolves() {
        show()
        act { open(AppRoutes.Friends) }
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        dragBack(1f)
        val held = compose.onNodeWithTag(ShellTags.overlay(AppRoutes.Friends.toString())).fetchSemanticsNode().boundsInRoot

        val frames = framesOf(AppRoutes.Friends) { compose.activity.onBackPressedDispatcher.onBackPressed() }

        val shown = frames.size * FRAME_MILLIS
        assertTrue("the top screen dissolves for at least $MIN_DISSOLVE_MILLIS ms: $shown ms", shown >= MIN_DISSOLVE_MILLIS)
        assertTrue("and no longer than the dissolve: $shown ms", shown <= ShellBack.DISSOLVE_MILLIS + SETTLE_FRAMES * FRAME_MILLIS)
        assertEquals("no jump on release", held.width, frames.first().width, held.width * 0.02f)
        assertTrue("it keeps shrinking, never back to full size: $frames", frames.all { it.width <= held.width + 0.5f })
        assertTrue("the held screen was shrunk: $held", held.width < window.width)
        assertEquals(emptyList<AppRoute>(), navigator.state.overlays)
    }

    private fun dragBack(progress: Float) {
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }

    /** The bounds of [route]'s screen on every frame after [command], with the main clock paused, until it settles. */
    private fun framesOf(route: AppRoute, command: () -> Unit): List<Rect> {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread(command)
        val frames = (0 until MAX_FRAMES).mapNotNull {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(ShellTags.overlay(route.toString())).fetchSemanticsNodes()
                .singleOrNull()?.boundsInRoot
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return frames
    }

    /** [frames] of a screen that is placed: one move, full window size on each. */
    private fun assertMovesAtFullHeight(frames: List<Rect>, towardsEnd: Boolean) {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val placed = frames.filter { it.width > 0f }
        assertTrue("the screen moves over several frames: $frames", placed.count { it.left > 0f } >= MIN_MOVE_FRAMES)
        placed.forEach { frame ->
            assertEquals("full height on every frame: $frames", window.height, frame.height, 0.5f)
            assertEquals("full width on every frame: $frames", window.width, frame.width, 0.5f)
        }
        val lefts = placed.map { it.left }
        assertEquals("one direction: $lefts", if (towardsEnd) lefts.sorted() else lefts.sortedDescending(), lefts)
    }

    @Test
    fun aSecondOpenOfTheSameScreenOrSheetOpensNothing() {
        show()
        act {
            open(AppRoutes.Settings())
            open(AppRoutes.Settings())
        }
        assertEquals(1, navigator.state.overlays.size)

        act {
            open(AppRoutes.IcsExport)
            open(AppRoutes.IcsExport)
        }
        assertEquals(1, navigator.state.floating.size)
        assertEquals(1, shownDialogs().size)
    }

    @Test
    fun theSameProfileTwiceInTheOverlayStackGetsTwoEntries() {
        show()
        act {
            open(AppRoutes.UserProfile(ShellSamples.ISU))
            open(AppRoutes.Friends)
            open(AppRoutes.UserProfile(ShellSamples.ISU))
        }
        compose.onNodeWithTag("args").assertTextEquals("isu:${ShellSamples.ISU}")

        pressBack()
        pressBack()
        compose.onNodeWithTag("args").assertTextEquals("isu:${ShellSamples.ISU}")
    }

    @Test
    fun routeArgumentsReachTheEntrysSavedStateHandle() {
        show()
        act { open(AppRoutes.UserProfile(ShellSamples.ISU)) }

        compose.onNodeWithTag("args").assertTextEquals("isu:${ShellSamples.ISU}")
    }

    @Test
    fun anUnregisteredKeyShowsAnEmptyPlaceholder() {
        show()
        act { open(AppRoutes.Diagnostics) }

        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.Diagnostics)).assertIsDisplayed()
    }

    @Test
    fun theGateRefusesInDemoWithoutOpening() {
        var refusals = 0
        navigator.guard = { if (it == AppRoutes.MyItmoWeb) OpenDecision.REFUSE_IN_DEMO else OpenDecision.OPEN }
        navigator.onRefusedInDemo = { refusals++ }
        show()

        act { assertEquals(OpenDecision.REFUSE_IN_DEMO, open(AppRoutes.MyItmoWeb)) }

        assertEquals(1, refusals)
        assertTrue(navigator.state.overlays.isEmpty())
    }

    @Test
    fun progressSurfaceSaysTheSessionIsBeingChecked() {
        show(ShellSurface.Progress)

        compose.onNodeWithTag(ShellTags.PROGRESS).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(compose.activity.getString(R.string.auth_initializing)),
            ),
        )
        compose.onNodeWithTag(ShellTags.BAR).assertDoesNotExist()
    }

    @Test
    fun authSurfaceHasNoBarAndClosesTheOverlays() {
        act { open(AppRoutes.Settings()) }
        show(ShellSurface.Auth)

        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.Auth)).assertIsDisplayed()
        compose.onNodeWithTag(ShellTags.BAR).assertDoesNotExist()
        assertTrue(navigator.state.overlays.isEmpty())
    }

    @Test
    fun theDemoBannerSitsAboveTheBarAndSignsIn() {
        var signIns = 0
        show(ShellSurface.Tabs(demoBanner = true)) { signIns++ }

        val banner = compose.onNodeWithTag(ShellTags.DEMO_BANNER).getBoundsInRoot()
        val bar = compose.onNodeWithTag(ShellTags.BAR).getBoundsInRoot()
        assertEquals(bar.top, banner.bottom)
        compose.onNodeWithText(compose.activity.getString(R.string.demo_banner_sign_in)).performClick()

        assertEquals(1, signIns)
    }

    private fun SemanticsNodeInteraction.fetchText(): String =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private companion object {
        /** Well past the shared axis move at 16 ms a frame. */
        const val MAX_FRAMES = 60
        const val MIN_MOVE_FRAMES = 4
        const val HALF = 0.5f
        const val FRAME_MILLIS = 16
        const val MIN_DISSOLVE_MILLIS = 250
        const val SETTLE_FRAMES = 5
    }
}
