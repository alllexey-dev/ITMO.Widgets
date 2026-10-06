/// The route of the last URL, intent or tap, waiting for the app to be ready (Android's `MainRouteQueue`).
///
/// A route runs exactly once: `take` hands it out only when the session is ready and its root was selected, and
/// forgets it then. Until that moment a newer `offer` replaces it.
struct RouteQueue {
    private(set) var pending: AppRoute?

    mutating func offer(_ route: AppRoute) {
        pending = route
    }

    /// `selectRoot` returns `false` while the root cannot be selected; the route then stays queued.
    mutating func take(ready: Bool, selectRoot: (ShellTab) -> Bool) -> AppRoute? {
        guard let route = pending, ready, selectRoot(route.root) else { return nil }
        pending = nil
        return route
    }
}
