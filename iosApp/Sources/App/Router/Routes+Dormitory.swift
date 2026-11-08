import Shared
import SwiftUI

extension Routes {
    /// `dormitory_title`, pushed on the profile's stack (docs/features/dormitory.md).
    static func dormitory(_ route: any AppRoute) -> RouteTarget {
        guard route is AppRoutes.Dormitory else { return .notOnIOS }
        return .swiftUI { AnyView(DormitoryScreen()) }
    }
}
