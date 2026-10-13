import Shared
import SwiftUI

extension Routes {
    /// The home root (the shell's today stack shows `TodayScreen`) and the bell's sheet over it.
    static func home(_ route: any AppRoute) -> RouteTarget {
        if route is AppRoutes.Bell { return .swiftUISheet { AnyView(BellSheet()) } }
        return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
    }
}
