import Shared
import SwiftUI

extension Routes {
    /// Settings and the error journal are SwiftUI screens over the shared ViewModels: each `Settings` key is
    /// one page, so a sub-page pushes another key; «Подключение и данные» (`SERVICES`,
    /// `PRIVACY`) and «Уведомления» are screens of their own. The debug tools stay Android-only.
    static func settings(_ route: any AppRoute) -> RouteTarget {
        if let settings = route as? AppRoutes.Settings {
            let page = settings.page
            switch SettingsPage.companion.fromArgument(value: page) {
            case .services, .privacy:
                return .swiftUI { AnyView(ConnectionDataScreen()) }
            case .notifications:
                return .swiftUI { AnyView(NotificationSettingsScreen()) }
            default:
                return .swiftUI { AnyView(SettingsScreen(page: page)) }
            }
        }
        if route is AppRoutes.Diagnostics {
            return .swiftUI { AnyView(DiagnosticsScreen()) }
        }
        return .notOnIOS
    }
}
