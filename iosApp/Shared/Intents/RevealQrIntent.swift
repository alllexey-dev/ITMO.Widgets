import AppIntents
import Foundation

/// The QR widget's spoiler button (`Button(intent:)`): records a reveal in the App Group (`QrWidgetReveal`) and
/// returns; WidgetKit then reloads the widget's timeline, which shows the code until the reveal ends. It runs in the
/// widget extension, without Kotlin and without the network. Hidden from Shortcuts and Spotlight.
struct RevealQrIntent: AppIntent {
    static let title: LocalizedStringResource = "ios_widget_qr_reveal"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        // An unsigned build has no container: the widget then shows the signed-out state and has nothing to reveal.
        if let container = AppGroupSnapshot.container() {
            try QrWidgetReveal.startingAt(Date()).write(toContainer: container)
        }
        return .result()
    }
}
