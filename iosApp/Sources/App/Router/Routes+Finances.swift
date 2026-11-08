import Shared
import SwiftUI

extension Routes {
    /// `tuition_title`, pushed on the profile's stack (docs/features/finances.md).
    static func finances(_ route: any AppRoute) -> RouteTarget {
        guard route is AppRoutes.Tuition else { return .notOnIOS }
        return .swiftUI { AnyView(TuitionScreen()) }
    }
}
