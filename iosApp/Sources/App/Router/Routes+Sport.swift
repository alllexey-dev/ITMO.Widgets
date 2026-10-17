import Shared
import SwiftUI

extension Routes {
    /// The sport root (`SportTabScreen`), Where the points come from pushed on its stack, and another user's
    /// sport, the Compose route. The lesson sheet is the screens' own sheet, not a route.
    static func sport(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.SportPoints:
            return .swiftUI { AnyView(SportPointsScreen()) }
        case let sport as AppRoutes.UserSport:
            let isu = sport.isu
            let name = sport.name
            return .compose { AnyView(UserSportScreen(isu: isu, name: name)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
