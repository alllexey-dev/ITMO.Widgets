import Shared

extension Routes {
    /// The schedule root; changes, lesson details, the friend picker, the pending sport sheet and another user's
    /// schedule come with IO-09b.
    static func schedule(_ route: any AppRoute) -> RouteTarget {
        tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
    }
}
