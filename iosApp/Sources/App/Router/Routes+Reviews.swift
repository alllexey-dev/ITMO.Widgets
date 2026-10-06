import Shared

extension Routes {
    /// Not on iOS until IO-09f: the review editor and the review report.
    static func reviews(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
