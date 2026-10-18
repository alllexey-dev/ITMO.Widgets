import XCTest

/// Pull to refresh on SwiftUI roots of the demo session (`Спорт` `Моё` and `Сегодня`): the spinner is drawn under
/// the root header and stays while the refresh runs. `-itmoSlowRefresh` (`PullRefresh.slowArgument`) keeps it five
/// seconds over the demo's instant refresh. XCUITest lists no element for the system refresh control, so the test
/// reads the pixels under the header a few seconds into the refresh: drawn, and not the resting screen; then the
/// content comes back in place.
final class RefreshUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testSportMineShowsTheSpinnerUnderTheHeader() {
        assertPullShowsTheSpinner(tab: "sport", content: "sport.tabs")
    }

    func testTodayShowsTheSpinnerUnderTheHeader() {
        assertPullShowsTheSpinner(tab: "home", content: "today.hero")
    }

    // MARK: Helpers

    private func assertPullShowsTheSpinner(tab: String, content identifier: String) {
        let app = XCUIApplication.itmo(arguments: ["-itmoSlowRefresh"])
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab(tab)
        let root = app.descendants(matching: .any)["shell.root.\(tab)"].firstMatch
        XCTAssertTrue(root.waitForExistence(timeout: stepTimeout))
        let header = root.descendants(matching: .any)["kit.rootHeader"].firstMatch
        let content = root.descendants(matching: .any)[identifier].firstMatch
        XCTAssertTrue(content.waitForExistence(timeout: stepTimeout))
        let restingTop = content.frame.minY

        // The spinner sits centred under the header, where the content rests until the pull moves it down.
        let room = CGRect(x: app.frame.midX - 30, y: header.frame.maxY, width: 60, height: 60)
        let resting = XCUIScreen.main.screenshot()

        // The pull's press waits until the app is idle, and the spinner keeps it busy until the refresh ends: the
        // screen is captured from another thread while the press waits (element queries there would wait too).
        var shot: XCUIScreenshot?
        let sampled = expectation(description: "captured while refreshing")
        DispatchQueue.global().asyncAfter(deadline: .now() + 2.5) {
            shot = XCUIScreen.main.screenshot()
            sampled.fulfill()
        }
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
        start.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7)),
                    withVelocity: .default, thenHoldForDuration: 0.3)
        wait(for: [sampled], timeout: stepTimeout)
        guard let shot else { return XCTFail("no screenshot while refreshing") }
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = "RefreshUITests-\(tab)-refreshing"
        attachment.lifetime = .keepAlways
        add(attachment)
        XCTAssertTrue(app.isDrawn(in: room, of: shot), "the spinner is drawn under the header while the refresh runs")
        XCTAssertTrue(app.differs(in: room, between: resting, and: shot), "the screen under the header is not the resting one")

        let settled = NSPredicate { _, _ in abs(content.frame.minY - restingTop) < 2 }
        let ended = expectation(for: settled, evaluatedWith: nil)
        wait(for: [ended], timeout: stepTimeout)
        attachScreenshot(named: "\(tab)-refreshed")
    }
}
