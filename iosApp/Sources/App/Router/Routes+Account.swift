import Shared
import SwiftUI

extension Routes {
    /// Sign-in and the first-run flow are gate surfaces (IO-07b); My ITMO web is a SwiftUI screen on the tab's stack,
    /// the web sign-in a sheet (IO-08b). The demo refuses both before the map (`ShellGate`).
    static func account(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.Auth, is AppRoutes.Onboarding:
            return .gate
        case is AppRoutes.MyItmoWeb:
            return .swiftUI { AnyView(MyItmoWebScreen()) }
        case let webLogin as AppRoutes.WebLogin:
            return .sheet(.webLogin(code: webLogin.code))
        default:
            return .notOnIOS
        }
    }
}
