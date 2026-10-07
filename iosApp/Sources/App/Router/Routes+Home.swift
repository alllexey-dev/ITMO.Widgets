import Shared

extension Routes {
    /// The home root: the shell's home stack shows the Compose feed (`HomeScreen`, IO-09a).
    static func home(_ route: any AppRoute) -> RouteTarget {
        tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
    }
}
