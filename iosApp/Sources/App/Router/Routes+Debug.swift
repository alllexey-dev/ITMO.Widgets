import Shared

extension Routes {
    /// The debug tools stay Android-only (ADR 0016).
    static func debug(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
