import XCTest

/// The sign-in screen and the first-run flow on the shared session (IO-07b): five taps on the logo open the demo
/// with zero network calls, `Войти через ITMO.ID` opens the ITMO.ID page above the screen and its close button
/// returns, and the flow walks every step in Android's order, answering iOS's notification dialog.
final class OnboardingUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testFiveTapsOnTheLogoOpenTheDemo() {
        let app = XCUIApplication.itmoSignedOut()
        app.launch()
        let title = element(app, "auth.title")
        XCTAssertTrue(title.waitForExistence(timeout: rootTimeout))
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        attachScreenshot(named: "sign-in")

        // The logo is decorative for VoiceOver, so it is tapped by its place: 88 pt, 24 pt above the title.
        let logo = title.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0)).withOffset(CGVector(dx: 0, dy: -68))
        for _ in 0..<5 {
            logo.tap()
        }

        XCTAssertTrue(element(app, "kit.demoBanner").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "auth.screen").exists)
        attachScreenshot(named: "demo-home")
    }

    func testItmoIdPageOpensAboveTheScreenAndCloses() {
        let app = XCUIApplication.itmoSignedOut()
        app.launch()
        XCTAssertTrue(element(app, "auth.signInItmoId").waitForExistence(timeout: rootTimeout))

        element(app, "auth.signInItmoId").tap()

        XCTAssertTrue(element(app, "auth.signIn").waitForExistence(timeout: stepTimeout))
        element(app, "auth.signIn.close").tap()
        XCTAssertTrue(element(app, "auth.signIn").waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "auth.screen").exists)
    }

    func testFirstRunFlowInOrderWithTheNotificationPrompt() {
        let app = XCUIApplication.itmoOnboarding()
        app.launch()
        XCTAssertTrue(element(app, "onboarding.screen").waitForExistence(timeout: rootTimeout))
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        let back = element(app, "onboarding.back")
        XCTAssertFalse(back.exists && back.isEnabled, "the first step has nowhere to go back to")

        let next = element(app, "onboarding.next")
        for step in ["compact_widget", "full_widget", "qr_widget", "services"] {
            XCTAssertTrue(element(app, "onboarding.step.\(step)").waitForExistence(timeout: stepTimeout), step)
            attachScreenshot(named: step.replacingOccurrences(of: "_", with: "-"))
            next.tap()
        }

        XCTAssertTrue(element(app, "onboarding.step.notifications").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "onboarding.skip").exists, "the last step has only Done")
        let status = element(app, "onboarding.notifications.status")
        if status.label != Self.allowedStatus {
            element(app, "onboarding.notifications.button").tap()
            let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").alerts.buttons["Allow"]
            XCTAssertTrue(allow.waitForExistence(timeout: stepTimeout), "iOS did not ask")
            allow.tap()
        }
        let allowed = NSPredicate(format: "label == %@", Self.allowedStatus)
        expectation(for: allowed, evaluatedWith: status)
        waitForExpectations(timeout: stepTimeout)
        attachScreenshot(named: "notifications")

        next.tap()

        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "onboarding.screen").exists)
    }

    /// `onboarding_notifications_on`: the app shows only the Russian catalog.
    private static let allowedStatus = "Разрешены"

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
