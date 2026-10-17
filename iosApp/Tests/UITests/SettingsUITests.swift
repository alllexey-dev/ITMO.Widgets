import XCTest

/// Settings and the error journal on the shared demo session: opened from the me tab, a sub-page pushed by
/// its row, a switch that keeps its value across a relaunch, and the journal from the maintenance page. The rows carry
/// `settings.row.<SettingRowId.key>`, a page's form `settings.page.<page>`; texts are Russian on an English iPhone.
final class SettingsUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testRootOpensFromMeAndLeadsToTheConnectionAndTheNotifications() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openSettings(app)

        XCTAssertTrue(app.navigationBars.firstMatch.exists, "a SwiftUI screen uses the stack's navigation bar")
        attachScreenshot(named: "root")
        element(app, "settings.row.page_notifications").tap()
        XCTAssertTrue(element(app, "notifications.screen").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "notifications.changes").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "notifications.marks").exists, "mark tracking is on iOS")
        attachScreenshot(named: "notifications")
        app.navigationBars.buttons.element(boundBy: 0).tap()

        element(app, "settings.row.page_services").tap()
        XCTAssertTrue(element(app, "connection.screen").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "connection.switch").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "connection.switch").isEnabled, "the demo's opt-in cannot change")
        attachScreenshot(named: "connection")
    }

    func testASwitchKeepsItsValueAcrossARelaunch() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openPage(app, "schedule")
        let toggle = settingsSwitch(app, "schedule_sport_auto_sign")
        let before = isOn(toggle)

        XCTAssertTrue(flip(toggle, to: !before), "the switch follows the stored value")
        attachScreenshot(named: "schedule")

        app.terminate()
        app.launch()
        openPage(app, "schedule")
        let relaunched = settingsSwitch(app, "schedule_sport_auto_sign")
        XCTAssertEqual(isOn(relaunched), !before, "the stored value survives the relaunch")

        // Leave the simulator as it was for the next run.
        XCTAssertTrue(flip(relaunched, to: before))
    }

    /// The calendar group: the switch and the `.ics` row; the demo's own schedule is synthetic, so the sheet
    /// writes a file without a request. The switch is not flipped: it would show the system's calendar prompt.
    func testTheSchedulePageOffersTheCalendarAndTheIcsFile() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openPage(app, "schedule")

        XCTAssertTrue(settingsSwitch(app, "calendar_sync").exists)
        let export = element(app, "settings.row.ics_export")
        if !export.isHittable { element(app, "settings.page.schedule").swipeUp() }
        export.tap()

        let week = element(app, "ics.range.week")
        XCTAssertTrue(week.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "ics-choose")
        week.tap()
        XCTAssertTrue(element(app, "ics.send").waitForExistence(timeout: stepTimeout), "the demo week has lessons")
        attachScreenshot(named: "ics-ready")

        element(app, "ics.close").tap()
        XCTAssertTrue(element(app, "settings.page.schedule").waitForExistence(timeout: stepTimeout))
    }

    /// The mark checks: My ITMO and the sheets; BARS joins with the account's first BARS answer.
    /// The mark sources follow `Баллы` on the notifications page while it is on.
    func testTheNotificationsPageListsTheMarkSources() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        openSettings(app)
        element(app, "settings.row.page_notifications").tap()
        XCTAssertTrue(element(app, "notifications.screen").waitForExistence(timeout: stepTimeout))

        XCTAssertTrue(element(app, "notifications.marks.my_itmo").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "notifications.marks.sheets").exists)
        attachScreenshot(named: "notifications-marks")
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

        // The form is lazy: the last row exists once it scrolls near the screen.
        let maintenance = element(app, "settings.row.page_maintenance")
        for _ in 0..<6 where !maintenance.exists || !maintenance.isHittable {
            element(app, "settings.page.root").swipeUp()
        }
        XCTAssertTrue(maintenance.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(maintenance.isHittable, "the last row is reachable at AX1")
        attachScreenshot(named: "root-ax1-end")
    }

    // MARK: Helpers

    private func openSettings(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        // The settings row of the Compose Me tab (`MeTestTags.SETTINGS_ROW`).
        let entry = element(app, "settings_row")
        XCTAssertTrue(entry.waitForExistence(timeout: stepTimeout))
        // At large text sizes the row starts under the floating tab bar, which covers the content but not its frame.
        let bar = app.tabBars.firstMatch
        for _ in 0..<4 where !entry.isHittable || entry.frame.maxY > bar.frame.minY {
            element(app, "main").swipeUp()
        }
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

    /// Taps the switch at the trailing edge (in a list only the switch flips, not the label) until it shows `on`. On
    /// the CI runner a tap synthesized on the relaunched page once left the switch unchanged for 10 s, so a tap that
    /// changed nothing within `stepTimeout` is repeated; a tap that did change it is never repeated.
    private func flip(_ toggle: XCUIElement, to on: Bool) -> Bool {
        for _ in 0..<3 {
            toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.93, dy: 0.5)).tap()
            if waitForValue(of: toggle, on: on) { return true }
        }
        return false
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
