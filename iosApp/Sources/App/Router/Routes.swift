import Shared
import SwiftUI

/// How iOS shows one key of the shared route model (`AppRoutes`, SH-1a2).
enum RouteTarget {
    /// A tab root: opening it selects the tab.
    case tab(ShellTab)
    /// A surface of the session gate (sign-in, first run): the session chooses it, no route opens it.
    case gate
    /// A Compose screen from its `screens/<Feature>Screens.kt` factory, pushed on the selected tab's stack; it draws
    /// its own top bar (`ShellChrome.compose`).
    case compose(@MainActor () -> AnyView)
    /// A SwiftUI screen pushed on the selected tab's stack, under the stack's navigation bar (`ShellChrome.native`).
    case swiftUI(@MainActor () -> AnyView)
    /// A sheet above the shell.
    case sheet(ShellSheet)
    /// A Compose sheet's content from its `screens/<Feature>Screens.kt` factory in a SwiftUI sheet above the shell
    /// (`ShellSheet.route`); the content draws its own title and close button, the SwiftUI view sets the detents.
    case composeSheet(@MainActor () -> AnyView)
    /// No iOS screen: a feature whose IO card has not mapped it yet, or never (the debug tools). Such a route has
    /// no entry point on iOS (App Review 2.1) and opening it does nothing.
    case notOnIOS

    /// The chrome of a pushed screen; `nil` for anything else.
    var chrome: ShellChrome? {
        switch self {
        case .compose: .compose
        case .swiftUI: .native
        case .tab, .gate, .sheet, .composeSheet, .notOnIOS: nil
        }
    }
}

/// The map from shared keys to iOS screens. The one exhaustive switch over `RouteFeature` delegates each key to its
/// feature's `Routes+<Feature>.swift`; a feature card changes only its own file. A key no feature claims fails
/// `RouterTests.testEveryRegisteredRouteHasAFeature`, a new feature breaks the build until it has a file.
enum Routes {
    static func target(for route: any AppRoute) -> RouteTarget {
        guard let feature = IosRoutes.shared.feature(route: route) else { return .notOnIOS }
        switch feature {
        case .shell: return shell(route)
        case .account: return account(route)
        case .home: return home(route)
        case .qr: return qr(route)
        case .schedule: return schedule(route)
        case .sport: return sport(route)
        case .social: return social(route)
        case .settings: return settings(route)
        case .recordbook: return recordbook(route)
        case .reviews: return reviews(route)
        case .resources: return resources(route)
        case .calendar: return calendar(route)
        case .debug: return debug(route)
        }
    }

    /// The screen of a pushed destination; a key that has none renders nothing.
    @MainActor
    @ViewBuilder
    static func view(for destination: ShellDestination) -> some View {
        switch target(for: destination.route) {
        case let .compose(make), let .swiftUI(make): make()
        case .tab, .gate, .sheet, .composeSheet, .notOnIOS: EmptyView()
        }
    }

    /// The content of a route sheet (`ShellSheet.route`); a key that is no sheet renders nothing.
    @MainActor
    @ViewBuilder
    static func sheetView(for destination: ShellDestination) -> some View {
        if case let .composeSheet(make) = target(for: destination.route) {
            make()
        }
    }

    /// The tab a tab-root key names, if the shell shows that tab.
    static func tab(of route: any AppRoute) -> ShellTab? {
        guard let root = route as? AppRoutes.TabRoot else { return nil }
        let tab = ShellTab(root.tab)
        return tab.isAvailable ? tab : nil
    }
}
