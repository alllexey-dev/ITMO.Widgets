import XCTest

/// The shell: tabs in the bar's order, the pass accessory on today only, `itmowidgets://route/<id>` URLs, the session
/// gate and the demo banner on fixtures and on the shared session, and the edge swipe back from a screen that hides
/// the navigation bar (the Compose people search).
final class ShellUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testTabsFollowTheBarOrder() {
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

    func testPassAccessoryShowsOnTodayOnly() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        XCTAssertTrue(element(app, "shell.passAccessory").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.bell").exists, "the bell sits in the today header")
        attachScreenshot(named: "today")

        app.selectTab("schedule")
        XCTAssertTrue(element(app, "shell.root.schedule").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.passAccessory").waitForNonExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "shell.bell").exists)
    }

    func testQrPassUrlOpensThePassSheetOverToday() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        app.selectTab("sport")
        XCTAssertTrue(element(app, "shell.root.sport").waitForExistence(timeout: stepTimeout))

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/qr_pass")))

        XCTAssertTrue(element(app, "qr.pass").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "qr-pass")
        app.buttons[QrPassUITests.closeLabel].tap()
        XCTAssertTrue(element(app, "qr.pass").waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.home").exists)
    }

    func testTodayUrlOpensScheduleOnToday() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/today")))

        XCTAssertTrue(element(app, "shell.root.schedule").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "schedule.row.head-\(ScheduleUITests.today)").waitForExistence(timeout: stepTimeout))
    }

    /// On a free Sunday the feed has no row of its own for today: the link lands on the weekend's one row, which
    /// stands for Saturday and Sunday (`ScheduleFeedBody.Days.indexOf`), not on Monday.
    func testTodayUrlOnASundayOpensScheduleOnTheWeekend() throws {
        let app = XCUIApplication.itmo(academicDate: XCUIApplication.dayOfThisWeek(7))
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/today")))

        XCTAssertTrue(element(app, "shell.root.schedule").waitForExistence(timeout: stepTimeout))
        let weekend = element(app, "schedule.row.weekend-\(XCUIApplication.dayOfThisWeek(6))")
        XCTAssertTrue(weekend.waitForExistence(timeout: stepTimeout))
        let onScreen = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hittable == true"), object: weekend)
        XCTAssertEqual(XCTWaiter.wait(for: [onScreen], timeout: stepTimeout), .completed, "the feed went to the weekend")
        XCTAssertTrue(weekend.waitUntilDrawn(timeout: stepTimeout), "the weekend row is drawn")
        attachScreenshot(named: "today-sunday")
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

    func testEdgeSwipeGoesBackFromAPushedScreen() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        // A friend's sport, three screens deep on the profile stack: Друзья, Иван's card, his sport.
        app.selectTab("me")
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
        element(app, "friends_row").tap()
        let friend = element(app, "user_list_row_999002")
        XCTAssertTrue(friend.waitForExistence(timeout: stepTimeout))
        friend.tap()
        let sportRow = element(app, "user_profile_sport")
        XCTAssertTrue(sportRow.waitForExistence(timeout: stepTimeout))
        if !sportRow.isShown { element(app, "user_profile_list").swipeUp() }
        XCTAssertTrue(sportRow.isShown, "the sport row is drawn")
        sportRow.tap()
        let sport = element(app, "sport.user")
        XCTAssertTrue(sport.waitForExistence(timeout: stepTimeout))

        let edge = app.coordinate(withNormalizedOffset: CGVector(dx: 0.005, dy: 0.5))
        edge.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)))

        XCTAssertTrue(sport.waitForNonExistence(timeout: stepTimeout), "the edge swipe did not go back")
        XCTAssertTrue(element(app, "social.profile").exists)
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

    /// The shared session: the Compose Me tab's sign-out ends the demo through `SessionRepository`
    /// after the route's confirmation, and the gate shows the sign-in screen instead of the tabs.
    func testSignOutOfTheSharedDemoOpensTheSignInPage() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "kit.demoBanner").waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))

        let signOut = element(app, "sign_out_row")
        if !signOut.waitForExistence(timeout: stepTimeout) || !signOut.isShown { element(app, "main").swipeUp() }
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
