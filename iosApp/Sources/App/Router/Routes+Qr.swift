import Shared
import SwiftUI

extension Routes {
    /// The QR pass above home; IO-21 swaps the fixture for the CMP QR route.
    static func qr(_ route: any AppRoute) -> RouteTarget {
        route is AppRoutes.QrPass ? .compose { AnyView(FixtureQrPassScreen()) } : .notOnIOS
    }
}
