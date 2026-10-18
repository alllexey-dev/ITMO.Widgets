import XCTest

/// The pass sheet on the shared demo session: the Compose sheet content in a SwiftUI sheet, opened by the today
/// accessory, refreshed, closed by its own close button, and at the accessibility text size AX1. Compose maps
/// `testTag` to the accessibility identifier, so the route's tags (`QrPassTestTags`) find its parts; its texts are
/// Russian on an English iPhone.
final class QrPassUITests: XCTestCase {
    /// `common_back`, the label of the Compose top bar's back button.
    static let backLabel = "Назад"
    /// `common_close`, the label of the Compose sheet header's close button.
    static let closeLabel = "Закрыть"

    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testDemoPassOpensFromTheAccessoryRefreshesAndCloses() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        element(app, "shell.passAccessory").tap()

        XCTAssertTrue(element(app, "qr.pass").waitForExistence(timeout: stepTimeout))
        let image = element(app, "qr_pass_image")
        XCTAssertTrue(image.waitForExistence(timeout: stepTimeout), "the demo pass shows its code")
        XCTAssertTrue(image.waitUntilDrawn(timeout: stepTimeout), "the demo pass code is drawn")
        attachScreenshot(named: "demo-pass")

        let refresh = element(app, "qr_pass_refresh")
        XCTAssertTrue(refresh.isHittable)
        refresh.tap()
        XCTAssertTrue(image.waitForExistence(timeout: stepTimeout), "the refreshed pass still shows its code")

        app.buttons[Self.closeLabel].tap()

        XCTAssertTrue(element(app, "qr.pass").waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.home").exists)
    }

    func testDemoPassAtAccessibilityTextSize() throws {
        let app = XCUIApplication.itmo(
            session: .demo,
            arguments: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        )
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/qr_pass")))

        XCTAssertTrue(element(app, "qr_pass_image").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "demo-pass-ax1")
        // The sheet scrolls: its end, the refresh button, comes into reach.
        element(app, "qr_pass_image").swipeUp()
        let refresh = element(app, "qr_pass_refresh")
        XCTAssertTrue(refresh.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "demo-pass-ax1-end")
        XCTAssertTrue(refresh.isHittable, "the refresh button is reachable at AX1")
        XCTAssertTrue(app.buttons[Self.closeLabel].isHittable)
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
