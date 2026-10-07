import XCTest

extension XCUIApplication {
    /// The app as every UI test launches it: English language and locale, so a test that reads a CMP or
    /// `.xcstrings` text also proves the English fallback, and the shared demo session (`-itmoDemo`, IO-21), so the
    /// Compose screens show fictional data and send nothing. Later cards append their fixture arguments here, never
    /// per test.
    static func itmo(arguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-itmoDemo"] + arguments
        return app
    }

    /// The app on the shared session, signed out first (`-itmoSignedOut`, IO-07b): the sign-in screen, never the demo.
    static func itmoSignedOut() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-itmoSignedOut"]
        return app
    }

    /// The shared demo session with the first-run flow over it until the flow ends (`-itmoOnboarding`, IO-07b).
    static func itmoOnboarding() -> XCUIApplication {
        itmo(arguments: ["-itmoOnboarding"])
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
