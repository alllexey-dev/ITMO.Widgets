package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [tabSwipeBlocked] on an arrow-only week strip inside the tab pager. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class TabSwipeBlockedTest {

    @get:Rule
    val compose = createComposeRule()

    private var clicks = 0

    @Test
    fun `a swipe on a blocked strip switches no tab either way`() {
        val tabs = showStrip(blocked = true)

        compose.onNodeWithTag(STRIP).performTouchInput { swipeLeft() }
        compose.runOnIdle { assertSettledOn(1, tabs) }
        compose.onNodeWithTag(STRIP).performTouchInput { swipeRight() }

        compose.runOnIdle { assertSettledOn(1, tabs) }
    }

    @Test
    fun `without the block the same swipe switches the tab`() {
        val tabs = showStrip(blocked = false)

        compose.onNodeWithTag(STRIP).performTouchInput { swipeLeft() }

        compose.runOnIdle { assertSettledOn(2, tabs) }
    }

    @Test
    fun `a tap on the blocked strip still clicks and a swipe from its button does not`() {
        val tabs = showStrip(blocked = true)

        compose.onNodeWithTag(DAY).performClick()
        compose.onNodeWithTag(DAY).performTouchInput { swipeLeft(startX = right, endX = right - 200.dp.toPx()) }

        compose.runOnIdle {
            assertEquals(1, clicks)
            assertSettledOn(1, tabs)
        }
    }

    private fun showStrip(blocked: Boolean): PagerState {
        val tabs = PagerState(currentPage = 1) { 3 }
        compose.setContent {
            TabPager(tabs) { page ->
                Column(Modifier.fillMaxSize()) {
                    if (page != 1) return@Column
                    val strip = Modifier.fillMaxWidth().height(56.dp).testTag(STRIP)
                    Row(if (blocked) strip.tabSwipeBlocked() else strip) {
                        Box(Modifier.weight(1f))
                        Box(Modifier.size(56.dp).testTag(DAY).clickable { clicks++ })
                    }
                }
            }
        }
        return tabs
    }

    private fun assertSettledOn(page: Int, tabs: PagerState) {
        assertEquals(page, tabs.currentPage)
        assertEquals(0f, tabs.currentPageOffsetFraction, 0f)
    }

    private companion object {
        const val STRIP = "strip"
        const val DAY = "day"
    }
}
