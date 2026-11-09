import Shared
import SwiftUI

extension Routes {
    /// Sign-in and the first-run flow are gate surfaces; My ITMO web, the devices screen and a connected client are
    /// SwiftUI screens on the tab's stack, so is `me_isu_hub`, the pairing sheet a route sheet with its link, the Wi-Fi sheet a route
    /// sheet. The demo refuses all but the gate and the Wi-Fi sheet before the map (`ShellGate`).
    static func account(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.Auth, is AppRoutes.Onboarding:
            return .gate
        case let web as AppRoutes.MyItmoWeb:
            return .swiftUI { AnyView(MyItmoWebScreen(path: web.path)) }
        case let pairing as AppRoutes.Pairing:
            return .swiftUISheet { AnyView(PairingSheet(link: pairing.link)) }
        case is AppRoutes.Devices:
            return .swiftUI { AnyView(DevicesScreen()) }
        case let connection as AppRoutes.Connection:
            return .swiftUI { AnyView(ConnectedClientScreen(id: connection.id)) }
        case is AppRoutes.Isu:
            return .swiftUI { AnyView(IsuHubScreen()) }
        case is AppRoutes.Wifi:
            return .swiftUISheet { AnyView(WifiSheet()) }
        default:
            return .notOnIOS
        }
    }
}
