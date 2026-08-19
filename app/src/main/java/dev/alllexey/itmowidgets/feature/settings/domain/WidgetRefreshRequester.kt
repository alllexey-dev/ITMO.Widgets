package dev.alllexey.itmowidgets.feature.settings.domain

/** Starts a user-requested refresh for every supported home-screen widget. */
interface WidgetRefreshRequester {

    fun refreshAll()
}
