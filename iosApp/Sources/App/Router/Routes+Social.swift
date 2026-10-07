import Shared
import SwiftUI

extension Routes {
    /// The me root (`MeTabScreen`); friends, search, a profile and a user's friends are the social Compose routes
    /// pushed on the selected tab's stack (IO-09e). A user's schedule and sport are the schedule's and sport's keys.
    static func social(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case is AppRoutes.Friends:
            return .compose { AnyView(FriendsScreen()) }
        case is AppRoutes.UserSearch:
            return .compose { AnyView(UserSearchScreen()) }
        case let profile as AppRoutes.UserProfile:
            let isu = profile.isu
            return .compose { AnyView(UserProfileScreen(isu: isu)) }
        case let friends as AppRoutes.UserFriends:
            let isu = friends.isu
            let name = friends.name
            return .compose { AnyView(UserFriendsScreen(isu: isu, name: name)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
