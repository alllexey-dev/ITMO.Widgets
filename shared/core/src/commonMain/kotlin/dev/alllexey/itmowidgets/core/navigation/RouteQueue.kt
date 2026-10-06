package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/**
 * The route of the last entry intent, waiting for the app to be ready; saved with the shell's state.
 *
 * A route runs exactly once: [take] hands it out only when the shell is ready and its tab was selected, and forgets
 * it then. Until that moment a newer [offer] replaces it.
 */
@Serializable
class RouteQueue {

    var pending: EntryRoute? = null
        private set

    fun offer(route: EntryRoute) {
        pending = route
    }

    /** [selectTab] returns `false` while the tab cannot change; the route then stays queued. */
    fun take(ready: Boolean, selectTab: (AppTab) -> Boolean): EntryRoute? {
        val route = pending ?: return null
        if (!ready || !selectTab(route.tab)) return null
        pending = null
        return route
    }
}
