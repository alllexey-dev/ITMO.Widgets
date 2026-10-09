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

/** The Compose shell with fake entries: layers, Back, tab state, sheets, arguments and gates (v2.2 parity). */
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
    fun anOverlayCoversTheBarWhileTheTabContentKeepsItsBounds() {
        show()
        val before = probe.tabBounds.getValue(AppTab.HOME)
        val settings = AppRoutes.Settings()

        act { open(settings) }

        compose.onNodeWithTag("screen:settings:ROOT").assertIsDisplayed()
        assertEquals(
            compose.onRoot().getBoundsInRoot(),
            compose.onNodeWithTag(ShellTags.overlay(settings.toString())).getBoundsInRoot(),
        )
        assertEquals(before, probe.tabBounds.getValue(AppTab.HOME))
        assertTrue(AppTab.HOME in probe.composedTabs)

        compose.onNodeWithTag(ShellTags.overlay(settings.toString())).performClick()
        assertEquals("a tap on the overlay never reaches the covered tab", 0, probe.tabClicks)

        pressBack()
        compose.onNodeWithTag(ShellTags.BAR).assertIsDisplayed()
        assertEquals(before, probe.tabBounds.getValue(AppTab.HOME))
    }

    @Test
    fun coveredTabContentAndBarAreHiddenFromTalkBack() {
        show()
        compose.onNodeWithTag(ShellTags.BAR).assertExists()
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).assertExists()

        act { open(AppRoutes.Friends) }

        compose.onNodeWithTag(ShellTags.BAR).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).assertDoesNotExist()
        compose.onNodeWithTag("sub:HOME").assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.TAB_LAYER, useUnmergedTree = true)
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
     * A profile opened from a sheet (2.2: the sheet dismisses first) slides in from the end and, on Back, out to the
     * end over the tab, full height on every frame: the layer never animates its size between no overlay and one.
     */
    @Test
    fun theOnlyOverlayOpenedFromASheetSlidesInAndOutHorizontallyAtFullSize() {
        show()
        act { open(AppRoutes.IcsExport) }
        assertEquals(1, shownDialogs().size)
        val profile = AppRoutes.UserProfile(ShellSamples.ISU)

        val push = framesOf(profile) { navigator.open(profile) }

        assertTrue(navigator.state.floating.isEmpty())
        assertEquals(0, shownDialogs().size)
        assertSlidesAtFullSize(push, towardsEnd = false)

        val pop = framesOf(profile) { navigator.back() }

        assertSlidesAtFullSize(pop, towardsEnd = true)
        compose.onNodeWithTag(ShellTags.overlay(profile.toString())).assertDoesNotExist()
        sheet(AppRoutes.IcsExport).assertDoesNotExist()
        assertEquals(ShellBackStack(), navigator.state)
        compose.onNodeWithTag(ShellTags.BAR).assertIsDisplayed()
    }

    /**
     * The predictive Back gesture on that profile moves it with the finger at full height, a cancel puts it back, and
     * a committed gesture slides it on to the end from where the finger let go.
     */
    @Test
    fun aPredictiveBackOnTheOverlayFromASheetFollowsTheFingerAndPopsFromThere() {
        show()
        act { open(AppRoutes.IcsExport) }
        val profile = AppRoutes.UserProfile(ShellSamples.ISU)
        act { open(profile) }
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val dispatcher = compose.activity.onBackPressedDispatcher
        val overlay = { compose.onNodeWithTag(ShellTags.overlay(profile.toString())).fetchSemanticsNode().boundsInRoot }

        dragBack(HALF)
        assertEquals(window.width * HALF, overlay().left, 1f)
        assertEquals(window.height, overlay().height, 0.5f)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(0f, overlay().left, 0.5f)
        assertEquals(listOf<AppRoute>(profile), navigator.state.overlays)

        dragBack(HALF)
        val pop = framesOf(profile) { dispatcher.onBackPressed() }

        val placed = pop.filter { it.width > 0f }
        assertTrue("the pop slides on from the finger: $pop", placed.isNotEmpty())
        placed.forEach { frame ->
            assertTrue("never behind the finger: $pop", frame.left >= window.width * HALF - 1f)
            assertEquals("full height on every frame: $pop", window.height, frame.height, 0.5f)
        }
        assertEquals(placed.map { it.left }.sorted(), placed.map { it.left })
        assertEquals(ShellBackStack(), navigator.state)
    }

    private fun dragBack(progress: Float) {
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }

    /** The bounds of [route]'s overlay on every frame after [command], with the main clock paused, until it settles. */
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

    /** [frames] of an overlay that is placed: one slide, full height and pinned to the window's end on each. */
    private fun assertSlidesAtFullSize(frames: List<Rect>, towardsEnd: Boolean) {
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val placed = frames.filter { it.width > 0f }
        assertTrue("the overlay slides over several frames: $frames", placed.count { it.left > 0f } >= MIN_SLIDE_FRAMES)
        placed.forEach { frame ->
            assertEquals("full height on every frame: $frames", window.height, frame.height, 0.5f)
            assertEquals("pinned to the end on every frame: $frames", window.right, frame.right, 0.5f)
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
        /** Well past the 220 ms slide at 16 ms a frame. */
        const val MAX_FRAMES = 40
        const val MIN_SLIDE_FRAMES = 4
        const val HALF = 0.5f
    }
}
