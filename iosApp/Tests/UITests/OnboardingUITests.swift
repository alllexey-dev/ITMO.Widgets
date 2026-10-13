import XCTest

/// The sign-in screen on the shared session: five taps on the logo open the demo with zero network calls, and
/// `Войти через ITMO.ID` opens the ITMO.ID page above the screen and its close button returns. The first-run flow is
/// `FirstRunUITests`.
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

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
