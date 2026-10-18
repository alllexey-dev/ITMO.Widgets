import XCTest

/// Сегодня on the shared demo session: the SwiftUI screen under the today root header (the bell, the pass
/// accessory), the bell's sheet that reads everything and closes, the demo's new marks in it, and the list at the
/// accessibility text size AX1. The texts are Russian on an English iPhone.
final class TodayUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testTodayShowsTheDayUnderTheHeaderWithTheBellAndThePass() {
        let app = XCUIApplication.itmo()
        app.launch()
        waitForToday(app)

        XCTAssertTrue(element(app, "kit.rootHeader").exists, "the today root header sits over the list")
        XCTAssertTrue(element(app, "shell.bell").isHittable)
        XCTAssertTrue(element(app, "shell.passAccessory").isHittable)
        attachScreenshot(named: "today")
    }

    /// The demo's new marks are in the bell; opening it clears the badge, the close button closes it.
    func testTheBellOpensItsSheetWithTheDemosMarks() {
        let app = XCUIApplication.itmo()
        app.launch()
        waitForToday(app)

        element(app, "shell.bell").tap()

        let sheet = element(app, "bell.sheet")
        XCTAssertTrue(sheet.waitForExistence(timeout: stepTimeout))
        let mark = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'bell.row.mark:'"))
        XCTAssertTrue(mark.firstMatch.waitForExistence(timeout: stepTimeout), "the demo has new marks")
        XCTAssertTrue(element(app, "bell.settings").exists)
        attachScreenshot(named: "bell")
        element(app, "sheet.close").tap()
        XCTAssertTrue(sheet.waitForNonExistence(timeout: stepTimeout))
        // The badge follows the seen mark through the header's flow, a moment after the sheet closes.
        let read = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == ''"), object: element(app, "shell.bell"))
        XCTAssertEqual(XCTWaiter().wait(for: [read], timeout: stepTimeout), .completed, "opening the bell read everything")
    }

    func testTodayAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        waitForToday(app)
        attachScreenshot(named: "today-ax1")

        // The list scrolls: its end comes clear of the demo banner, the pass accessory and the tab bar.
        for _ in 0..<6 { app.scrollViews.firstMatch.swipeUp() }
        attachScreenshot(named: "today-ax1-end")
        XCTAssertTrue(element(app, "shell.passAccessory").isHittable, "the pass accessory is reachable at AX1")
    }

    /// The today root and its list or its empty state, once the demo's cache answered.
    private func waitForToday(_ app: XCUIApplication) {
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        let shown = NSPredicate(format: "identifier IN %@", ["today.hero", "today.day", "today.empty"])
        let day = app.descendants(matching: .any).matching(shown).firstMatch
        XCTAssertTrue(day.waitForExistence(timeout: rootTimeout), "Сегодня shows a day or says there is none")
        XCTAssertTrue(day.waitUntilDrawn(timeout: stepTimeout), "the today screen's day is drawn")
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
