import AppIntents

/// The QR pass shortcut (`shortcut_qr_short`) in Siri, Spotlight and the Shortcuts app: the pass above home, as
/// Android's `qr_pass` shortcut.
struct OpenQrPassIntent: AppIntent {
    static let title: LocalizedStringResource = "shortcut_qr_long"
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        IntentRoute.qrPass.open()
        return .result()
    }
}

/// The today shortcut (`shortcut_today_short`) in Siri, Spotlight and the Shortcuts app: the schedule root on today,
/// as Android's `today` shortcut.
struct OpenTodayIntent: AppIntent {
    static let title: LocalizedStringResource = "shortcut_today_long"
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        IntentRoute.today.open()
        return .result()
    }
}

/// The app's App Shortcuts, ready without setup. The phrases are ASCII keys of `AppShortcuts.xcstrings`, whose
/// Russian texts come from `strings_ios_shortcuts.xml` (`apple-tables.properties`); the short titles and symbols
/// are Android's short labels and the quick actions' symbols.
struct ITMOWidgetsShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenQrPassIntent(),
            phrases: [
                "Open the QR pass in \(.applicationName)",
                "Show the pass of \(.applicationName)",
            ],
            shortTitle: "shortcut_qr_short",
            systemImageName: "qrcode"
        )
        AppShortcut(
            intent: OpenTodayIntent(),
            phrases: [
                "Today in \(.applicationName)",
                "Lessons today in \(.applicationName)",
            ],
            shortTitle: "shortcut_today_short",
            systemImageName: "calendar"
        )
    }
}
