import Shared

extension Routes {
    /// Not on iOS until IO-15b: the `.ics` export.
    static func calendar(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
