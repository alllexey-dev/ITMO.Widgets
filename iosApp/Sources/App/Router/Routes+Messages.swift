import Shared
import SwiftUI

extension Routes {
    /// `isu_messages_title`, pushed on the profile's stack (docs/features/isu-messages.md).
    static func messages(_ route: any AppRoute) -> RouteTarget {
        guard route is AppRoutes.IsuMessages else { return .notOnIOS }
        return .swiftUI { AnyView(IsuMessagesScreen()) }
    }
}
