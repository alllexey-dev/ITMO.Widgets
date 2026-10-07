package dev.alllexey.itmowidgets.app.shell

import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.designsystem.gesture.TabSwipeDefaults

/**
 * The tab pager's pages: the visible tabs in bar order (design.md "Tab swipe"). The only place that maps an [AppTab] to
 * a page index and back; routes, widgets, shortcuts and links always name an [AppTab], never a page.
 */
internal object TabPages {

    /** Every tab is visible, so the pages are [AppTab.entries]; RECORDBOOK and ME are the ends. */
    private val tabs: List<AppTab> = AppTab.entries

    val count: Int get() = tabs.size

    fun pageOf(tab: AppTab): Int = tabs.indexOf(tab)

    fun tabAt(page: Int): AppTab = tabs[page.coerceIn(0, tabs.lastIndex)]

    /**
     * The tab a swipe that started on [origin] lands on if released at [position] (the pager's current page plus its
     * offset fraction): the neighbour once the pages moved [TabSwipeDefaults.commitFraction] of the width towards it,
     * never further, never past an end.
     */
    fun swipeTarget(origin: AppTab, position: Float): AppTab {
        val start = pageOf(origin)
        val moved = position - start
        val page = when {
            moved >= TabSwipeDefaults.commitFraction -> start + TabSwipeDefaults.pagesPerSwipe
            moved <= -TabSwipeDefaults.commitFraction -> start - TabSwipeDefaults.pagesPerSwipe
            else -> start
        }
        return tabAt(page)
    }

    /**
     * Whether a horizontal swipe on the tab content switches the tab: only on the tabs [surface], with nothing above the
     * tab root in [backStack] (no overlay, sheet or dialog), where the bottom bar (not a rail) shows, and never under
     * touch exploration (TalkBack), where the bar's buttons are the way to switch.
     */
    fun swipeEnabled(
        surface: ShellSurface,
        backStack: ShellBackStack,
        touchExploration: Boolean,
        barShown: Boolean = true,
    ): Boolean = surface is ShellSurface.Tabs &&
        backStack.overlays.isEmpty() &&
        backStack.floating.isEmpty() &&
        barShown &&
        !touchExploration
}
