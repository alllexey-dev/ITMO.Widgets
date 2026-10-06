import Shared
import SwiftUI

/// The app entry point: the SwiftUI shell (`ShellView`) on the fixture session until IO-07a gates it on the shared
/// one. Every `itmowidgets://route/<id>` URL goes to the router. `init` sets the app locale, starts the Kotlin graph,
/// then the shared session.
@main
struct ITMOWidgetsApp: App {
    @State private var router = AppRouter()
    @State private var session = ShellSession(state: ShellFixtures.sessionState())

    init() {
        IosStrings.shared.installAppLocale()
        _ = startKoinIos(platform: AppPlatform())
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

    /// Reads the stored session (KM-11h1), then, in a Debug build launched with `demoArgument`, opens the demo
    /// unless it is already open; the shared screens follow `SessionRepository` and `DemoMode`.
    private static func startSession() {
        guard let repository = IosKoin.shared.get(protocol: SessionRepository.self) as? SessionRepository else {
            return
        }
        Task { @MainActor in
            try? await repository.initialize()
            #if DEBUG
            let isDemo = (repository.state.value as? SessionStateSignedIn)?.demo == true
            if CommandLine.arguments.contains(demoArgument), !isDemo {
                try? await repository.startDemo()
            }
            #endif
        }
    }
}
