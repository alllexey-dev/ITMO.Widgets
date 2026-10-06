import Foundation
import Observation
import Shared

/// A key of the shared route model as a Swift dictionary key: Kotlin's `equals` and `hashCode`, which the Kotlin
/// objects expose as `isEqual` and `hash`.
struct RouteKey: Hashable {
    let route: any AppRoute

    init(_ route: any AppRoute) {
        self.route = route
    }

    private var object: NSObject {
        // Every Kotlin object is an NSObject (KotlinBase).
        route as! NSObject
    }

    static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.object.isEqual(rhs.object)
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(object.hash)
    }
}

/// A screen pushed onto a tab's `NavigationStack`: a shared key whose `RouteTarget` is a screen.
struct ShellDestination: Hashable {
    let key: RouteKey

    init(_ route: any AppRoute) {
        key = RouteKey(route)
    }

    var route: any AppRoute { key.route }

    /// Who draws the top bar (the chrome rule, `docs/ios.md` Shell).
    var chrome: ShellChrome {
        Routes.target(for: route).chrome ?? .native
    }
}

/// A sheet above the shell.
enum ShellSheet: String, Identifiable {
    case linkUnavailable

    var id: String { rawValue }
}

/// What an in-app `open` did.
enum RouteOpening: Equatable {
    case opened
    /// No ready session, the first-run flow is not passed, a gate surface, or another sheet is shown.
    case ignored
    /// The key needs a real account (`ShellGate`); the caller says `error_demo_unavailable`.
    case refusedInDemo
    /// No iOS screen for the key (`RouteTarget.notOnIOS`).
    case notOnIOS
}

/// The shell's navigation state and the one way into it, on the shared route model (SH-1a2): every URL, App Intent,
/// link and notification becomes an `EntryRoute` (`EntryRouteParser`, `AppLinks`), every tap opens an `AppRoute`.
///
/// An entry route waits in the shared `RouteQueue` until `ShellGate` reports the tabs and the tab bar is on screen,
/// then runs once: it selects its tab, opens its overlay, hands its request to the tab's root and shows its alert.
/// `Routes` decides how iOS shows each key. The stacks and the sheet are plain state the shell binds to, so the tab
/// container (`ShellTabs`) can change without touching the router.
@MainActor
@Observable
final class AppRouter {
    var selectedTab: ShellTab = .launch
    var sheet: ShellSheet?
    /// Bumped by each `ScheduleToday` request; the schedule root scrolls to today when it changes.
    private(set) var todayRequest = 0

    private var paths: [ShellTab: [ShellDestination]] = [:]
    private var requests: [ShellTab: TabRequest] = [:]
    private var results: [RouteKey: (Any) -> Void] = [:]
    @ObservationIgnored private let queue = RouteQueue()
    private var session: SessionState = SessionStateInitializing.shared
    private var onboarding: OnboardingStatus = .unknown
    private var isShellMounted = false

    var pendingRoute: EntryRoute? { queue.pending }

    func path(of tab: ShellTab) -> [ShellDestination] {
        paths[tab, default: []]
    }

    func setPath(_ path: [ShellDestination], of tab: ShellTab) {
        paths[tab] = path
    }

    // MARK: Entry routes

    /// Queues an entry route; a newer one replaces a waiting one. Returns `false` for a tab the shell does not show.
    @discardableResult
    func open(entry route: EntryRoute) -> Bool {
        guard ShellTab(route.tab).isAvailable else { return false }
        queue.offer(route: route)
        drain()
        return true
    }

    /// Opens an app URL (`RouteURL`); returns `false` for a URL that is not the app's.
    @discardableResult
    func open(url: URL) -> Bool {
        RouteURL.entryRoute(url: url).map { open(entry: $0) } ?? false
    }

    /// Opens a route by its stable id (App Intents, quick actions); returns `false` for an unknown id.
    @discardableResult
    func open(id: String) -> Bool {
        RouteURL.entryRoute(id: id).map { open(entry: $0) } ?? false
    }

    // MARK: In-app routes

    /// Opens a key from a tap, if `ShellGate` allows it and iOS has a screen for it.
    @discardableResult
    func open(_ route: any AppRoute) -> RouteOpening {
        switch ShellGate.shared.check(route: route, session: session, onboarding: onboarding) {
        case .ignore: .ignored
        case .refuseInDemo: .refusedInDemo
        case .open: present(route)
        }
    }

    /// Opens a key whose screen answers its opener (the friend picker answers the schedule): the router holds
    /// `onResult` until the screen calls `deliver(_:from:)`; the next open of an equal key replaces it, leaving the
    /// session drops it.
    @discardableResult
    func open<Result>(_ route: any AppRoute, onResult: @escaping (Result) -> Void) -> RouteOpening {
        let opening = open(route)
        if opening == .opened {
            results[RouteKey(route)] = { answer in
                if let answer = answer as? Result { onResult(answer) }
            }
        }
        return opening
    }

    /// The answer of the screen of `route`; runs its opener's callback once.
    func deliver(_ result: Any, from route: any AppRoute) {
        results.removeValue(forKey: RouteKey(route))?(result)
    }

    /// The request waiting for `tab`'s root, handed out once.
    func consumeRequest(of tab: ShellTab) -> TabRequest? {
        requests.removeValue(forKey: tab)
    }

    // MARK: Gate

    /// The session gate (`ShellGate`): routes wait while the session is loading, signed out or in the first-run
    /// flow. Leaving the tabs drops the stacks, the sheet, the requests and the callbacks, so the next session
    /// starts on home.
    func sessionChanged(_ session: SessionState, onboarding: OnboardingStatus) {
        let wasReady = isReady
        self.session = session
        self.onboarding = onboarding
        if wasReady && !isReady {
            paths = [:]
            sheet = nil
            requests = [:]
            results = [:]
            selectedTab = .launch
        }
        drain()
    }

    /// The tab bar is on screen; until then no tab can be selected.
    func shellMounted(_ mounted: Bool) {
        isShellMounted = mounted
        drain()
    }

    private var isReady: Bool {
        ShellGate.shared.ready(session: session, onboarding: onboarding)
    }

    private func drain() {
        let route = queue.take(ready: isReady) { [self] tab in
            KotlinBoolean(bool: select(ShellTab(tab)))
        }
        if let route { run(route) }
    }

    private func select(_ tab: ShellTab) -> Bool {
        guard isShellMounted, tab.isAvailable else { return false }
        selectedTab = tab
        return true
    }

    /// Runs on the selected tab: a route that only names its tab opens it as it is, any other starts from the root.
    private func run(_ route: EntryRoute) {
        guard route.overlay != nil || route.request != nil || route.alert != nil else { return }
        sheet = nil
        paths[selectedTab] = []
        if let overlay = route.overlay { present(overlay) }
        if let request = route.request { receive(request) }
        if let alert = route.alert { present(alert) }
    }

    private func receive(_ request: TabRequest) {
        if request is TabRequestScheduleToday {
            todayRequest += 1
        } else {
            requests[ShellTab(request.tab)] = request
        }
    }

    @discardableResult
    private func present(_ route: any AppRoute) -> RouteOpening {
        switch Routes.target(for: route) {
        case let .tab(tab):
            return select(tab) ? .opened : .ignored
        case .compose, .swiftUI:
            sheet = nil
            paths[selectedTab, default: []].append(ShellDestination(route))
            return .opened
        case let .sheet(next):
            guard sheet == nil else { return .ignored }
            sheet = next
            return .opened
        case .gate:
            return .ignored
        case .notOnIOS:
            return .notOnIOS
        }
    }
}
