import Shared
import SwiftUI

extension Routes {
    /// The QR pass above home: the Compose route of `:shared:feature-qr` (IO-21).
    static func qr(_ route: any AppRoute) -> RouteTarget {
        route is AppRoutes.QrPass ? .compose { AnyView(QrPassScreen()) } : .notOnIOS
    }
}
