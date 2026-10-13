import Shared
import SwiftUI

extension Routes {
    /// The pass sheet: the today accessory, widgets, the Control and shortcuts all open it.
    static func qr(_ route: any AppRoute) -> RouteTarget {
        route is AppRoutes.QrPass ? .composeSheet { AnyView(QrPassScreen()) } : .notOnIOS
    }
}
