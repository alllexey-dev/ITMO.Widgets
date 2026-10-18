import Shared
import SwiftUI

extension Routes {
    /// The campus map, pushed on the selected tab's stack.
    static func map(_ route: any AppRoute) -> RouteTarget {
        guard let map = route as? AppRoutes.CampusMap else { return .notOnIOS }
        return .swiftUI { AnyView(CampusMapScreen(route: map)) }
    }
}
