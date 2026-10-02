package dev.alllexey.itmowidgets.app

/**
 * The route of the last widget, notification, tile or shortcut intent, waiting for the app to be ready.
 *
 * A route runs exactly once: [take] hands it out only when the session is ready and its root was selected,
 * and forgets it then. Until that moment a newer [offer] replaces it.
 */
class MainRouteQueue {

    var pending: MainActivityRoute? = null
        private set

    fun offer(route: MainActivityRoute) {
        pending = route
    }

    /** [selectRoot] returns `false` while the root cannot change (state saved); the route then stays queued. */
    fun take(ready: Boolean, selectRoot: (Int) -> Boolean): MainActivityRoute? {
        val route = pending ?: return null
        if (!ready || !selectRoot(route.rootDestination)) return null
        pending = null
        return route
    }
}
