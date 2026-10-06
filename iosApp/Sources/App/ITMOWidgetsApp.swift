import Shared
import SwiftUI

/// The app entry point: the SwiftUI shell (`ShellView`) on the fixture session until IO-21 binds the shared one.
/// Every `itmowidgets://route/<id>` URL goes to the router.
@main
struct ITMOWidgetsApp: App {
    @State private var router = AppRouter()
    @State private var session = ShellSession(state: ShellFixtures.sessionState())

    init() {
        IosStrings.shared.installAppLocale()
    }

    var body: some Scene {
        WindowGroup {
            ShellView(router: router, session: session)
                .onOpenURL { url in
                    router.open(url: url)
                }
        }
    }
}
