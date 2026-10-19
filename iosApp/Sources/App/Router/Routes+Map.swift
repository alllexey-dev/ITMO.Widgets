import Shared
import SwiftUI

extension Routes {
    /// The campus map, pushed on the selected tab's stack over the whole screen (docs/features/map.md).
    static func map(_ route: any AppRoute) -> RouteTarget {
        guard let map = route as? AppRoutes.CampusMap else { return .notOnIOS }
        return .fullScreen { AnyView(CampusMapScreen(route: map)) }
    }
}
