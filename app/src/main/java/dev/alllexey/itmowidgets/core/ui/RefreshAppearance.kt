package dev.alllexey.itmowidgets.core.ui

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import dev.alllexey.itmowidgets.core.util.color

/** Opt-in app refresh palette; the light ITMO.ID web sign-in keeps library defaults. */
fun SwipeRefreshLayout.applyAppRefreshColors() {
    val colors = context.color
    setColorSchemeColors(colors.primary)
    setProgressBackgroundColorSchemeColor(colors.background)
}
