import Shared
import SwiftUI

extension Routes {
    /// The profile root (`MeTabScreen`); Friends, a person card and a user's friends are SwiftUI screens pushed on the
    /// selected tab's stack. A user's schedule and sport are the schedule's and sport's keys.
    static func social(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.Friends:
            return .swiftUI { AnyView(FriendsScreen()) }
        case let profile as AppRoutes.UserProfile:
            let isu = profile.isu
            return .swiftUI { AnyView(UserProfileScreen(isu: isu)) }
        case let friends as AppRoutes.UserFriends:
            let isu = friends.isu
            let name = friends.name
            return .swiftUI { AnyView(UserFriendsScreen(isu: isu, name: name)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
