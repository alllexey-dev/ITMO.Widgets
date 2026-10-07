package dev.alllexey.itmowidgets.app.shell

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.designsystem.gesture.TabSwipeDefaults
import dev.alllexey.itmowidgets.designsystem.tokens.rememberReducedMotion
import kotlin.math.abs

/**
 * The tab layer's pages and the swipe moving them. [barTab] is what the bar marks; the shell's back stack stays the
 * one source of the selected tab, and the pages follow it.
 */
@Stable
internal class TabPagerState(initial: AppTab) {

    internal val pager: PagerState = TabPagesState(TabPages.pageOf(initial))

    /** The tab the pages had settled on when the current swipe began; null while no swipe moves them. */
    internal var swipeOrigin: AppTab? by mutableStateOf(null)

    /** True while the pages jump to the selected tab, which is no swipe. */
    internal var jumping: Boolean = false

    /** During a swipe the tab it lands on if released now (it flips at the commit threshold), [selected] otherwise. */
    fun barTab(selected: AppTab): AppTab =
        swipeOrigin?.let { TabPages.swipeTarget(it, pager.currentPage + pager.currentPageOffsetFraction) } ?: selected
}

/**
 * The pages start on [selected], the tab of the restored back stack, so recreation and process death never flash
 * another tab first; so do the pages that come back when touch exploration ends.
 */
@Composable
internal fun rememberTabPagerState(selected: AppTab, touchExploration: Boolean): TabPagerState =
    remember(touchExploration) { TabPagerState(selected) }

/**
 * The tab roots in a [HorizontalPager] (design.md "Tab swipe"): one tab per swipe, no wrap-around, committed past
 * [TabSwipeDefaults.commitFraction] or by a fling. The pager starts a drag only after [TabSwipeDefaults.slopMultiplier]
 * system touch slops while every page keeps the system's, so a diagonal drag scrolls the root's list. Inner horizontal
 * content takes its own drags through the kit's `tabSwipeHandover` and `tabSwipeBlocked`; the shell adds nothing.
 *
 * [selected] moves the pages at once, without sliding through the tabs between (a tap, Back, a route), and first
 * cancels a swipe in progress; a swipe that settles on another tab reports it to [onSwipe]. Only the shown page is
 * composed once the pages settle. [swipeEnabled] off keeps the pages where [selected] is.
 * One threshold haptic per swipe when the target flips; none on taps and none when it flips back.
 *
 * Under [touchExploration] there are no pages at all, only [selected]'s root: a [HorizontalPager] always carries a
 * collection, a scroll range and page actions in its semantics, and TalkBack must find nothing but the tab content
 * and the bar's buttons.
 */
@Composable
internal fun TabPager(
    state: TabPagerState,
    selected: AppTab,
    swipeEnabled: Boolean,
    touchExploration: Boolean,
    onSwipe: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (AppTab) -> Unit,
) {
    if (touchExploration) {
        Box(modifier) { key(selected) { page(selected) } }
    } else {
        SwipePager(state, selected, swipeEnabled, onSwipe, modifier, page)
    }
}

@Composable
private fun SwipePager(
    state: TabPagerState,
    selected: AppTab,
    swipeEnabled: Boolean,
    onSwipe: (AppTab) -> Unit,
    modifier: Modifier,
    page: @Composable (AppTab) -> Unit,
) {
    val pager = state.pager
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)

    LaunchedEffect(pager, selected, swipeEnabled) {
        val target = TabPages.pageOf(selected)
        if (state.swipeOrigin == null && pager.currentPage == target && pager.isSettled()) return@LaunchedEffect
        state.jumping = true
        try {
            state.swipeOrigin = null
            // Takes the pages from the finger (or from a running settle) before jumping.
            pager.scroll(MutatePriority.PreventUserInput) {}
            pager.scrollToPage(target)
        } finally {
            state.jumping = false
        }
    }
    // Any movement but a jump is a swipe: the pager's own drag, or what an inner scroller at its edge hands over
    // through nested scrolling.
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress to pager.isSettled() }.collect { (scrolling, atPage) ->
            if (scrolling) {
                if (!state.jumping && state.swipeOrigin == null) state.swipeOrigin = TabPages.tabAt(pager.settledPage)
            } else if (atPage) {
                state.swipeOrigin = null
                val landed = TabPages.tabAt(pager.currentPage)
                if (!state.jumping && landed != currentSelected) currentOnSwipe(landed)
            }
        }
    }
    LaunchedEffect(state) {
        var played: AppTab? = null
        snapshotFlow { state.swipeOrigin to state.barTab(currentSelected) }.collect { (origin, bar) ->
            if (origin == null) {
                played = null
            } else if (bar != origin && played != origin) {
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                played = origin
            }
        }
    }

    val system = LocalViewConfiguration.current
    val pagerConfiguration = remember(system) { SlopScaled(system, TabSwipeDefaults.slopMultiplier) }
    val settle = if (rememberReducedMotion()) {
        snap()
    } else {
        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Int.VisibilityThreshold.toFloat())
    }
    val fling = PagerDefaults.flingBehavior(
        state = pager,
        pagerSnapDistance = PagerSnapDistance.atMost(TabSwipeDefaults.pagesPerSwipe),
        snapAnimationSpec = settle,
        snapPositionalThreshold = TabSwipeDefaults.commitFraction,
    )
    CompositionLocalProvider(LocalViewConfiguration provides pagerConfiguration) {
        HorizontalPager(
            state = pager,
            modifier = modifier.testTag(ShellTags.TAB_PAGER),
            beyondViewportPageCount = 0,
            flingBehavior = fling,
            userScrollEnabled = swipeEnabled,
            key = TabPages::tabAt,
            overscrollEffect = null,
        ) { index ->
            CompositionLocalProvider(LocalViewConfiguration provides system) { page(TabPages.tabAt(index)) }
        }
    }
}

/** Whether TalkBack explores by touch now; the tab swipe and the pages are off then. */
@Composable
internal fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var exploring by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { exploring = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return exploring
}

/** At a page boundary, within a pixel. */
private fun PagerState.isSettled(): Boolean {
    val pageSize = layoutInfo.pageSize
    return pageSize == 0 || abs(currentPageOffsetFraction) * pageSize < 1f
}

private class TabPagesState(page: Int) : PagerState(page, 0f) {
    override val pageCount: Int get() = TabPages.count
}

private class SlopScaled(system: ViewConfiguration, multiplier: Float) : ViewConfiguration by system {
    override val touchSlop: Float = system.touchSlop * multiplier
}
