package dev.alllexey.itmowidgets.feature.sport.ui.my

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.designsystem.platform.ItmoPlatformStyle
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Compose port of the View `SportScoreCollapseTest`: the card over a list of rows, as LP-4b mounts it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SportScoreCollapseTest {

    private val listState = LazyListState()
    private lateinit var collapse: SportScoreCollapseState
    private lateinit var scope: CoroutineScope
    private var cardMeasures = 0
    private var rowCount by mutableStateOf(ROWS)
    private var rowHeight by mutableStateOf(ROW_HEIGHT)

    @Test
    fun scoreCardCollapsesWithScrollAndRestoresAtTheTop() = runComposeUiTest {
        setContent { Screen() }
        val expanded = snapshot()
        assertEquals(0f, expanded.fraction)
        assertEquals(1f, expanded.detailsAlpha)
        assertEquals(expanded.cardHeight.toFloat(), expanded.visibleCardHeight, 0.5f)
        // The list reserves the expanded card, otherwise the first row would start life under it.
        assertTrue(expanded.firstRowTop >= expanded.cardBottom, "row ${expanded.firstRowTop} vs card ${expanded.cardBottom}")

        val half = collapse.range / 2
        scrollBy(half)
        val mid = snapshot()
        assertTrue(mid.visibleCardHeight < expanded.visibleCardHeight, "card ${mid.visibleCardHeight}")
        assertTrue(mid.visibleDetailsHeight > 0f && mid.visibleDetailsHeight < expanded.visibleDetailsHeight)
        assertTrue(mid.detailsAlpha < 1f)
        // The rows move one to one with the scroll: the reserved padding does not move while the card collapses.
        assertEquals(expanded.firstRowTop - half, mid.firstRowTop, 1f)
        assertEquals(expanded.cardHeight, mid.cardHeight)

        scrollBy(collapse.range * 3)
        val collapsed = snapshot()
        assertEquals(1f, collapsed.fraction)
        assertEquals(0f, collapsed.visibleDetailsHeight)
        assertEquals(0f, collapsed.detailsAlpha)
        // The compact bar still says what it is: the title and the status keep their row.
        assertTrue(collapsed.headerHeight > 0)
        assertTrue(
            collapsed.visibleCardHeight >= collapsed.headerHeight &&
                collapsed.visibleCardHeight <= expanded.visibleCardHeight / 2,
            "bar ${collapsed.visibleCardHeight} vs header ${collapsed.headerHeight}",
        )
        assertEquals(collapsed.headerHeight + px(BAR_PADDING * 2), collapsed.visibleCardHeight, 1f)
        assertEquals(expanded.cardHeight, collapsed.cardHeight)

        runOnIdle { scope.launch { listState.scrollToItem(0) } }
        waitForIdle()
        val restored = snapshot()
        assertEquals(expanded, restored)
    }

    @Test
    fun touchScrollDrawsIntermediateCollapseFrames() = runComposeUiTest {
        setContent { Screen() }
        val expanded = snapshot()
        val measures = cardMeasures
        val frames = mutableListOf<Float>()
        val list = onNodeWithTag(LIST)
        val step = px(STEP)
        list.performTouchInput { down(Offset(centerX, height * 0.8f)) }
        repeat(STEPS) { index ->
            list.performTouchInput { moveTo(Offset(centerX, height * 0.8f - (index + 1) * step)) }
            waitForIdle()
            frames += runOnIdle { collapse.fraction }
        }
        list.performTouchInput { up() }
        waitForIdle()

        val intermediate = frames.filter { it > 0f && it < 1f }
        assertTrue(intermediate.size >= 2, "a touch scroll draws intermediate frames: $frames")
        assertEquals(measures, cardMeasures, "the card is never measured again during the gesture")
        // The release may have flung the first row away, so read the card alone.
        assertEquals(expanded.cardHeight, onNodeWithTag(SportScoreCardTestTags.CARD).fetchSemanticsNode().size.height)
    }

    @Test
    fun collapseUpdatesDrawingBoundsWithoutRequestingLayout() = runComposeUiTest {
        setContent { Screen() }
        val expanded = snapshot()
        val measures = cardMeasures
        val step = collapse.range / 12
        val heights = mutableListOf<Float>()
        repeat(12) {
            scrollBy(step)
            val frame = snapshot()
            assertEquals(measures, cardMeasures, "scrolling must not measure the card")
            assertEquals(expanded.cardHeight, frame.cardHeight)
            assertEquals(expanded.detailsHeight, frame.detailsHeight)
            heights += frame.visibleCardHeight
        }
        assertEquals(heights.sortedDescending(), heights, "the bar only shrinks while scrolling down")
        assertTrue(heights.last() < expanded.cardHeight)
    }

    @Test
    fun releasingMidCollapseSnapsToTheNearerEdge() = runComposeUiTest {
        setContent { Screen() }
        val range = collapse.range

        // Just past the halfway mark: settles closed.
        scrollBy((range * 0.6f).roundToInt())
        assertTrue(runOnIdle { collapse.fraction } in 0.5f..0.7f)
        settle()
        assertEquals(1f, runOnIdle { collapse.fraction })
        assertEquals(range, runOnIdle { listState.firstItemScrollOffset() })

        // Just short of it: settles back open.
        runOnIdle { scope.launch { listState.scrollToItem(0) } }
        waitForIdle()
        scrollBy((range * 0.3f).roundToInt())
        settle()
        assertEquals(0f, runOnIdle { collapse.fraction })
        assertEquals(0, runOnIdle { listState.firstItemScrollOffset() })
    }

    @Test
    fun aListTooShortToReachTheEdgeStaysPut() = runComposeUiTest {
        setContent { Screen() }
        val viewport = onNodeWithTag(LIST).fetchSemanticsNode().size.height
        val reserved = snapshot().firstRowTop.roundToInt()
        // One row that leaves 60 % of the collapse range to scroll: the snap would need the rest.
        rowCount = 1
        rowHeight = dp(viewport - reserved + (collapse.range * 0.6f).roundToInt())
        waitForIdle()

        scrollBy(collapse.range)
        val before = runOnIdle { listState.firstItemScrollOffset() }
        assertTrue(runOnIdle { collapse.fraction } in 0.5f..0.7f, "the list scrolls only part of the range")
        repeat(3) { settle() }
        assertEquals(before, runOnIdle { listState.firstItemScrollOffset() })

        // A drag released there does not chase the edge either.
        onNodeWithTag(LIST).performTouchInput { swipeUp(durationMillis = 400) }
        waitForIdle()
        assertEquals(before, runOnIdle { listState.firstItemScrollOffset() })
    }

    @Test
    fun aListThatCannotScrollKeepsTheCardExpanded() = runComposeUiTest {
        rowCount = 1
        setContent { Screen() }

        onNodeWithTag(LIST).performTouchInput { swipeUp(durationMillis = 300) }
        waitForIdle()
        assertEquals(0f, runOnIdle { collapse.fraction })
    }

    @Test
    fun aFlingCollapsesTheCardAllTheWayWithoutSnappingBack() = runComposeUiTest {
        setContent { Screen() }

        onNodeWithTag(LIST).performTouchInput { swipeUp(durationMillis = 80) }
        waitForIdle()
        assertEquals(1f, runOnIdle { collapse.fraction })
        assertTrue(runOnIdle { listState.firstItemScrollOffset() } > collapse.range, "the fling ran past the range")
    }

    @Test
    fun aShortReleasedDragEndsAtAnEdge() = runComposeUiTest {
        setContent { Screen() }
        val list = onNodeWithTag(LIST)
        val distance = collapse.range * 0.7f + px(TOUCH_SLOP)

        list.performTouchInput {
            val start = center
            down(start)
            moveTo(start - Offset(0f, distance / 2))
            moveTo(start - Offset(0f, distance))
            // Hold still, so the release carries no velocity and only the settle moves the list.
            advanceEventTime(500)
            moveTo(start - Offset(0f, distance))
            up()
        }
        waitForIdle()
        val fraction = runOnIdle { collapse.fraction }
        assertTrue(fraction == 0f || fraction == 1f, "released at $fraction")
    }

    @Composable
    private fun Screen() {
        scope = rememberCoroutineScope()
        ItmoTheme(platformStyle = ItmoPlatformStyle.Material) {
            collapse = rememberSportScoreCollapseState(listState)
            SportScoreCollapsingLayout(
                state = collapse,
                card = {
                    SportScoreCard(
                        SportScore(48, 20, emptyList()),
                        Modifier.layout { measurable, constraints ->
                            cardMeasures++
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        },
                        collapse = collapse,
                        animated = false,
                    )
                },
                modifier = Modifier.size(SCREEN_WIDTH, SCREEN_HEIGHT),
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize().testTag(LIST), state = listState, contentPadding = padding) {
                    items(rowCount) { index -> Box(Modifier.fillMaxWidth().height(rowHeight).testTag("row$index")) }
                }
            }
        }
    }

    private data class Snapshot(
        val fraction: Float,
        val cardHeight: Int,
        val cardBottom: Float,
        val visibleCardHeight: Float,
        val headerHeight: Int,
        val detailsHeight: Int,
        val visibleDetailsHeight: Float,
        val detailsAlpha: Float,
        val firstRowTop: Float,
    )

    private fun ComposeUiTest.snapshot(): Snapshot {
        waitForIdle()
        val card = onNodeWithTag(SportScoreCardTestTags.CARD).fetchSemanticsNode()
        val details = onNodeWithTag(SportScoreCardTestTags.DETAILS).fetchSemanticsNode()
        val header = onNodeWithTag(SportScoreCardTestTags.HEADER).fetchSemanticsNode()
        val row = onNodeWithTag("row0").fetchSemanticsNode()
        return runOnIdle {
            Snapshot(
                fraction = collapse.fraction,
                cardHeight = card.size.height,
                cardBottom = card.boundsInRoot.bottom,
                visibleCardHeight = collapse.visibleCardHeight(card.size.height.toFloat()),
                headerHeight = header.size.height,
                detailsHeight = details.size.height,
                visibleDetailsHeight = collapse.visibleDetailsHeight(),
                detailsAlpha = collapse.detailsAlpha(),
                firstRowTop = row.boundsInRoot.top,
            )
        }
    }

    private fun ComposeUiTest.scrollBy(pixels: Int) {
        runOnIdle { listState.dispatchRawDelta(pixels.toFloat()) }
        waitForIdle()
    }

    private fun ComposeUiTest.settle() {
        runOnIdle { scope.launch { collapse.settleToNearestEdge() } }
        waitForIdle()
    }

    private fun ComposeUiTest.px(dp: Dp): Float = with(density) { dp.toPx() }

    private fun ComposeUiTest.dp(px: Int): Dp = with(density) { px.toDp() }

    private companion object {
        const val LIST = "list"
        const val ROWS = 12
        const val STEPS = 6
        val ROW_HEIGHT = 120.dp
        val SCREEN_WIDTH = 411.dp
        val SCREEN_HEIGHT = 700.dp
        val STEP = 24.dp
        val TOUCH_SLOP = 16.dp

        /** `ItmoTheme.spacing.content`: the bar's padding above and below the header. */
        val BAR_PADDING = 12.dp
    }
}
