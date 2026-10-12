import Shared
import SwiftUI

extension Routes {
    /// Sign-in and the first-run flow are gate surfaces; My ITMO web and the connected devices are SwiftUI screens
    /// on the tab's stack, the device connection sheet a sheet. The demo refuses both before the map (`ShellGate`).
    static func account(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.Auth, is AppRoutes.Onboarding:
            return .gate
        case is AppRoutes.MyItmoWeb:
            return .swiftUI { AnyView(MyItmoWebScreen()) }
        case let webLogin as AppRoutes.WebLogin:
            return .sheet(.webLogin(code: webLogin.code))
        case is AppRoutes.Connections:
            return .swiftUI { AnyView(ConnectionsScreen()) }
        default:
            return .notOnIOS
        }
    }
}
