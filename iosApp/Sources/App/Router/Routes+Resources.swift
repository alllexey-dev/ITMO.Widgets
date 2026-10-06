import Shared

extension Routes {
    /// Not on iOS until IO-09f: the subject links, their editor, actions and report.
    static func resources(_ route: any AppRoute) -> RouteTarget {
        .notOnIOS
    }
}
