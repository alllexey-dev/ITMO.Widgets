package dev.alllexey.itmowidgets.feature.sport.cards

import android.content.Intent
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.matcher.ViewMatchers.withId
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.feature.sport.ui.SportScoreCollapsePreviewActivity
import dev.alllexey.itmowidgets.testing.Appearances
import dev.alllexey.itmowidgets.testing.Appearances.toSportScoreCollapse
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SportScoreCollapseTest {

    @Test fun scoreCardCollapsesWithScrollAndRestoresAtTheTop() {
        Appearances.default.forEachIndexed { index, spec ->
            preview(spec.toSportScoreCollapse()) { scenario ->
                lateinit var expanded: Snapshot
                scenario.onActivity { it.showBookings((1L..12L).map { id -> SportCardFixtures.booking(id) }) }
                settle()
                screenshot("collapse-$index-0-expanded")

                scenario.onActivity { activity ->
                    expanded = snapshot(activity)
                    assertEquals(1f, expanded.detailsAlpha, 0.01f)
                    // The list must reserve the expanded card, otherwise the first booking would
                    // start life hidden underneath it.
                    assertTrue(
                        "List padding ${expanded.listPaddingTop} vs card ${expanded.cardBottom}",
                        expanded.listPaddingTop >= expanded.cardBottom
                    )
                }

                val half = scrollBy(scenario) { it.collapseRange() / 2 }
                settle()
                screenshot("collapse-$index-1-half")
                scenario.onActivity { activity ->
                    val mid = snapshot(activity)
                    assertTrue("Card ${mid.cardHeight} vs ${expanded.cardHeight}", mid.cardHeight < expanded.cardHeight)
                    assertTrue("Details ${mid.detailsHeight}", mid.detailsHeight in 1 until expanded.detailsHeight)
                    assertTrue("Alpha ${mid.detailsAlpha}", mid.detailsAlpha < 1f)
                    // The reserved padding must not move while collapsing, or the list would
                    // scroll twice per drag.
                    assertEquals(expanded.listPaddingTop, mid.listPaddingTop)
                    assertTrue("Half scrolled $half", half > 0)
                }

                scrollBy(scenario) { it.collapseRange() * 3 }
                settle()
                screenshot("collapse-$index-2-collapsed")
                scenario.onActivity { activity ->
                    val collapsed = snapshot(activity)
                    assertEquals(0, collapsed.detailsHeight)
                    assertEquals(0f, collapsed.detailsAlpha, 0.01f)
                    assertTrue("Header ${collapsed.headerHeight}", collapsed.headerHeight > 0)
                    // The compact bar still shows what the card is: title and status stay put.
                    assertTrue(
                        "Collapsed ${collapsed.cardHeight} vs header ${collapsed.headerHeight}",
                        collapsed.cardHeight in collapsed.headerHeight..(expanded.cardHeight / 2)
                    )
                    assertNotEquals(expanded.cardColor, collapsed.cardColor)
                }

                scenario.onActivity { it.binding.mainRecyclerView.scrollToPosition(0) }
                settle()
                scenario.onActivity { activity ->
                    val restored = snapshot(activity)
                    assertEquals(expanded.cardHeight, restored.cardHeight)
                    assertEquals(1f, restored.detailsAlpha, 0.01f)
                    assertEquals(expanded.cardColor, restored.cardColor)
                }
                screenshot("collapse-$index-3-restored")
            }
        }
    }

    @Test fun listReservesTheCardWhenContentArrivesAfterLoading() {
        preview(SportScoreCollapsePreviewActivity.Appearance(), startLoading = true) { scenario ->
            settle()
            val entryFrames = mutableListOf<Snapshot>()
            lateinit var drawListener: ViewTreeObserver.OnDrawListener
            scenario.onActivity { activity ->
                drawListener = ViewTreeObserver.OnDrawListener { entryFrames += snapshot(activity) }
                activity.binding.root.viewTreeObserver.addOnDrawListener(drawListener)
                activity.showContent((1L..12L).map { id -> SportCardFixtures.booking(id) })
            }
            settle()
            screenshot("collapse-entry")
            scenario.onActivity { activity ->
                activity.binding.root.viewTreeObserver.removeOnDrawListener(drawListener)
                assertTrue("Content must actually draw", entryFrames.isNotEmpty())
                entryFrames.forEach { frame ->
                    assertTrue("A drawn frame overlaps the first booking: $frame", frame.firstRowTop >= frame.cardBottom)
                    assertEquals("A layout frame is not a scroll", 0, frame.scrollOffset)
                    assertEquals("Entry frame must be expanded", 1f, frame.detailsAlpha, 0.01f)
                }
                val entry = snapshot(activity)
                assertTrue(
                    "First row starts under the card: padding ${entry.listPaddingTop}, card ${entry.cardBottom}",
                    entry.listPaddingTop >= entry.cardBottom
                )
                assertEquals("Card must not start collapsed", 1f, entry.detailsAlpha, 0.01f)
                val firstRow = checkNotNull(
                    (activity.binding.mainRecyclerView.layoutManager as LinearLayoutManager).findViewByPosition(0)
                )
                assertTrue(
                    "First row ${firstRow.top} overlaps card ${entry.cardBottom}",
                    firstRow.top >= entry.cardBottom
                )
                assertEquals("Layout is not a scroll", 0, activity.binding.mainRecyclerView.computeVerticalScrollOffset())
                // A short drag may not collapse the whole way; the range has to be the real card,
                // not a stale measurement taken while the card was still out of the layout.
                assertTrue("Collapse range ${activity.collapseRange()}", activity.collapseRange() > entry.headerHeight)
            }
            lateinit var beforeScroll: Snapshot
            scenario.onActivity {
                beforeScroll = snapshot(it)
                it.binding.mainRecyclerView.scrollBy(0, 8)
            }
            settle()
            scenario.onActivity {
                val afterScroll = snapshot(it)
                assertTrue("A small scroll must not collapse the entire card", afterScroll.detailsHeight > 0)
                assertTrue(
                    "Card follows the scroll without jumping",
                    beforeScroll.cardHeight - afterScroll.cardHeight in 6..10
                )
            }
            screenshot("collapse-first-small-scroll")
        }
    }

    @Test fun touchScrollDrawsIntermediateCollapseFrames() {
        preview(SportScoreCollapsePreviewActivity.Appearance(), startLoading = true) { scenario ->
            scenario.onActivity { it.showContent((1L..12L).map { id -> SportCardFixtures.booking(id) }) }
            settle()
            val frames = mutableListOf<Snapshot>()
            lateinit var expanded: Snapshot
            lateinit var listener: ViewTreeObserver.OnDrawListener
            scenario.onActivity { activity ->
                expanded = snapshot(activity)
                listener = ViewTreeObserver.OnDrawListener { frames += snapshot(activity) }
                activity.binding.root.viewTreeObserver.addOnDrawListener(listener)
            }
            onView(withId(R.id.main_recycler_view)).perform(
                GeneralSwipeAction(
                    Swipe.SLOW,
                    { view ->
                        val location = IntArray(2).also(view::getLocationOnScreen)
                        floatArrayOf(location[0] + view.width / 2f, location[1] + view.height * 0.8f)
                    },
                    { view ->
                        val location = IntArray(2).also(view::getLocationOnScreen)
                        floatArrayOf(location[0] + view.width / 2f, location[1] + view.height * 0.5f)
                    },
                    Press.FINGER
                )
            )
            settle()
            scenario.onActivity { activity ->
                activity.binding.root.viewTreeObserver.removeOnDrawListener(listener)
                val intermediate = frames.filter { it.detailsHeight in 1 until expanded.detailsHeight }
                assertTrue(
                    "Touch scroll must draw intermediate frames: ${frames.map { it.detailsHeight to it.scrollOffset }}",
                    intermediate.size >= 2
                )
                intermediate.forEach {
                    assertEquals("Padding must stay fixed during the gesture", expanded.listPaddingTop, it.listPaddingTop)
                }
            }
            screenshot("collapse-after-touch-scroll")
        }
    }

    @Test fun collapseUpdatesDrawingBoundsWithoutRequestingLayout() {
        preview(SportScoreCollapsePreviewActivity.Appearance()) { scenario ->
            scenario.onActivity { it.showContent((1L..12L).map { id -> SportCardFixtures.booking(id) }) }
            settle()
            scenario.onActivity { activity ->
                with(activity.binding) {
                    val measuredCardHeight = pointsCard.measuredHeight
                    val measuredDetailsHeight = scoreDetails.measuredHeight
                    val step = activity.collapseRange() / 12
                    repeat(12) {
                        mainRecyclerView.scrollBy(0, step)
                        assertFalse("Scrolling must not request screen layout", root.isLayoutRequested)
                        assertFalse("Scrolling must not request card layout", pointsCard.isLayoutRequested)
                        assertFalse("Scrolling must not request detail layout", scoreDetails.isLayoutRequested)
                        assertEquals(measuredCardHeight, pointsCard.measuredHeight)
                        assertEquals(measuredDetailsHeight, scoreDetails.measuredHeight)
                    }
                    assertTrue(pointsCard.height < measuredCardHeight)
                }
            }
        }
    }

    @Test fun emptyContentExpandsCardAndBookingsReturnBelowIt() {
        preview(SportScoreCollapsePreviewActivity.Appearance(dark = true)) { scenario ->
            val bookings = (1L..12L).map { SportCardFixtures.booking(it) }
            scenario.onActivity { it.showContent(bookings) }
            settle()
            scrollBy(scenario) { it.collapseRange() * 2 }
            settle()
            scenario.onActivity { it.showContent(emptyList()) }
            settle()
            scenario.onActivity {
                assertEquals("Empty state has the expanded score", 1f, snapshot(it).detailsAlpha, 0.01f)
                assertTrue(it.binding.emptyStateLayout.top >= it.binding.pointsCard.bottom)
            }
            screenshot("collapse-empty")
            scenario.onActivity { it.showContent(bookings) }
            settle()
            scenario.onActivity {
                val restored = snapshot(it)
                assertTrue("New bookings must not overlap the card", restored.firstRowTop >= restored.cardBottom)
                assertEquals(0, restored.scrollOffset)
            }
        }
    }

    @Test fun releasingMidCollapseSnapsToTheNearerEdge() {
        preview(SportScoreCollapsePreviewActivity.Appearance()) { scenario ->
            scenario.onActivity { it.showBookings((1L..12L).map { id -> SportCardFixtures.booking(id) }) }
            settle()

            // Just past the halfway mark: settles closed.
            scenario.onActivity { it.binding.mainRecyclerView.scrollBy(0, (it.collapseRange() * 0.6f).toInt()) }
            settle()
            scenario.onActivity { assertTrue("Mid", snapshot(it).detailsHeight > 0) }
            scenario.onActivity { it.collapse.settleToNearestEdge() }
            settle()
            scenario.onActivity { assertEquals("Snapped closed", 0, snapshot(it).detailsHeight) }

            // Just short of it: settles back open.
            scenario.onActivity { activity ->
                activity.binding.mainRecyclerView.scrollToPosition(0)
                activity.binding.mainRecyclerView.scrollBy(0, (activity.collapseRange() * 0.3f).toInt())
            }
            settle()
            scenario.onActivity { it.collapse.settleToNearestEdge() }
            settle()
            scenario.onActivity { activity ->
                val restored = snapshot(activity)
                assertEquals(1f, restored.detailsAlpha, 0.01f)
                assertEquals(0, activity.binding.mainRecyclerView.computeVerticalScrollOffset())
            }

            // A list that cannot reach either edge must not chase the snap forever.
            scenario.onActivity { it.showBookings(listOf(SportCardFixtures.booking())) }
            settle()
            scenario.onActivity { activity ->
                val before = activity.binding.mainRecyclerView.computeVerticalScrollOffset()
                repeat(3) { activity.collapse.settleToNearestEdge() }
                assertEquals(before, activity.binding.mainRecyclerView.computeVerticalScrollOffset())
            }
        }
    }

    private data class Snapshot(
        val cardHeight: Int,
        val headerHeight: Int,
        val detailsHeight: Int,
        val detailsAlpha: Float,
        val listPaddingTop: Int,
        val cardBottom: Int,
        val cardColor: Int,
        val firstRowTop: Int,
        val scrollOffset: Int
    )

    private fun snapshot(activity: SportScoreCollapsePreviewActivity) = with(activity.binding) {
        Snapshot(
            cardHeight = pointsCard.height,
            headerHeight = scoreHeader.height,
            detailsHeight = scoreDetails.height,
            detailsAlpha = scoreDetailsContent.alpha,
            listPaddingTop = mainRecyclerView.paddingTop,
            cardBottom = pointsCard.bottom,
            cardColor = pointsCard.cardBackgroundColor.defaultColor,
            firstRowTop = (mainRecyclerView.layoutManager as LinearLayoutManager).findViewByPosition(0)?.top ?: -1,
            scrollOffset = mainRecyclerView.computeVerticalScrollOffset()
        )
    }

    /** Collapse range in pixels, read back from the layout the controller actually drives. */
    private fun SportScoreCollapsePreviewActivity.collapseRange(): Int =
        binding.scoreDetailsContent.height + (2 * 8 * resources.displayMetrics.density).toInt()

    private fun scrollBy(
        scenario: ActivityScenario<SportScoreCollapsePreviewActivity>,
        amount: (SportScoreCollapsePreviewActivity) -> Int
    ): Int {
        var scrolled = 0
        scenario.onActivity {
            scrolled = amount(it)
            it.binding.mainRecyclerView.scrollBy(0, scrolled)
        }
        return scrolled
    }

    private fun preview(
        appearance: SportScoreCollapsePreviewActivity.Appearance,
        startLoading: Boolean = false,
        block: (ActivityScenario<SportScoreCollapsePreviewActivity>) -> Unit
    ) {
        SportScoreCollapsePreviewActivity.appearance = appearance
        try {
            ActivityScenario.launch<SportScoreCollapsePreviewActivity>(
                Intent(ApplicationProvider.getApplicationContext(), SportScoreCollapsePreviewActivity::class.java)
                    .putExtra(SportScoreCollapsePreviewActivity.EXTRA_START_LOADING, startLoading)
            ).use(block)
        } finally {
            SportScoreCollapsePreviewActivity.appearance = SportScoreCollapsePreviewActivity.Appearance()
        }
    }

    private fun settle() = TestUi.settle(400)

    private fun screenshot(name: String) = Screenshots.capture("sport-cards-screenshots", name)
}
