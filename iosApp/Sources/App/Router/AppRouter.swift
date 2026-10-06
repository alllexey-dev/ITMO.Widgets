import Foundation
import Observation

/// A screen pushed onto a tab's `NavigationStack`.
enum ShellDestination: Hashable {
    case qrPass

    /// Who draws the top bar (the chrome rule, `docs/ios.md` Shell).
    var chrome: ShellChrome {
        switch self {
        case .qrPass: .compose
        }
    }
}

/// A sheet above the shell.
enum ShellSheet: String, Identifiable {
    case linkUnavailable

    var id: String { rawValue }
}

/// The shell's navigation state and the one way into it: every URL, App Intent, tap and link calls `open`.
///
/// A route waits in a `RouteQueue` until the session is ready and the tab bar is on screen, then runs once: it
/// selects its root and shows its screen there. The stacks and the sheet are plain state the shell binds to, so
/// the tab container (`ShellTabs`) can change without touching the router.
@MainActor
@Observable
final class AppRouter {
    var selectedTab: ShellTab = .launch
    var sheet: ShellSheet?
    /// Bumped by each `today` route; the schedule root scrolls to today when it changes.
    private(set) var todayRequest = 0

    private var paths: [ShellTab: [ShellDestination]] = [:]
    private var queue = RouteQueue()
    private var isSessionReady = false
    private var isShellMounted = false

    var pendingRoute: AppRoute? { queue.pending }

    func path(of tab: ShellTab) -> [ShellDestination] {
        paths[tab, default: []]
    }

    func setPath(_ path: [ShellDestination], of tab: ShellTab) {
        paths[tab] = path
    }

    func open(_ route: AppRoute) {
        queue.offer(route)
        drain()
    }

    /// Opens an `itmowidgets://route/<id>` URL; returns `false` for a URL that is not the app's.
    @discardableResult
    func open(url: URL) -> Bool {
        guard let route = AppRoute(url: url) else { return false }
        open(route)
        return true
    }

    /// Opens a route by its stable id (App Intents, quick actions); returns `false` for an unknown id.
    @discardableResult
    func open(id: String) -> Bool {
        guard let route = AppRoute(id: id) else { return false }
        open(route)
        return true
    }

    /// The session gate: a route waits while the session is loading or signed out. Leaving the session drops the
    /// stacks and the sheet, so the next session starts on home.
    func sessionChanged(ready: Bool) {
        if isSessionReady && !ready {
            paths = [:]
            sheet = nil
            selectedTab = .launch
        }
        isSessionReady = ready
        drain()
    }

    /// The tab bar is on screen; until then no root can be selected.
    func shellMounted(_ mounted: Bool) {
        isShellMounted = mounted
        drain()
    }

    private func drain() {
        guard let route = queue.take(ready: isSessionReady, selectRoot: select) else { return }
        run(route)
    }

    private func select(_ tab: ShellTab) -> Bool {
        guard isShellMounted, tab.isAvailable else { return false }
        selectedTab = tab
        return true
    }

    private func run(_ route: AppRoute) {
        switch route {
        case .root:
            break
        case .qrPass:
            sheet = nil
            paths[.home] = [.qrPass]
        case .today:
            sheet = nil
            paths[.schedule] = []
            todayRequest += 1
        case .linkUnavailable:
            sheet = .linkUnavailable
        }
    }
}
