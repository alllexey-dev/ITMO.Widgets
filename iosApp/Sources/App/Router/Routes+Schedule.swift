import Shared
import SwiftUI

extension Routes {
    /// The schedule root (`ScheduleScreen`); another user's schedule and the changes are Compose routes pushed on the
    /// selected tab's stack, the lesson sheet, the pending sport sheet and the friend picker Compose sheets above the
    /// shell (IO-09b).
    static func schedule(_ route: any AppRoute) -> RouteTarget {
        switch route {
        case let user as AppRoutes.UserSchedule:
            let isu = user.isu
            let name = user.name
            return .compose { AnyView(UserScheduleScreen(isu: isu, name: name)) }
        case is AppRoutes.ScheduleChanges:
            return .compose { AnyView(ScheduleChangesScreen()) }
        case let details as AppRoutes.LessonDetails:
            let lesson = details.args
            return .composeSheet { AnyView(LessonDetailsSheet(lesson: lesson)) }
        case let pending as AppRoutes.PendingSportDetails:
            let booking = pending.args
            return .composeSheet { AnyView(PendingSportDetailsSheet(booking: booking)) }
        case let picker as AppRoutes.FriendSelector:
            return .composeSheet { AnyView(FriendSelectorSheet(picker: picker)) }
        default:
            return tab(of: route).map(RouteTarget.tab) ?? .notOnIOS
        }
    }
}
