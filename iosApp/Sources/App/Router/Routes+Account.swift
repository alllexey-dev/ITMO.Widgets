import Shared
import SwiftUI

extension Routes {
    /// Sign-in and the first-run flow are gate surfaces; My ITMO web, the devices screen and a connected client are
    /// SwiftUI screens on the tab's stack, the pairing sheet a route sheet with its link, the Wi-Fi sheet a route
    /// sheet. The demo refuses all but the gate and the Wi-Fi sheet before the map (`ShellGate`).
    static func account(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.Auth, is AppRoutes.Onboarding:
            return .gate
        case is AppRoutes.MyItmoWeb:
            return .swiftUI { AnyView(MyItmoWebScreen()) }
        case let pairing as AppRoutes.Pairing:
            return .swiftUISheet { AnyView(PairingSheet(link: pairing.link)) }
        case is AppRoutes.Devices:
            return .swiftUI { AnyView(DevicesScreen()) }
        case let connection as AppRoutes.Connection:
            return .swiftUI { AnyView(ConnectedClientScreen(id: connection.id)) }
        case is AppRoutes.Wifi:
            return .swiftUISheet { AnyView(WifiSheet()) }
        default:
            return .notOnIOS
        }
    }
}
