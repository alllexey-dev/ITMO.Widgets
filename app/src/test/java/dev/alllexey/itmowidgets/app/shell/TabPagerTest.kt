package dev.alllexey.itmowidgets.app.shell

import android.animation.ValueAnimator
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ActivityScenario
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import kotlin.math.cos
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Hosts the shell from `onCreate`, as `MainActivity` will, so a recreation restores it the way the system does. */
class TabPagerTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ItmoTheme { ShellContent(rememberNav3AppNavigator(), registry, ShellSurface.Tabs(false), onDemoSignIn = {}) }
        }
    }

    companion object {
        val probe = ShellProbe()
        private val registry = fakeEntries(probe)
    }
}

/** The tab pager of the Compose shell with fake tab roots (design.md "Tab swipe"). */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class, qualifiers = "w360dp-h640dp")
class TabPagerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val probe = ShellProbe()
    private val registry = fakeEntries(probe)
    private val navigator = Nav3AppNavigator()
    private val haptics = RecordingHaptics()

    @After
    fun restoreAnimationsAndTouchExploration() {
        setDurationScale(1f)
        accessibility().setTouchExplorationEnabled(false)
    }

    private fun show(surface: ShellSurface = ShellSurface.Tabs(demoBanner = false)) {
        compose.setContent { Shell(navigator, surface) }
        compose.waitForIdle()
    }

    @Composable
    private fun Shell(navigator: Nav3AppNavigator, surface: ShellSurface = ShellSurface.Tabs(demoBanner = false)) {
        ItmoTheme {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                ShellContent(navigator, registry, surface, onDemoSignIn = {})
            }
        }
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    private fun swipeTowardsNext() {
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    private fun swipeTowardsPrevious() {
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).performTouchInput { swipeRight() }
        compose.waitForIdle()
    }

    private fun assertBar(selected: AppTab) {
        AppTab.entries.forEach { tab ->
            val item = compose.onNodeWithTag(ShellTags.tab(tab))
            if (tab == selected) item.assertIsSelected() else item.assertIsNotSelected()
        }
    }

    private fun accessibility() =
        shadowOf(compose.activity.getSystemService(AccessibilityManager::class.java))

    @Test
    fun aSwipeFromHomeReachesSportAndBackWithTheBarAndOneHapticPerSwipe() {
        show()

        swipeTowardsNext()

        assertEquals(AppTab.SPORT, navigator.tab)
        assertBar(AppTab.SPORT)
        assertEquals(setOf(AppTab.SPORT), probe.composedTabs)
        assertEquals(listOf(HapticFeedbackType.GestureThresholdActivate), haptics.played)

        swipeTowardsPrevious()

        assertEquals(AppTab.HOME, navigator.tab)
        assertBar(AppTab.HOME)
        assertEquals(setOf(AppTab.HOME), probe.composedTabs)
        assertEquals(2, haptics.played.size)
    }

    @Test
    fun theBarFlipsAtTheCommitThresholdAndBackWhenTheDragReturns() {
        show()
        val content = compose.onNodeWithTag(ShellTags.TAB_CONTENT)

        content.performTouchInput {
            down(center)
            dragBy(-width * 0.2f)
        }
        compose.waitForIdle()
        assertBar(AppTab.HOME)
        assertTrue(haptics.played.isEmpty())

        content.performTouchInput { dragBy(-width * 0.3f) }
        compose.waitForIdle()
        assertBar(AppTab.SPORT)
        assertEquals(AppTab.HOME, navigator.tab)
        assertEquals(1, haptics.played.size)

        content.performTouchInput { dragBy(width * 0.35f) }
        compose.waitForIdle()
        assertBar(AppTab.HOME)

        content.performTouchInput {
            holdStill()
            up()
        }
        compose.waitForIdle()
        assertEquals(AppTab.HOME, navigator.tab)
        assertBar(AppTab.HOME)
        assertEquals("no haptic when the target flips back", 1, haptics.played.size)
    }

    @Test
    fun neitherEndWrapsAround() {
        show()
        act { select(AppTab.RECORDBOOK) }

        swipeTowardsPrevious()
        assertEquals(AppTab.RECORDBOOK, navigator.tab)
        assertEquals(setOf(AppTab.RECORDBOOK), probe.composedTabs)

        act { select(AppTab.ME) }
        swipeTowardsNext()
        assertEquals(AppTab.ME, navigator.tab)
        assertEquals(setOf(AppTab.ME), probe.composedTabs)
        assertTrue(haptics.played.isEmpty())
    }

    @Test
    fun theSwipeIsOffWithAnOverlayASheetOrADialogOpen() {
        show()
        listOf<AppRoute>(AppRoutes.Friends, AppRoutes.IcsExport, AppRoutes.LinkUnavailable).forEach { route ->
            act { open(route) }
            assertFalse(TabPages.swipeEnabled(ShellSurface.Tabs(false), navigator.state, touchExploration = false))

            // Into the main window, under the overlay or beside the sheet's and the dialog's own window.
            compose.onNodeWithTag(ShellTags.TAB_LAYER, useUnmergedTree = true).performTouchInput { swipeLeft() }
            compose.waitForIdle()

            assertEquals(route.toString(), AppTab.HOME, navigator.tab)
            assertEquals(route.toString(), setOf(AppTab.HOME), probe.composedTabs)
            act { select(AppTab.HOME) }
        }
        assertTrue(haptics.played.isEmpty())
    }

    @Test
    fun theSignInAndFirstRunSurfacesHaveNoPager() {
        listOf(ShellSurface.Auth, ShellSurface.Onboarding, ShellSurface.Progress).forEach { surface ->
            assertFalse(TabPages.swipeEnabled(surface, ShellBackStack(), touchExploration = false))
        }
        show(ShellSurface.Auth)

        compose.onNodeWithTag(ShellTags.TAB_PAGER).assertDoesNotExist()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(AppTab.HOME, navigator.tab)
        assertTrue(probe.compositions.isEmpty())
    }

    @Test
    fun underTouchExplorationThereIsNoPagerAndTheBarStillSwitches() {
        accessibility().setTouchExplorationEnabled(true)
        show()

        compose.onNodeWithTag(ShellTags.TAB_PAGER).assertDoesNotExist()
        compose.onAllNodes(PAGE_SEMANTICS).assertCountEquals(0)
        compose.onNodeWithTag(ShellTags.TAB_CONTENT).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(AppTab.HOME, navigator.tab)

        compose.onNodeWithTag(ShellTags.tab(AppTab.SPORT)).performClick()
        compose.waitForIdle()
        assertEquals(AppTab.SPORT, navigator.tab)
        compose.onNodeWithTag("sub:SPORT").assertIsDisplayed()
        compose.onAllNodesWithTag("sub:HOME").assertCountEquals(0)
    }

    @Test
    fun aTapFromHomeToMeNeverComposesSport() {
        show()
        probe.compositions.clear()

        compose.onNodeWithTag(ShellTags.tab(AppTab.ME)).performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppTab.ME), probe.compositions)
        assertBar(AppTab.ME)
        assertTrue(haptics.played.isEmpty())
    }

    @Test
    fun aRouteToSportWhileOnRecordbookLandsWithoutSlidingThroughTheTabsBetween() {
        show()
        act { select(AppTab.RECORDBOOK) }
        probe.compositions.clear()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle { navigator.apply(EntryRoute(AppTab.SPORT)) }
        repeat(FRAMES_TO_LAND) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        assertEquals("no slide through the tabs between", listOf(AppTab.SPORT), probe.compositions)
        assertEquals(0f, probe.tabBounds.getValue(AppTab.SPORT).left)
        compose.mainClock.autoAdvance = true
        assertBar(AppTab.SPORT)
    }

    @Test
    fun aRouteDuringASwipeCancelsTheSwipe() {
        show()
        val content = compose.onNodeWithTag(ShellTags.TAB_CONTENT)
        content.performTouchInput {
            down(center)
            dragBy(-width * 0.5f)
        }
        compose.waitForIdle()

        act { apply(EntryRoute(AppTab.SCHEDULE)) }
        content.performTouchInput {
            dragBy(-width * 0.2f)
            up()
        }
        compose.waitForIdle()

        assertEquals(AppTab.SCHEDULE, navigator.tab)
        assertEquals(setOf(AppTab.SCHEDULE), probe.composedTabs)
        assertEquals(0f, probe.tabBounds.getValue(AppTab.SCHEDULE).left)
        assertBar(AppTab.SCHEDULE)
    }

    @Test
    fun aSavedStateRestoreKeepsTheSportPageAndEachRootsScroll() {
        val restoration = StateRestorationTester(compose)
        lateinit var restored: Nav3AppNavigator
        restoration.setContent {
            restored = rememberNav3AppNavigator()
            Shell(restored)
        }
        compose.onNodeWithTag("list:HOME").performScrollToIndex(30)
        swipeTowardsNext()
        compose.onNodeWithTag("list:SPORT").performScrollToIndex(20)
        swipeTowardsPrevious()
        compose.onNodeWithTag("item:HOME:30").assertIsDisplayed()
        swipeTowardsNext()
        probe.compositions.clear()

        restoration.emulateSavedInstanceStateRestore()

        assertEquals("the restored pages start on sport", listOf(AppTab.SPORT), probe.compositions)
        compose.runOnIdle { assertEquals(AppTab.SPORT, restored.tab) }
        assertBar(AppTab.SPORT)
        compose.onNodeWithTag("item:SPORT:20").assertIsDisplayed()
        swipeTowardsPrevious()
        compose.onNodeWithTag("item:HOME:30").assertIsDisplayed()
        compose.runOnIdle { assertEquals(AppTab.HOME, restored.tab) }
    }

    @Test
    fun recreationKeepsTheSportPageAndEachRootsScroll() {
        val shell = TabPagerTestActivity.probe.apply {
            compositions.clear()
            composedTabs.clear()
        }
        shadowOf(compose.activity.packageManager).addOrUpdateActivity(
            ActivityInfo().apply {
                name = TabPagerTestActivity::class.java.name
                packageName = compose.activity.packageName
            },
        )
        ActivityScenario.launch(TabPagerTestActivity::class.java).use { scenario ->
            compose.waitForIdle()
            compose.onNodeWithTag("list:HOME").performScrollToIndex(30)
            swipeTowardsNext()
            compose.onNodeWithTag("list:SPORT").performScrollToIndex(20)
            compose.waitForIdle()
            shell.compositions.clear()

            scenario.recreate()
            compose.waitForIdle()

            assertEquals("the recreated pages start on sport", listOf(AppTab.SPORT), shell.compositions)
            assertBar(AppTab.SPORT)
            // The recreated window is not shown under Robolectric, so the roots' scroll is read from what is composed.
            compose.onNodeWithTag("item:SPORT:20").assertExists()
            compose.onNodeWithTag("item:SPORT:0").assertDoesNotExist()
            swipeTowardsPrevious()
            compose.onNodeWithTag("item:HOME:30").assertExists()
            compose.onNodeWithTag("item:HOME:0").assertDoesNotExist()
        }
    }

    @Test
    fun aDragThirtyDegreesOffVerticalScrollsTheRootListAndNotThePager() {
        show()

        compose.onNodeWithTag("list:HOME").performTouchInput {
            val distance = height * 0.6f
            val radians = Math.toRadians(DIAGONAL_DEGREES)
            val dx = distance * sin(radians).toFloat()
            val start = Offset(centerX + dx / 2, bottom - 1f)
            swipe(start, start - Offset(dx, distance * cos(radians).toFloat()), DIAGONAL_MILLIS)
        }
        compose.waitForIdle()

        assertEquals(AppTab.HOME, navigator.tab)
        assertEquals(setOf(AppTab.HOME), probe.composedTabs)
        assertEquals(0f, probe.tabBounds.getValue(AppTab.HOME).left)
        compose.onNodeWithTag("item:HOME:0").assertDoesNotExist()
    }

    @Test
    fun withAnimationsOffTheReleaseSettlesInOneFrame() {
        setDurationScale(0f)
        show()
        val content = compose.onNodeWithTag(ShellTags.TAB_CONTENT)
        content.performTouchInput {
            down(center)
            dragBy(-width * 0.6f)
            holdStill()
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        content.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()

        assertEquals(0f, probe.tabBounds.getValue(AppTab.SPORT).left)
        assertFalse(AppTab.HOME in probe.composedTabs)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(AppTab.SPORT, navigator.tab)
    }

    @Test
    fun theTabsStayBarButtonsAndOnlyTheSettledRootIsInTheSemanticsTree() {
        show()
        swipeTowardsNext()

        AppTab.entries.forEach { tab ->
            compose.onNodeWithTag(ShellTags.tab(tab))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        assertBar(AppTab.SPORT)
        compose.onNodeWithTag("sub:SPORT").assertIsDisplayed()
        AppTab.entries.filter { it != AppTab.SPORT }.forEach { tab ->
            compose.onAllNodesWithTag("sub:$tab", useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun anOverlayHidesThePagerAndTheBarFromTalkBack() {
        show()
        act { open(AppRoutes.Settings()) }

        compose.onNodeWithTag(ShellTags.TAB_PAGER).assertDoesNotExist()
        compose.onNodeWithTag(ShellTags.BAR).assertDoesNotExist()
        compose.onAllNodes(PAGE_SEMANTICS).assertCountEquals(0)
        compose.onNodeWithTag(ShellTags.TAB_LAYER, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    /** `ValueAnimator.setDurationScale` is hidden; android-all has it (the animator scale of reduced motion). */
    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    /** Moves the pointer by [dx] in small steps, so the pager sees a drag rather than a jump. */
    private fun TouchInjectionScope.dragBy(dx: Float) {
        val step = dx / DRAG_STEPS
        repeat(DRAG_STEPS) { moveBy(Offset(step, 0f)) }
    }

    /** Keeps the pointer still long enough that its release carries no fling. */
    private fun TouchInjectionScope.holdStill() {
        repeat(STILL_MOVES) { moveBy(Offset.Zero, delayMillis = STILL_MILLIS) }
    }

    private class RecordingHaptics : HapticFeedback {
        val played = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            played += hapticFeedbackType
        }
    }

    private companion object {
        const val DRAG_STEPS = 10
        const val STILL_MOVES = 8
        const val STILL_MILLIS = 50L
        const val FRAMES_TO_LAND = 3
        const val DIAGONAL_DEGREES = 30.0
        const val DIAGONAL_MILLIS = 400L

        /** What a pager node carries: a horizontal range and the page actions. */
        val PAGE_SEMANTICS = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) or
            SemanticsMatcher.keyIsDefined(SemanticsActions.PageLeft) or
            SemanticsMatcher.keyIsDefined(SemanticsActions.PageRight)
    }
}
