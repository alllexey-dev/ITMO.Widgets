import Shared

extension Routes {
    /// The shell's own keys: a malformed app link explains itself in a sheet above home.
    static func shell(_ route: any AppRoute) -> RouteTarget {
        route is AppRoutes.LinkUnavailable ? .sheet(.linkUnavailable) : .notOnIOS
    }
}
