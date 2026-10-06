import Shared

extension Routes {
    /// The sport root; another user's sport and the booking confirmation come with IO-09c.
    static func sport(_ route: any AppRoute) -> RouteTarget {
        tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
    }
}
