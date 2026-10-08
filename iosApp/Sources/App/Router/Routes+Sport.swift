import Shared
import SwiftUI

extension Routes {
    /// The sport root (`SportTabScreen`) and another user's sport, the Compose route pushed on the selected tab's
    /// stack (IO-09c). The details sheet is the root's own sheet, not a route; `CancelBookingConfirm` asks for the
    /// schedule's pending sheet, which IO-09b brings.
    static func sport(_ route: any AppRoute) -> RouteTarget {
        if let sport = route as? AppRoutes.UserSport {
            let isu = sport.isu
            let name = sport.name
            return .compose { AnyView(UserSportScreen(isu: isu, name: name)) }
        }
        return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
    }
}
