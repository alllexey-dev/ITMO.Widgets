import XCTest

/// The QR pass on the shared demo session (IO-21): LH-3's Compose route in the SwiftUI shell, opened from home,
/// refreshed, left by its own back button, and at the accessibility text size AX1. Compose maps `testTag` to the
/// accessibility identifier, so the route's tags (`QrPassTestTags`) find its parts; its texts are Russian on an
/// English iPhone.
final class QrPassUITests: XCTestCase {
    /// `common_back`, the label of the Compose top bar's back button.
    static let backLabel = "Назад"

    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testDemoPassOpensFromHomeRefreshesAndGoesBack() {
        let app = XCUIApplication.itmo(session: .demo)
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))

        element(app, "home_qr_fab").tap()

        XCTAssertTrue(element(app, "qr.pass").waitForExistence(timeout: stepTimeout))
        let image = element(app, "qr_pass_image")
        XCTAssertTrue(image.waitForExistence(timeout: stepTimeout), "the demo pass shows its code")
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the Compose route draws its own top bar")
        attachScreenshot(named: "demo-pass")

        let refresh = element(app, "qr_pass_refresh")
        XCTAssertTrue(refresh.isHittable)
        refresh.tap()
        XCTAssertTrue(image.waitForExistence(timeout: stepTimeout), "the refreshed pass still shows its code")

        app.buttons[Self.backLabel].tap()

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
        // The pass scrolls: its end, the refresh button, comes clear of the demo banner and the tab bar.
        element(app, "qr_pass_image").swipeUp()
        let refresh = element(app, "qr_pass_refresh")
        XCTAssertTrue(refresh.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "demo-pass-ax1-end")
        XCTAssertLessThanOrEqual(refresh.frame.maxY, element(app, "kit.demoBanner").frame.minY)
        XCTAssertTrue(refresh.isHittable, "the refresh button is reachable at AX1")
        XCTAssertTrue(app.buttons[Self.backLabel].isHittable)
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
