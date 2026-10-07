package dev.alllexey.itmowidgets.app.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeBlocked
import dev.alllexey.itmowidgets.designsystem.gesture.tabSwipeHandover
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The kit's `tabSwipeHandover` and `tabSwipeBlocked` inside the shell's tab pager: inner horizontal content takes a
 * swipe first, a new swipe at its edge switches the tab, and a blocked strip moves nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class, qualifiers = "w360dp-h640dp")
class TabSwipeHandoverTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val row = LazyListState()
    private val segments = PagerState { 2 }
    private val navigator = Nav3AppNavigator()

    /** Home holds a chip row, a two-page segment pager and a blocked strip; the other roots are empty. */
    private val registry = entryRegistry {
        entry<AppRoutes.TabRoot> { key, _ ->
            if (key.tab == AppTab.HOME) {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(Modifier.fillMaxWidth().height(ROW_HEIGHT).testTag(ROW).tabSwipeHandover(row), state = row) {
                        items(CHIPS) { Box(Modifier.size(CHIP_WIDTH, ROW_HEIGHT)) }
                    }
                    HorizontalPager(
                        segments,
                        Modifier.fillMaxWidth().height(SEGMENT_HEIGHT).testTag(SEGMENTS).tabSwipeHandover(segments),
                    ) { Box(Modifier.fillMaxSize()) }
                    Box(Modifier.fillMaxWidth().height(ROW_HEIGHT).testTag(STRIP).tabSwipeBlocked())
                }
            } else {
                Box(Modifier.fillMaxSize().testTag("root:${key.tab}"))
            }
        }
    }

    @Before
    fun show() {
        compose.setContent { ItmoTheme { ShellContent(navigator, registry, ShellSurface.Tabs(false), onDemoSignIn = {}) } }
        compose.waitForIdle()
    }

    private fun swipe(tag: String, durationMillis: Long = SWIPE_MILLIS) {
        compose.onNodeWithTag(tag).performTouchInput { swipeLeft(durationMillis = durationMillis) }
        compose.waitForIdle()
    }

    @Test
    fun theFirstSwipeOnTheRowScrollsTheRowOnly() {
        swipe(ROW)

        compose.runOnIdle {
            assertTrue(row.canScrollBackward)
            assertEquals(AppTab.HOME, navigator.tab)
        }
    }

    @Test
    fun aFlingThatCarriesTheRowToItsEndLeavesTheTab() {
        compose.runOnIdle { runBlocking { row.scrollToItem(CHIPS - CHIPS_LEFT) } }
        compose.waitForIdle()

        swipe(ROW, FLING_MILLIS)

        compose.runOnIdle {
            assertFalse(row.canScrollForward)
            assertEquals(AppTab.HOME, navigator.tab)
        }
    }

    @Test
    fun aNewSwipeAtTheEndOfTheRowSwitchesTheTab() {
        compose.runOnIdle { runBlocking { row.scrollToItem(CHIPS - 1) } }
        compose.waitForIdle()

        swipe(ROW)

        assertEquals(AppTab.SPORT, navigator.tab)
        compose.onNodeWithTag("root:SPORT").assertExists()
    }

    @Test
    fun theSegmentPagerKeepsItsSwipeAndHandsTheNextOneOverAtItsLastPage() {
        swipe(SEGMENTS)
        compose.runOnIdle {
            assertEquals(1, segments.currentPage)
            assertEquals(AppTab.HOME, navigator.tab)
        }

        swipe(SEGMENTS)

        assertEquals(AppTab.SPORT, navigator.tab)
    }

    @Test
    fun aBlockedStripMovesNothing() {
        swipe(STRIP)
        swipe(STRIP, FLING_MILLIS)

        compose.runOnIdle {
            assertEquals(AppTab.HOME, navigator.tab)
            assertEquals(0, segments.currentPage)
            assertFalse(row.canScrollBackward)
        }
    }

    private companion object {
        const val ROW = "row"
        const val SEGMENTS = "segments"
        const val STRIP = "strip"
        const val CHIPS = 12
        const val CHIPS_LEFT = 5
        const val SWIPE_MILLIS = 300L
        const val FLING_MILLIS = 40L
        val ROW_HEIGHT = 80.dp
        val CHIP_WIDTH = 100.dp
        val SEGMENT_HEIGHT = 200.dp
    }
}
