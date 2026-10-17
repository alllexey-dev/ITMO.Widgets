import Foundation
import Shared

/// `itmowidgets://route/<id>`: the URLs placed widgets, Controls and quick actions hold. The ids never change once
/// shipped (`StableIdentifiersTests`); each one names an entry route of the shared model (`EntryRouteParser`).
enum RouteURL {
    /// Registered in the app's Info.plist as `$(APP_URL_SCHEME)`.
    static let scheme = "itmowidgets"
    static let host = "route"

    /// The ids a URL can name: the visible roots in the order of the bar, then the screens.
    static var ids: [String] { ShellTab.visible.map(\.rawValue) + ["qr_pass", "today", "lesson"] }

    static func url(id: String) -> URL? {
        URL(string: "\(scheme)://\(host)/\(id)")
    }

    /// The entry route of a stable id; `nil` for an id this build does not route.
    static func entryRoute(id: String) -> EntryRoute? {
        switch id {
        case "qr_pass": IosRoutes.shared.entryRoute(action: AppEntryIntents.shared.ACTION_OPEN_QR_PASS)
        case "today": IosRoutes.shared.entryRoute(action: AppEntryIntents.shared.ACTION_OPEN_TODAY)
        // A widget's lesson without its arguments: the schedule alone.
        case "lesson": IosRoutes.shared.entryRoute(action: AppEntryIntents.shared.ACTION_OPEN_LESSON)
        default: ShellTab(rawValue: id).flatMap { $0.isAvailable ? EntryRoute(tab: $0.appTab) : nil }
        }
    }

    /// The entry route of an app URL: `itmowidgets://route/<id>`, or an `https` app link (`AppLinks`). An unknown id
    /// becomes the damaged-link explanation above home, as Android's malformed link; `nil` for a URL not the app's.
    /// Widget taps carry arguments (`WidgetRoute`): `lesson?args=<base64url LessonDetailsArgs JSON>` opens the lesson
    /// sheet over the schedule, `sport?lesson=<id>[&predicted=1]` the sport tab on that lesson; arguments that do not read
    /// open the id alone.
    static func entryRoute(url: URL) -> EntryRoute? {
        if url.scheme?.lowercased() == "https" {
            return IosRoutes.shared.linkRoute(link: url.absoluteString)
        }
        guard url.scheme?.lowercased() == scheme, url.host()?.lowercased() == host else { return nil }
        let components = url.pathComponents.filter { $0 != "/" }
        guard components.count == 1, let route = entryRoute(id: components[0]) else {
            return EntryRoute(tab: .home, alert: AppRoutes.LinkUnavailable.shared)
        }
        return argumentRoute(id: components[0], url: url) ?? route
    }

    /// The route a widget's arguments name, nil without them.
    private static func argumentRoute(id: String, url: URL) -> EntryRoute? {
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func value(_ name: String) -> String? { items.first { $0.name == name }?.value }
        switch id {
        case "lesson":
            guard let data = value(WidgetRoute.lessonArguments).flatMap(WidgetRoute.data(base64URL:)),
                  let json = String(data: data, encoding: .utf8)
            else { return nil }
            return IosRoutes.shared.lessonRoute(json: json)
        case ShellTab.sport.rawValue:
            guard let lesson = value(WidgetRoute.sportLesson).flatMap(Int64.init), lesson > 0 else { return nil }
            return IosRoutes.shared.sportRoute(lessonId: lesson, predicted: value(WidgetRoute.sportPredicted) == "1")
        default:
            return nil
        }
    }
}

extension EntryRoute {
    /// An entry route that only selects [tab], or explains a link above it.
    convenience init(tab: AppTab, alert: (any AppRoute)? = nil) {
        self.init(tab: tab, overlay: nil, request: nil, activity: nil, alert: alert, shortcutId: nil)
    }
}

extension ShellTab {
    init(_ tab: AppTab) {
        switch tab {
        case .recordbook: self = .recordbook
        case .schedule: self = .schedule
        case .home: self = .today
        case .sport: self = .sport
        case .me: self = .profile
        }
    }

    var appTab: AppTab {
        switch self {
        case .recordbook: .recordbook
        case .schedule: .schedule
        case .today: .home
        case .sport: .sport
        case .profile: .me
        }
    }
}
