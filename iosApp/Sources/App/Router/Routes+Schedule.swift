import Shared
import SwiftUI

extension Routes {
    /// The schedule root (`ScheduleScreen`), another user's schedule pushed on the selected tab's stack, and the
    /// lesson sheet, the month sheet and the friend picker above the shell, all SwiftUI.
    static func schedule(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case let user as AppRoutes.UserSchedule:
            let isu = user.isu
            let name = user.name
            return .swiftUI { AnyView(UserScheduleScreen(isu: isu, name: name)) }
        case is AppRoutes.LessonDetails, is AppRoutes.PendingSportDetails:
            return .swiftUISheet { AnyView(LessonSheet(route: route)) }
        case let month as AppRoutes.ScheduleMonth:
            return .swiftUISheet { AnyView(ScheduleMonthSheet(route: month)) }
        case let picker as AppRoutes.FriendSelector:
            return .swiftUISheet { AnyView(FriendPickerSheet(route: picker)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
