import XCTest

/// The first-run flow on the shared session walks every step in Android's order and asks iOS for notifications.
/// The dialog comes only while the permission is not determined, so this class must be the first to ask in a run:
/// test classes run in alphabetical order, and it sorts before `NotificationTapUITests` (`NotificationDialog`).
final class FirstRunUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
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
        XCTAssertEqual(status.label, Self.offStatus, "answered before: on a fresh install this test asks first")
        element(app, "onboarding.notifications.button").tap()
        let allow = NotificationDialog.allow
        XCTAssertTrue(allow.waitForExistence(timeout: stepTimeout), "iOS did not ask")
        allow.tap()
        expectation(for: NSPredicate(format: "label == %@", Self.allowedStatus), evaluatedWith: status)
        waitForExpectations(timeout: stepTimeout)
        attachScreenshot(named: "notifications")

        next.tap()

        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "onboarding.screen").exists)
    }

    /// `onboarding_notifications_off` and `onboarding_notifications_on`: the app shows only the Russian catalog.
    private static let offStatus = "Выключены"
    private static let allowedStatus = "Разрешены"

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
