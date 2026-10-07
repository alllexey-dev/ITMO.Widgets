import XCTest

/// The web sign-in sheet on the fixture (IO-08b, `-itmoWebLoginFixture`): the simulator has no camera, so the scan
/// button hands over the fixture's QR link and the field takes the fixture code; the fixture answers without Backend.
/// The sheet opens by itself on the tabs of a signed-in fixture session.
final class WebLoginUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testScannedCodeIsConfirmedThenApproved() {
        let app = launch()
        attachScreenshot(named: "input")

        element(app, "webLogin.scan").tap()

        XCTAssertTrue(element(app, "webLogin.browser").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "webLogin.approve").exists)
        attachScreenshot(named: "confirm")
        element(app, "webLogin.approve").tap()

        XCTAssertTrue(element(app, "webLogin.done").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "done")
        element(app, "webLogin.result.action").tap()
        XCTAssertTrue(element(app, "shell.sheet.webLogin").waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.home").exists)
    }

    func testTypedCodeIsCheckedAndCancelReturnsToTheField() {
        let app = launch()
        let field = element(app, "webLogin.code")

        // The keyboard's Go submits, as `web_login_continue` does.
        type("abcd-234\n", into: field)
        XCTAssertTrue(element(app, "webLogin.code.error").waitForExistence(timeout: stepTimeout), "7 characters")
        attachScreenshot(named: "invalid")

        type("ABCD2346\n", into: field)
        let notFound = NSPredicate(format: "label == %@", Self.notFoundText)
        expectation(for: notFound, evaluatedWith: element(app, "webLogin.code.error"))
        waitForExpectations(timeout: stepTimeout)
        XCTAssertFalse(element(app, "webLogin.browser").exists)

        type("ABCD 2345\n", into: field)
        XCTAssertTrue(element(app, "webLogin.browser").waitForExistence(timeout: stepTimeout))

        element(app, "webLogin.cancel").tap()
        XCTAssertTrue(field.waitForExistence(timeout: stepTimeout))
        XCTAssertEqual(field.value as? String, "ABCD2345", "the checked code stays in the field")
    }

    func testCloseLeavesTheSheet() {
        let app = launch()

        element(app, "webLogin.close").tap()

        XCTAssertTrue(element(app, "shell.sheet.webLogin").waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.home").exists)
    }

    /// `web_login_not_found`: the app shows only the Russian catalog.
    private static let notFoundText = "Код не найден или устарел"

    private func launch() -> XCUIApplication {
        let app = XCUIApplication.itmo(session: .signedIn, arguments: ["-itmoWebLoginFixture"])
        app.launch()
        XCTAssertTrue(element(app, "shell.sheet.webLogin").waitForExistence(timeout: rootTimeout))
        XCTAssertTrue(element(app, "webLogin.scan").waitForExistence(timeout: stepTimeout))
        return app
    }

    /// Replaces the field's text: the cursor goes to the end (a tap on the field's right edge), then every
    /// character is deleted before `text` is typed.
    private func type(_ text: String, into field: XCUIElement) {
        field.coordinate(withNormalizedOffset: CGVector(dx: 0.95, dy: 0.5)).tap()
        let length = (field.value as? String)?.count ?? 0
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: length) + text)
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
