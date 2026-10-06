import Shared

extension Routes {
    /// Settings and diagnostics come with IO-08a.
    static func settings(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
