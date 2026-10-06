import Shared

extension Routes {
    /// Sign-in and the first-run flow are gate surfaces; My ITMO web and the web sign-in come with IO-08b.
    static func account(_ route: any AppRoute) -> RouteTarget {
        route is AppRoutes.Auth || route is AppRoutes.Onboarding ? .gate : .notOnIOS
    }
}
