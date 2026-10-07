import Shared
import SwiftUI

/// The app entry point: the SwiftUI shell (`ShellView`) gated on the shared session (IO-07a), or in a Debug build on
/// a fixture session from the launch arguments (`ShellFixtures`). Every `itmowidgets://route/<id>` URL goes to the
/// router. `init` sets the app locale, starts the Kotlin graph, builds the shell's session, then starts the shared one.
@main
struct ITMOWidgetsApp: App {
    @State private var router = AppRouter()
    @State private var session: ShellSession

    init() {
        IosStrings.shared.installAppLocale()
        _ = startKoinIos(platform: AppPlatform())
        _session = State(initialValue: Self.makeSession())
        Self.startSession()
    }

    var body: some Scene {
        WindowGroup {
            ShellView(router: router, session: session)
                .onOpenURL { url in
                    router.open(url: url)
                }
        }
    }

    /// The launch argument of a Debug build that opens the shared demo session (UI tests, until IO-07b's five taps).
    static let demoArgument = "-itmoDemo"

    private static func makeSession() -> ShellSession {
        if let fixture = ShellFixtures.sessionState() {
            return ShellSession(state: fixture)
        }
        return ShellSession(gateway: SharedSessionGateway.fromGraph())
    }

    /// Reads the stored session (KM-11h1). A Debug build launched with `demoArgument` opens the demo first unless it
    /// is already on, so the gate never shows the sign-in page on the way; the shell and the shared screens follow
    /// `SessionRepository` and `DemoMode`.
    private static func startSession() {
        guard let repository = IosKoin.shared.get(protocol: SessionRepository.self) as? SessionRepository else {
            return
        }
        Task { @MainActor in
            #if DEBUG
            if CommandLine.arguments.contains(demoArgument),
               let demo = IosKoin.shared.get(protocol: DemoMode.self) as? DemoMode,
               (try? await demo.isActive()) != true {
                try? await repository.startDemo()
            }
            #endif
            try? await repository.initialize()
        }
    }
}
