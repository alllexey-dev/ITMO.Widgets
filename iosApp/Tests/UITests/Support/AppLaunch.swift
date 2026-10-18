import XCTest

extension XCUIApplication {
    /// The academic date of every UI test (`-itmoAcademicDate`): this week's Wednesday, Moscow time, so the demo's day
    /// has lessons whatever day the test runs on.
    static let academicDate = dayOfThisWeek(3)

    /// The date of the ISO `weekday` (1 is Monday) in this week, Moscow time, as the app's row ids spell it.
    static func dayOfThisWeek(_ weekday: Int) -> String {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "Europe/Moscow") ?? .current
        let monday = calendar.dateInterval(of: .weekOfYear, for: Date())?.start ?? Date()
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: calendar.date(byAdding: .day, value: weekday - 1, to: monday) ?? monday)
    }

    /// The app as every UI test launches it: English language and locale, so a test that reads a CMP or
    /// `.xcstrings` text also proves the English fallback, `-itmoUITests` (WebKit's website data removal completes
    /// at once, it hangs on a CI simulator), the pinned `academicDate`, and the shared demo session (`-itmoDemo`), so
    /// the Compose screens show fictional data and send nothing. Fixture arguments are added here, never per test.
    static func itmo(arguments: [String] = [], academicDate: String = academicDate) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = baseArguments(academicDate) + ["-itmoDemo"] + arguments
        return app
    }

    /// The app on the shared session, signed out first (`-itmoSignedOut`): the sign-in screen, never the demo.
    static func itmoSignedOut() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = baseArguments(academicDate) + ["-itmoSignedOut"]
        return app
    }

    private static func baseArguments(_ academicDate: String) -> [String] {
        ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-itmoUITests", "-itmoAcademicDate", academicDate]
    }

    /// The shared demo session with the first-run flow over it until the flow ends (`-itmoOnboarding`).
    static func itmoOnboarding() -> XCUIApplication {
        itmo(arguments: ["-itmoOnboarding"])
    }

    /// The app on a fixture session of the shell (`ShellFixtures.sessionArgument`).
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
