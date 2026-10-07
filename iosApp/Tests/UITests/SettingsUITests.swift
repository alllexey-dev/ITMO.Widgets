import XCTest

/// Settings and the error journal on the shared demo session (IO-08a): opened from the me tab, a sub-page pushed by
/// its row, a switch that keeps its value across a relaunch, and the journal from the maintenance page. The rows carry
/// `settings.row.<SettingRowId.key>`, a page's form `settings.page.<page>`; texts are Russian on an English iPhone.
final class SettingsUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testRootOpensFromMeWithoutTheRecordbookPage() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openSettings(app)

        XCTAssertTrue(element(app, "settings.row.page_services").exists)
        XCTAssertTrue(element(app, "settings.row.notifications").exists)
        XCTAssertFalse(element(app, "settings.row.page_recordbook").exists, "mark tracking is not on iOS yet")
        XCTAssertTrue(app.navigationBars.firstMatch.exists, "a SwiftUI screen uses the stack's navigation bar")
        attachScreenshot(named: "root")
    }

    func testASwitchKeepsItsValueAcrossARelaunch() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openPage(app, "schedule")
        let toggle = settingsSwitch(app, "schedule_sport_auto_sign")
        let before = isOn(toggle)

        flip(toggle)
        XCTAssertTrue(waitForValue(of: toggle, on: !before), "the switch follows the stored value")
        attachScreenshot(named: "schedule")

        app.terminate()
        app.launch()
        openPage(app, "schedule")
        let relaunched = settingsSwitch(app, "schedule_sport_auto_sign")
        XCTAssertEqual(isOn(relaunched), !before, "the stored value survives the relaunch")

        // Leave the simulator as it was for the next run.
        flip(relaunched)
        XCTAssertTrue(waitForValue(of: relaunched, on: before))
    }

    func testQrWidgetPageListsOnlyWhatIosOffers() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openPage(app, "qr_widget")

        XCTAssertTrue(element(app, "settings.row.qr_dynamic_colors").exists)
        XCTAssertTrue(element(app, "settings.row.qr_spoiler").exists)
        XCTAssertFalse(element(app, "settings.row.qr_tile").exists)
        XCTAssertFalse(element(app, "settings.row.qr_animation").exists)
        XCTAssertFalse(element(app, "settings.row.qr_custom_image").exists)
    }

    func testDiagnosticsOpenFromMaintenanceAndGoBack() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openPage(app, "maintenance")
        XCTAssertTrue(element(app, "settings.row.app_version").exists)

        element(app, "settings.row.diagnostics").tap()

        XCTAssertTrue(element(app, "diagnostics.screen").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "diagnostics")
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(element(app, "settings.page.maintenance").waitForExistence(timeout: stepTimeout))
    }

    func testRootAtAccessibilityTextSize() {
        let app = XCUIApplication.itmo(
            session: .demo,
            arguments: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        )
        app.launch()
        openSettings(app)
        attachScreenshot(named: "root-ax1")

        element(app, "settings.page.root").swipeUp()
        let maintenance = element(app, "settings.row.page_maintenance")
        XCTAssertTrue(maintenance.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(maintenance.isHittable, "the last row is reachable at AX1")
        attachScreenshot(named: "root-ax1-end")
    }

    // MARK: Helpers

    private func openSettings(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.tabBars.firstMatch.buttons.element(boundBy: 3).tap()
        // The settings row of the Compose Me tab (`MeTestTags.SETTINGS_ROW`, IO-09e).
        let entry = element(app, "settings_row")
        XCTAssertTrue(entry.waitForExistence(timeout: stepTimeout))
        entry.tap()
        XCTAssertTrue(element(app, "settings.page.root").waitForExistence(timeout: stepTimeout))
    }

    /// Opens the root, then the page whose navigation row is `page_<page>`.
    private func openPage(_ app: XCUIApplication, _ page: String) {
        openSettings(app)
        let row = element(app, "settings.row.page_\(page)")
        if !row.isHittable { element(app, "settings.page.root").swipeUp() }
        row.tap()
        XCTAssertTrue(element(app, "settings.page.\(page)").waitForExistence(timeout: stepTimeout))
    }

    private func settingsSwitch(_ app: XCUIApplication, _ key: String) -> XCUIElement {
        let toggle = app.switches["settings.row.\(key)"].firstMatch
        XCTAssertTrue(toggle.waitForExistence(timeout: stepTimeout))
        return toggle
    }

    /// Taps the switch at the trailing edge: in a list only the switch flips, not the label.
    private func flip(_ toggle: XCUIElement) {
        toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
    }

    private func isOn(_ toggle: XCUIElement) -> Bool {
        (toggle.value as? String) == "1"
    }

    private func waitForValue(of toggle: XCUIElement, on: Bool) -> Bool {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "value == %@", on ? "1" : "0"),
            object: toggle
        )
        return XCTWaiter().wait(for: [expectation], timeout: stepTimeout) == .completed
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
