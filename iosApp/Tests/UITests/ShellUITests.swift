import XCTest

/// The shell (IO-06b): tabs in Android's order, `itmowidgets://route/<id>` URLs, the session gate and the demo banner
/// on fixtures and on the shared session (IO-07a), and the edge swipe back from a screen that hides the navigation bar
/// (the Compose QR pass, IO-21).
final class ShellUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testTabsFollowAndroidOrder() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        let buttons = app.tabBars.firstMatch.buttons
        XCTAssertEqual(buttons.count, XCUIApplication.shellTabs.count)
        for (index, root) in XCUIApplication.shellTabs.enumerated() {
            buttons.element(boundBy: index).tap()
            XCTAssertTrue(element(app, "shell.root.\(root)").waitForExistence(timeout: stepTimeout), root)
        }
        attachScreenshot(named: "me")
    }

    func testQrPassUrlOpensThePassOverHome() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        app.selectTab("sport")
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

        XCTAssertTrue(element(app, "shell.root.schedule").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, ScheduleUITests.todayTag).waitForExistence(timeout: stepTimeout))
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

        element(app, "home_qr_fab").tap()
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

    /// The shared session (IO-07a): the Compose Me tab's sign-out (IO-09e) ends the demo through `SessionRepository`
    /// after the route's confirmation, and the gate shows the sign-in screen instead of the tabs (IO-07b).
    func testSignOutOfTheSharedDemoOpensTheSignInPage() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "kit.demoBanner").waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))

        let signOut = element(app, "sign_out_row")
        if !signOut.waitForExistence(timeout: stepTimeout) || !signOut.isHittable { element(app, "main").swipeUp() }
        signOut.tap()
        // `me_sign_out` on the dialog's confirm button, the row's text too: the button is the one without the row's tag.
        let confirm = app.buttons
            .matching(NSPredicate(format: "label == %@ AND identifier != %@", Self.signOutLabel, "sign_out_row"))
            .firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "sign-out-confirm")
        confirm.tap()

        XCTAssertTrue(element(app, "auth.screen").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(app.tabBars.firstMatch.exists)
        XCTAssertFalse(element(app, "kit.demoBanner").exists)
    }

    /// `me_sign_out`: the Compose texts are Russian on an English iPhone.
    private static let signOutLabel = "Выйти"

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
