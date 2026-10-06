import Shared

extension Routes {
    /// Not on iOS until IO-09d2: the recordbook root, the subject page, the period picker and the sheet scores.
    static func recordbook(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
