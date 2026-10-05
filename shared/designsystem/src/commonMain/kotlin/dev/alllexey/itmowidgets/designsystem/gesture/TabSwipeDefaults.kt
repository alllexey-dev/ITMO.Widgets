package dev.alllexey.itmowidgets.designsystem.gesture

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The one set of numbers both shells use for the horizontal swipe between the bottom tabs (design.md, Tab swipe). */
object TabSwipeDefaults {

    /**
     * The tab pager starts a drag only after this many system touch slops, so a diagonal drag reaches the slop of a
     * vertical list first and scrolls the list.
     */
    const val slopMultiplier: Float = 2f

    /** A released drag switches the tab once it has moved this fraction of the width, or on a fling. */
    const val commitFraction: Float = 0.35f

    /** One swipe moves one tab, never more. */
    const val pagesPerSwipe: Int = 1

    /**
     * iOS only: the tab swipe never starts this close to the leading edge, which belongs to the back swipe. Android
     * needs no dead zone, because its back edges are the system's and win over any app gesture.
     */
    val edgeDeadZone: Dp = 24.dp
}
