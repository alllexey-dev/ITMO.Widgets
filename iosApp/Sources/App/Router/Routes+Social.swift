import Shared

extension Routes {
    /// The me root; friends, search, profiles and a user's friends come with IO-09e.
    static func social(_ route: any AppRoute) -> RouteTarget {
        tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
    }
}
