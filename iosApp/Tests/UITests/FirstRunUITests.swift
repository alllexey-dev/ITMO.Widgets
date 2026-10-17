import XCTest

/// The first-run flow on the shared session walks its three steps, asks iOS for notifications with the permission's
/// own button and finishes on the connection step (the demo keeps the connection on).
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
        XCTAssertTrue(element(app, "onboarding.step.value").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(next.waitUntilDrawn(timeout: stepTimeout), "the footer button is drawn")
        attachScreenshot(named: "value")
        next.tap()

        XCTAssertTrue(element(app, "onboarding.step.permissions").waitForExistence(timeout: stepTimeout))
        let ask = element(app, "onboarding.notifications.button")
        XCTAssertTrue(ask.exists, "answered before: on a fresh install this test asks first")
        XCTAssertTrue(ask.waitUntilDrawn(timeout: stepTimeout), "the permission's own button is drawn")
        ask.tap()
        let allow = NotificationDialog.allow
        XCTAssertTrue(allow.waitForExistence(timeout: stepTimeout), "iOS did not ask")
        allow.tap()
        XCTAssertTrue(element(app, "onboarding.notifications.granted").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "permissions")
        next.tap()

        XCTAssertTrue(element(app, "onboarding.step.connection").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "connection")
        // The demo session keeps the connection on, so the last step only finishes; `Не сейчас` is for the others.
        XCTAssertFalse(element(app, "onboarding.notNow").exists)
        next.tap()

        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(element(app, "onboarding.screen").exists)
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
