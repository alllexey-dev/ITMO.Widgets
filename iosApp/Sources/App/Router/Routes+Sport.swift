import Shared
import SwiftUI

extension Routes {
    /// The sport root (`SportTabScreen`), and Where the points come from and another user's sport pushed on a stack.
    /// The lesson sheet is the screens' own sheet, not a route.
    static func sport(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.SportPoints:
            return .swiftUI { AnyView(SportPointsScreen()) }
        case let sport as AppRoutes.UserSport:
            let isu = sport.isu
            let name = sport.name
            let givenName = sport.givenName
            return .swiftUI { AnyView(UserSportScreen(isu: isu, name: name, givenName: givenName)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
