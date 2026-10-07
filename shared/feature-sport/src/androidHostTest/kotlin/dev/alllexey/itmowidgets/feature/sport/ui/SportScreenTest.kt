package dev.alllexey.itmowidgets.feature.sport.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dev.alllexey.itmowidgets.designsystem.gesture.TabSwipeDefaults
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.testkit.assertTouchTargets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sport tab's frame: the `Мой спорт`/`Запись` tabs over their pager, alone and inside a pager built like the
 * Android shell's bottom-tab pager, where the inner pager keeps its swipes and only a new swipe at its end moves the
 * outer one (design.md, Tab swipe, rule 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SportScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val sport = PagerState { SportPage.entries.size }

    @Test
    fun theTabsNameThePagesAndATapSlidesToItsPage() {
        compose.setContent { ItmoTheme { SportScreen(sport) { Box(Modifier.fillMaxSize()) } } }

        compose.onNodeWithTag(SportScreenTestTags.tab(SportPage.MY)).assertIsSelected()
        compose.onNodeWithText("Мой спорт").assertExists()
        compose.assertTouchTargets()

        compose.onNodeWithText("Запись").performClick()

        compose.runOnIdle { assertSettledOn(SportPage.SIGN.ordinal, sport) }
        compose.onNodeWithTag(SportScreenTestTags.tab(SportPage.SIGN)).assertIsSelected()
    }

    @Test
    fun bothPagesStayComposedWhileOneIsInFront() {
        compose.setContent { ItmoTheme { SportScreen(sport) { page -> PageMarker(page) } } }

        compose.onNodeWithTag(markerTag(SportPage.MY)).assertExists()
        compose.onNodeWithTag(markerTag(SportPage.SIGN)).assertExists()
    }

    @Test
    fun aFlingOnMySportTowardsSignStopsAtSign() {
        val tabs = showInTabPager()

        // Half a page more than `Запись` is away: without the handover the rest would move the bottom tabs.
        compose.onNodeWithTag(SportScreenTestTags.PAGER).performTouchInput {
            swipe(Offset(right - 1f, centerY), Offset(left - width / 2f, centerY), FAST)
        }

        compose.runOnIdle {
            assertSettledOn(SportPage.SIGN.ordinal, sport)
            assertSettledOn(SPORT_TAB, tabs)
        }
    }

    @Test
    fun aNewSwipeAtSignMovesTheOuterPagerOnePageForward() {
        val tabs = showInTabPager()
        compose.onNodeWithTag(SportScreenTestTags.PAGER).performTouchInput { swipeLeft(durationMillis = FAST) }
        compose.runOnIdle { assertSettledOn(SPORT_TAB, tabs) }

        compose.onNodeWithTag(SportScreenTestTags.PAGER).performTouchInput { swipeLeft(durationMillis = FAST) }

        compose.runOnIdle {
            assertSettledOn(SPORT_TAB + 1, tabs)
            assertSettledOn(SportPage.SIGN.ordinal, sport)
        }
    }

    @Test
    fun aSwipeBackAtMySportMovesTheOuterPagerOnePageBack() {
        val tabs = showInTabPager()

        compose.onNodeWithTag(SportScreenTestTags.PAGER).performTouchInput { swipeRight(durationMillis = FAST) }

        compose.runOnIdle {
            assertSettledOn(SPORT_TAB - 1, tabs)
            assertSettledOn(SportPage.MY.ordinal, sport)
        }
    }

    /** Three bottom tabs, the sport tab in the middle; the outer pager is built like the shell's `TabPager`. */
    private fun showInTabPager(): PagerState {
        val tabs = PagerState(currentPage = SPORT_TAB) { TAB_COUNT }
        compose.setContent {
            ItmoTheme {
                TabPager(tabs) { tab ->
                    if (tab == SPORT_TAB) {
                        SportScreen(sport) { Box(Modifier.fillMaxSize()) }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        return tabs
    }

    @Composable
    private fun TabPager(state: PagerState, page: @Composable (Int) -> Unit) {
        val system = LocalViewConfiguration.current
        val scaled = remember(system) { SlopScaled(system, TabSwipeDefaults.slopMultiplier) }
        CompositionLocalProvider(LocalViewConfiguration provides scaled) {
            HorizontalPager(
                state = state,
                modifier = Modifier.fillMaxSize(),
                flingBehavior = PagerDefaults.flingBehavior(
                    state,
                    snapPositionalThreshold = TabSwipeDefaults.commitFraction,
                ),
            ) { index ->
                CompositionLocalProvider(LocalViewConfiguration provides system) { page(index) }
            }
        }
    }

    @Composable
    private fun PageMarker(page: SportPage) {
        Box(Modifier.fillMaxSize().testTag(markerTag(page)))
    }

    private class SlopScaled(system: ViewConfiguration, multiplier: Float) : ViewConfiguration by system {
        override val touchSlop: Float = system.touchSlop * multiplier
    }

    private fun assertSettledOn(page: Int, state: PagerState) {
        assertEquals(page, state.currentPage)
        assertEquals(0f, state.currentPageOffsetFraction, 0f)
    }

    private companion object {
        const val TAB_COUNT = 3
        const val SPORT_TAB = 1
        const val FAST = 80L

        fun markerTag(page: SportPage): String = "page_${page.name}"
    }
}
