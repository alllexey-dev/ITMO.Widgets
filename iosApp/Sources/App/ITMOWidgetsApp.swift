import Shared
import SwiftUI

/// The app entry point. IO-05 and IO-06 replace the root view with the shell (tab bar, router, CMP hosts).
@main
struct ITMOWidgetsApp: App {
    init() {
        IosStrings.shared.installAppLocale()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

struct RootView: View {
    var body: some View {
        Text(verbatim: IosShell.shared.productName)
            .font(.title)
            .accessibilityIdentifier("root.productName")
    }
}
