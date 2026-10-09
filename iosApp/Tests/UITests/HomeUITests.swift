import XCTest

/// The home tab on the shared demo session (IO-09a): LH-2's Compose feed as the root of the home stack, its hints
/// (the widget hint's instruction sheet, a closed hint that stays closed across a relaunch), the new-marks card
/// (IO-09d3), the QR and My ITMO buttons, and the feed at the accessibility text size AX1. Compose maps `testTag` to the accessibility identifier
/// (`HomeTestTags`, `HomeCardTestTags`); the texts are Russian on an English iPhone. A launch with
/// `-itmoForgetHomeHints` starts with every hint the simulator's state shows: no widget is placed there, so the widget
/// hint always shows.
final class HomeUITests: XCTestCase {
    private static let forgetHints = "-itmoForgetHomeHints"

    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testDemoFeedShowsTheHintsAndBothButtons() {
        let app = XCUIApplication.itmo(arguments: [Self.forgetHints])
        app.launch()
        waitForFeed(app)

        XCTAssertTrue(scrollTo(element(app, "home_card_hint_widgets"), in: app))
        XCTAssertTrue(element(app, "home_qr_fab").isHittable)
        XCTAssertTrue(element(app, "home_web_fab").isHittable)
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the feed has no top bar, as on Android")
        attachScreenshot(named: "demo-feed")
    }

    /// The new-marks card (IO-09d3): the demo's two unread subjects, and a tap opens the recordbook tab.
    func testDemoFeedShowsTheNewMarksCardThatOpensTheRecordbook() {
        let app = XCUIApplication.itmo()
        app.launch()
        waitForFeed(app)
        let marks = element(app, "home_card_marks")
        XCTAssertTrue(scrollTo(marks, in: app), "the demo has unread marks")
        reveal(marks, in: app)
        // Compose merges the card into one element whose label is its description, title first.
        XCTAssertTrue(
            marks.label.contains(Self.newMarksTitle) || app.staticTexts[Self.newMarksTitle].exists,
            marks.label
        )
        attachScreenshot(named: "marks-card")

        marks.tap()

        XCTAssertTrue(element(app, "shell.root.recordbook").waitForExistence(timeout: stepTimeout))
    }

    func testClosedHintStaysClosedAfterARelaunch() {
        let app = XCUIApplication.itmo(arguments: [Self.forgetHints])
        app.launch()
        waitForFeed(app)
        let hint = element(app, "home_card_hint_widgets")
        XCTAssertTrue(scrollTo(hint, in: app))
        reveal(element(app, "home_hint_action"), in: app)

        // The schedule cards come first since IO-09b, so the close button is the one inside the hint.
        closeButton(app, of: hint).tap()

        XCTAssertTrue(hint.waitForNonExistence(timeout: stepTimeout))
        app.terminate()
        let relaunched = XCUIApplication.itmo()
        relaunched.launch()
        waitForFeed(relaunched)
        XCTAssertTrue(element(relaunched, "home_qr_fab").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(relaunched, "home_card_hint_widgets").exists, "the closed hint came back")
        attachScreenshot(named: "hint-closed")
    }

    func testWidgetHintOpensTheInstructionSheet() {
        let app = XCUIApplication.itmo(arguments: [Self.forgetHints])
        app.launch()
        waitForFeed(app)
        let hint = element(app, "home_card_hint_widgets")
        XCTAssertTrue(scrollTo(hint, in: app))
        let action = element(app, "home_hint_action")
        reveal(action, in: app)

        action.tap()

        let sheet = element(app, "home.widgetHowTo")
        XCTAssertTrue(sheet.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "widget-howto")
        element(app, "sheet.close").tap()
        XCTAssertTrue(sheet.waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(hint.exists, "the instruction does not close the hint")
    }

    func testQrButtonOpensThePassAndBackReturnsHome() {
        let app = XCUIApplication.itmo()
        app.launch()
        waitForFeed(app)

        element(app, "home_qr_fab").tap()

        XCTAssertTrue(element(app, "qr.pass").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "qr_pass_image").waitForExistence(timeout: stepTimeout))
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "qr.pass").waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "home_qr_fab").exists)
    }

    func testMyItmoButtonSaysTheDemoCannotOpenIt() {
        let app = XCUIApplication.itmo()
        app.launch()
        waitForFeed(app)

        element(app, "home_web_fab").tap()

        XCTAssertTrue(app.staticTexts[Self.demoUnavailable].waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "web-demo")
        XCTAssertTrue(element(app, "shell.root.home").exists, "nothing opens over home")
    }

    func testFeedAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: [Self.forgetHints] + ax1)
        app.launch()
        waitForFeed(app)
        attachScreenshot(named: "demo-feed-ax1")
        // The schedule cards come first since IO-09b; the hint follows them below the fold.
        let hint = element(app, "home_card_hint_widgets")
        for _ in 0..<4 where !hint.exists {
            element(app, "home_feed").swipeUp()
        }
        XCTAssertTrue(hint.waitForExistence(timeout: stepTimeout))

        // The feed scrolls: its end comes clear of the buttons, and the buttons of the demo banner and the tab bar.
        element(app, "home_feed").swipeUp()
        element(app, "home_feed").swipeUp()
        attachScreenshot(named: "demo-feed-ax1-end")
        let qr = element(app, "home_qr_fab")
        XCTAssertTrue(qr.isHittable, "the QR button is reachable at AX1")
        XCTAssertLessThanOrEqual(qr.frame.maxY, element(app, "kit.demoBanner").frame.minY)
    }

    /// The new-marks card's title, `marks_new_title`.
    private static let newMarksTitle = "Новые оценки"

    /// The demo refusal, `error_demo_unavailable`.
    private static let demoUnavailable = "Недоступно в демо"

    /// The home root and its feed or its empty state: every source has answered from its cache.
    private func waitForFeed(_ app: XCUIApplication) {
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        let feed = element(app, "home_feed")
        let empty = element(app, "home_empty")
        let deadline = Date().addingTimeInterval(rootTimeout)
        while !feed.exists && !empty.exists && Date() < deadline {
            _ = feed.waitForExistence(timeout: 0.5)
        }
        XCTAssertTrue(feed.exists || empty.exists, "the feed never left its loading state")
    }

    /// Scrolls the feed until `target` exists: the feed is lazy, so a card below the fold (the hints under the
    /// schedule, sport and new-marks cards) has no element until it comes near the screen.
    private func scrollTo(_ target: XCUIElement, in app: XCUIApplication) -> Bool {
        for _ in 0..<6 where !target.exists {
            element(app, "home_feed").swipeUp()
        }
        return target.waitForExistence(timeout: stepTimeout)
    }

    /// Scrolls the feed until `target` can be tapped: the schedule (IO-09b) and sport (IO-09c) cards come first, so
    /// the hint can start under the tab bar. A tap during the fling only stops it, so this waits for the feed to rest.
    private func reveal(_ target: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<4 where !target.isHittable {
            element(app, "home_feed").swipeUp()
        }
        var resting = CGRect.null
        for _ in 0..<10 where target.frame != resting {
            resting = target.frame
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        }
        XCTAssertTrue(target.isHittable, "the feed never brought \(target) into reach")
    }

    /// The close button inside `card`: every closable card's button has the same tag.
    private func closeButton(_ app: XCUIApplication, of card: XCUIElement) -> XCUIElement {
        let buttons = app.descendants(matching: .any).matching(identifier: "home_card_dismiss")
        let inside = (0..<buttons.count).map { buttons.element(boundBy: $0) }.first { button in
            card.frame.contains(CGPoint(x: button.frame.midX, y: button.frame.midY))
        }
        return inside ?? buttons.firstMatch
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
