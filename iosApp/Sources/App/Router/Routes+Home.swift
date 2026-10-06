import Shared

extension Routes {
    /// The home root (IO-09a hosts it).
    static func home(_ route: any AppRoute) -> RouteTarget {
        tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
    }
}
