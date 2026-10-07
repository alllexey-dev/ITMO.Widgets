import Shared
import SwiftUI

/// The app entry point: the SwiftUI shell (`ShellView`) gated on the shared session (IO-07a), or in a Debug build on
/// a fixture session from the launch arguments (`ShellFixtures`). Every `itmowidgets://route/<id>` URL, App Intent,
/// quick action and notification tap (`NotificationTaps`) goes to the router; each return to the foreground refreshes
/// the push registration (`PushRefresh`, IO-13a). `init` sets the app locale, starts the Kotlin graph and the
/// background refresh (`BackgroundRefresh`), connects the router to `RouteInbox`, builds the shell's session, then
/// starts the shared one.
@main
struct ITMOWidgetsApp: App {
    @UIApplicationDelegateAdaptor(ITMOWidgetsAppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var router: AppRouter
    @State private var session: ShellSession

    init() {
        IosStrings.shared.installAppLocale()
        _ = startKoinIos(platform: AppPlatform())
        BackgroundRefresh.start()
        let router = AppRouter()
        _router = State(initialValue: router)
        RouteInbox.shared.connect { router.open(id: $0) }
        _session = State(initialValue: Self.makeSession())
        Self.startSession()
    }

    var body: some Scene {
        WindowGroup {
            ShellView(router: router, session: session)
                .onOpenURL { url in
                    router.open(url: url)
                }
                .task {
                    NotificationTaps.shared.attach { [router] route in router.open(entry: route) }
                }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await PushRefresh.run() } }
        }
        .backgroundRefresh()
    }

    /// The launch argument of a Debug build that opens the shared demo session, the way most UI tests start.
    static let demoArgument = "-itmoDemo"

    /// The launch argument of a Debug build that starts signed out, ending a stored session or demo first, for the UI
    /// tests of the sign-in screen and its five taps (IO-07b).
    static let signedOutArgument = "-itmoSignedOut"

    private static func makeSession() -> ShellSession {
        if let fixture = ShellFixtures.sessionState() {
            return ShellSession(state: fixture)
        }
        #if DEBUG
        let previewsOnboarding = CommandLine.arguments.contains(ShellSession.onboardingPreviewArgument)
        #else
        let previewsOnboarding = false
        #endif
        return ShellSession(gateway: SharedSessionGateway.fromGraph(), previewsOnboarding: previewsOnboarding)
    }

    /// Reads the stored session (KM-11h1). A Debug build launched with `demoArgument` opens the demo first unless it
    /// is already on, so the gate never shows the sign-in screen on the way; one launched with `signedOutArgument`
    /// signs out first, so it never shows the tabs. The shell and the shared screens follow `SessionRepository` and
    /// `DemoMode`.
    private static func startSession() {
        guard let repository = IosKoin.shared.get(protocol: SessionRepository.self) as? SessionRepository else {
            return
        }
        Task { @MainActor in
            #if DEBUG
            let arguments = CommandLine.arguments
            if arguments.contains(signedOutArgument) {
                try? await signOutBeforeLaunch(repository)
            } else if arguments.contains(demoArgument),
                      let demo = IosKoin.shared.get(protocol: DemoMode.self) as? DemoMode,
                      (try? await demo.isActive()) != true {
                try? await repository.startDemo()
            }
            #endif
            try? await repository.initialize()
            #if DEBUG
            BarsDebugLaunch.runIfRequested()
            BackgroundRefresh.runIfRequested()
            #endif
        }
    }

    #if DEBUG
    /// Ends a stored demo (its flag) or session (its tokens) before `initialize()` reads them; the repository is
    /// still initializing, so the gate shows only the loading state on the way.
    private static func signOutBeforeLaunch(_ repository: SessionRepository) async throws {
        if let demo = IosKoin.shared.get(protocol: DemoMode.self) as? DemoMode,
           try await demo.isActive().boolValue {
            try await repository.startDemo()
        }
        try await repository.signOut()
    }
    #endif
}
