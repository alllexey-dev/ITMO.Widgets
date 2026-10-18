import Shared
import SwiftUI

/// The app entry point: the SwiftUI shell (`ShellView`) gated on the shared session, or in a Debug build on
/// a fixture session from the launch arguments (`ShellFixtures`). Every `itmowidgets://route/<id>` URL, App Intent,
/// quick action and notification tap (`NotificationTaps`) goes to the router; each return to the foreground refreshes
/// the push registration (`PushRefresh`). `init` sets the app locale, starts the Kotlin graph and the
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
        #if DEBUG
        if CommandLine.arguments.contains(Self.uiTestsArgument) {
            AppPlatform.removeWebsiteData = { $0() }
        }
        #endif
        _ = startKoinIos(platform: AppPlatform(), academicDate: Self.academicDate())
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

    /// The launch argument of a Debug build that every UI test passes: WebKit's removal of website data (sign-out,
    /// the demo start, the ITMO.ID sign-in page) completes at once, as unit tests replace it, since the real call
    /// hangs for minutes on a CI simulator.
    static let uiTestsArgument = "-itmoUITests"

    /// The launch argument of a Debug build that opens the shared demo session, the way most UI tests start.
    static let demoArgument = "-itmoDemo"

    /// The launch argument of a Debug build that starts signed out, ending a stored session or demo first, for the UI
    /// tests of the sign-in screen and its five taps.
    static let signedOutArgument = "-itmoSignedOut"

    /// The launch argument of a Debug build that pins the academic date, `-itmoAcademicDate 2026-10-14`, at the real
    /// wall time, as Android's debug tools do: the UI tests run on a known weekday.
    static let academicDateArgument = "itmoAcademicDate"

    /// The pinned academic date from the launch arguments; nil without one, or in a Release build.
    private static func academicDate() -> String? {
        #if DEBUG
        UserDefaults.standard.string(forKey: academicDateArgument)
        #else
        nil
        #endif
    }

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

    /// Reads the stored session. A Debug build launched with `demoArgument` opens the demo first unless it
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
