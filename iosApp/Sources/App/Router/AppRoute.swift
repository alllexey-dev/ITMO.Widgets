import Foundation

/// Where an entry into the app leads: a widget or Control URL, an App Intent, a tap, a link. IO-06c replaces it with
/// the shared `AppRoute` model (SH-1a2).
enum AppRoute: Equatable {
    /// A root of the tab bar, opened as it is.
    case root(ShellTab)
    /// The QR pass above home (`qr_pass`, the Control, the QR widget and the App Shortcut).
    case qrPass
    /// The schedule root on today (`today`, the App Shortcut).
    case today
    /// An app URL whose route this build does not know; explained above home, as Android's damaged link.
    case linkUnavailable

    /// The URL scheme of `itmowidgets://route/<id>`, registered in the app's Info.plist as `$(APP_URL_SCHEME)`.
    static let urlScheme = "itmowidgets"
    static let urlHost = "route"

    /// The root this route opens over; it runs only once that root is selected.
    var root: ShellTab {
        switch self {
        case let .root(tab): tab
        case .qrPass, .linkUnavailable: .home
        case .today: .schedule
        }
    }

    /// The stable route id of `itmowidgets://route/<id>`; placed widgets and Controls hold these URLs, so an id
    /// never changes once shipped (`StableIdentifiersTests`).
    var id: String? {
        switch self {
        case let .root(tab): tab.isAvailable ? tab.rawValue : nil
        case .qrPass: "qr_pass"
        case .today: "today"
        case .linkUnavailable: nil
        }
    }

    /// Every route a URL can name, in the order of the bar and then the screens.
    static let routable: [AppRoute] = ShellTab.visible.map(AppRoute.root) + [.qrPass, .today]

    init?(id: String) {
        guard let route = Self.routable.first(where: { $0.id == id }) else { return nil }
        self = route
    }

    /// `itmowidgets://route/<id>`; `nil` for a URL that is not the app's, `linkUnavailable` for an unknown id.
    init?(url: URL) {
        guard url.scheme?.lowercased() == Self.urlScheme, url.host()?.lowercased() == Self.urlHost else { return nil }
        let components = url.pathComponents.filter { $0 != "/" }
        guard components.count == 1, let route = AppRoute(id: components[0]) else {
            self = .linkUnavailable
            return
        }
        self = route
    }

    var url: URL? {
        id.flatMap { URL(string: "\(Self.urlScheme)://\(Self.urlHost)/\($0)") }
    }
}
