import Shared
import SwiftUI

extension Routes {
    /// Settings and the error journal are SwiftUI screens over the shared ViewModels (IO-08a): each `Settings` key is
    /// one page, so a sub-page pushes another key; the debug tools stay Android-only.
    static func settings(_ route: any AppRoute) -> RouteTarget {
        if let settings = route as? AppRoutes.Settings {
            let page = settings.page
            return .swiftUI { AnyView(SettingsScreen(page: page)) }
        }
        if route is AppRoutes.Diagnostics {
            return .swiftUI { AnyView(DiagnosticsScreen()) }
        }
        return .notOnIOS
    }
}
