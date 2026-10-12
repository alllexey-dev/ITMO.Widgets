import Shared

extension Routes {
    /// The debug tools stay Android-only.
    static func debug(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
