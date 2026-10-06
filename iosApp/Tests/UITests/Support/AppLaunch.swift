import XCTest

extension XCUIApplication {
    /// The app as every UI test launches it: English language and locale, so a test that reads a CMP or
    /// `.xcstrings` text also proves the English fallback. Later cards append their fixture arguments (the demo
    /// session) here, never per test.
    static func itmo(arguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + arguments
        return app
    }

    /// The app on a fixture session of the shell (`ShellFixtures.sessionArgument`, IO-06b).
    static func itmo(session: ShellFixtureSession, arguments: [String] = []) -> XCUIApplication {
        itmo(arguments: ["-itmoShellSession", session.rawValue] + arguments)
    }
}

/// The fixture sessions of the shell, as `ShellSessionState` spells them.
enum ShellFixtureSession: String {
    case loading
    case signedOut = "signed-out"
    case demo
    case signedIn = "signed-in"
}

extension XCTestCase {
    /// Attaches a full-screen screenshot that scripts/ios/screenshots.sh exports as `<Class>-<name>.png`.
    /// Names are letters, digits and `-`, unique within the class.
    func attachScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "\(String(describing: type(of: self)))-\(name)"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
