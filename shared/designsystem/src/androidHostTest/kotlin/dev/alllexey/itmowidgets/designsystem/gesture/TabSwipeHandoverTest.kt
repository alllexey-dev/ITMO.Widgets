package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [tabSwipeHandover] inside the tab pager: the content scrolls first and never flies over into the next tab. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class TabSwipeHandoverTest {

    @get:Rule
    val compose = createComposeRule()

    private val row = LazyListState()

    @Test
    fun `a fast fling that carries the row to its end leaves the tab where it was`() {
        val tabs = showRow(startPage = 1)

        compose.onNodeWithTag(ROW).performTouchInput { swipeLeft(durationMillis = FAST) }

        compose.runOnIdle {
            assertFalse(row.canScrollForward)
            assertSettledOn(1, tabs)
        }
    }

    @Test
    fun `without the handover the same fling flies over into the next tab`() {
        val tabs = showRow(startPage = 1, handover = false)

        compose.onNodeWithTag(ROW).performTouchInput { swipeLeft(durationMillis = FAST) }

        compose.runOnIdle { assertSettledOn(2, tabs) }
    }

    @Test
    fun `a new swipe at the end of the row moves the tab exactly one page`() {
        val tabs = showRow(startPage = 0)
        compose.onNodeWithTag(ROW).performTouchInput { swipeLeft(durationMillis = FAST) }
        compose.runOnIdle { assertSettledOn(0, tabs) }

        compose.onNodeWithTag(ROW).performTouchInput { swipeLeft(durationMillis = FAST) }

        compose.runOnIdle { assertSettledOn(1, tabs) }
    }

    @Test
    fun `a swipe back at the start of the row moves the tab one page back`() {
        val tabs = showRow(startPage = 1)

        compose.onNodeWithTag(ROW).performTouchInput { swipeRight() }

        compose.runOnIdle { assertSettledOn(0, tabs) }
    }

    @Test
    fun `a fast fling back to the start of the row leaves the tab where it was`() {
        val tabs = showRow(startPage = 1)
        compose.runOnIdle { runBlocking { row.scrollBy(10_000f) } }

        compose.onNodeWithTag(ROW).performTouchInput { swipeRight(durationMillis = FAST) }

        compose.runOnIdle {
            assertFalse(row.canScrollBackward)
            assertSettledOn(1, tabs)
        }
    }

    @Test
    fun `an inner pager keeps its swipes until its last page and then hands the next one over`() {
        val inner = PagerState { 2 }
        val tabs = show(startPage = 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                HorizontalPager(inner, Modifier.size(200.dp, 80.dp).testTag(INNER).tabSwipeHandover(inner)) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        // Each swipe runs 340 dp from the pager's end, well past its 200 dp of room.
        val swipe: () -> Unit = {
            compose.onNodeWithTag(INNER).performTouchInput {
                val start = Offset(right, centerY)
                swipe(start, start - Offset(340.dp.toPx(), 0f), FAST)
            }
        }

        swipe()
        compose.runOnIdle {
            assertEquals(1, inner.currentPage)
            assertSettledOn(0, tabs)
        }

        swipe()
        compose.runOnIdle {
            assertEquals(1, inner.currentPage)
            assertSettledOn(1, tabs)
        }
    }

    @Test
    fun `a horizontal drag over a vertical list switches the tab`() {
        val list = LazyListState()
        val tabs = show(startPage = 1) { VerticalList(list) }

        compose.onNodeWithTag(LIST).performTouchInput { dragAt(center, 300.dp.toPx(), 90.0, SLOW) }

        compose.runOnIdle {
            assertSettledOn(2, tabs)
            assertEquals(0, list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun `a drag 30 degrees off vertical scrolls the list only`() {
        val list = LazyListState()
        val tabs = show(startPage = 1) { VerticalList(list) }

        compose.onNodeWithTag(LIST).performTouchInput {
            dragAt(Offset(centerX, bottom - 10.dp.toPx()), 300.dp.toPx(), 30.0, SLOW)
        }

        compose.runOnIdle {
            assertTrue(list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset > 0)
            assertSettledOn(1, tabs)
        }
    }

    /** Three tabs; [row] on [startPage], 40 dp wider than the screen, so a full-width swipe overshoots it. */
    private fun showRow(startPage: Int, handover: Boolean = true): PagerState = show(startPage) {
        val modifier = Modifier.fillMaxWidth().height(80.dp).testTag(ROW)
        LazyRow(if (handover) modifier.tabSwipeHandover(row) else modifier, state = row) {
            items(4) { Box(Modifier.size(100.dp, 80.dp)) }
        }
    }

    private fun show(startPage: Int, content: @Composable () -> Unit): PagerState {
        val tabs = PagerState(currentPage = startPage) { 3 }
        compose.setContent {
            TabPager(tabs) { page -> Box(Modifier.fillMaxSize()) { if (page == startPage) content() } }
        }
        return tabs
    }

    @Composable
    private fun VerticalList(state: LazyListState) {
        LazyColumn(Modifier.fillMaxSize().testTag(LIST), state = state) {
            items(50) { Box(Modifier.fillMaxWidth().height(80.dp)) }
        }
    }

    private fun assertSettledOn(page: Int, tabs: PagerState) {
        assertEquals(page, tabs.currentPage)
        assertEquals(0f, tabs.currentPageOffsetFraction, 0f)
    }

    private companion object {
        const val ROW = "row"
        const val INNER = "inner"
        const val LIST = "list"
        const val FAST = 80L
        const val SLOW = 300L
    }
}
