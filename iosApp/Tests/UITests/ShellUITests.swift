import XCTest

/// The shell on fixtures (IO-06b): tabs in Android's order, `itmowidgets://route/<id>` URLs, the session gate, the
/// demo banner, and the edge swipe back from a screen that hides the navigation bar (the Compose QR pass, IO-21).
final class ShellUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testTabsFollowAndroidOrderWithoutRecordbook() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        let buttons = app.tabBars.firstMatch.buttons
        XCTAssertEqual(buttons.count, 4)
        for (index, root) in ["schedule", "home", "sport", "me"].enumerated() {
            buttons.element(boundBy: index).tap()
            XCTAssertTrue(element(app, "shell.root.\(root)").waitForExistence(timeout: stepTimeout), root)
        }
        XCTAssertFalse(element(app, "shell.root.recordbook").exists)
        attachScreenshot(named: "me")
    }

    func testQrPassUrlOpensThePassOverHome() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        app.tabBars.firstMatch.buttons.element(boundBy: 2).tap()
        XCTAssertTrue(element(app, "shell.root.sport").waitForExistence(timeout: stepTimeout))

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/qr_pass")))

        XCTAssertTrue(element(app, "qr.pass").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "qr-pass")
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: stepTimeout))
    }

    func testTodayUrlOpensScheduleOnToday() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/today")))

        let schedule = element(app, "shell.root.schedule")
        XCTAssertTrue(schedule.waitForExistence(timeout: stepTimeout))
        XCTAssertEqual(schedule.value as? String, "today-1")
    }

    func testUnknownRouteExplainsTheLinkInASheet() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/nowhere")))

        XCTAssertTrue(element(app, "shell.sheet.linkUnavailable").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "link-unavailable")
        element(app, "sheet.close").tap()
        XCTAssertTrue(element(app, "shell.sheet.linkUnavailable").waitForNonExistence(timeout: stepTimeout))
    }

    func testEdgeSwipeGoesBackFromComposeChrome() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        element(app, "home.openQr").tap()
        let pass = element(app, "qr.pass")
        XCTAssertTrue(pass.waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "a compose route hides the navigation bar")

        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0.005, dy: 0.5))
        edge.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)))

        XCTAssertTrue(pass.waitForNonExistence(timeout: stepTimeout), "the edge swipe did not go back")
        XCTAssertTrue(element(app, "shell.root.home").exists)
    }

    func testRouteWaitsForSignIn() throws {
        let app = XCUIApplication.itmo(session: .signedOut)
        app.launch()
        XCTAssertTrue(element(app, "shell.gate.signedOut").waitForExistence(timeout: rootTimeout))
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        attachScreenshot(named: "signed-out")

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/qr_pass")))
        XCTAssertTrue(element(app, "shell.gate.signedOut").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "qr.pass").exists)

        element(app, "gate.signIn").tap()

        XCTAssertTrue(element(app, "qr.pass").waitForExistence(timeout: stepTimeout))
    }

    func testDemoSessionShowsTheBannerAboveTheTabBar() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        let banner = element(app, "kit.demoBanner")
        XCTAssertTrue(banner.waitForExistence(timeout: rootTimeout))
        XCTAssertLessThanOrEqual(banner.frame.maxY, app.tabBars.firstMatch.frame.maxY)
        attachScreenshot(named: "demo")

        element(app, "kit.demoBanner.signIn").tap()

        XCTAssertTrue(element(app, "shell.gate.signedOut").waitForExistence(timeout: stepTimeout))
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
