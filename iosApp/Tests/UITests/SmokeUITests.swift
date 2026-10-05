import XCTest

/// Launches the app and checks the first screen (IO-17). Runs with `scripts/ios/test.sh ui SmokeUITests` and in
/// scripts/ios/screenshots.sh; the shell cards (IO-05, IO-06) replace the root assertion with the tab bar.
final class SmokeUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func testLaunchShowsTheRootScreen() {
        let app = XCUIApplication.itmo()
        app.launch()

        let productName = app.staticTexts["root.productName"]
        XCTAssertTrue(productName.waitForExistence(timeout: 30), "the root screen did not appear")
        XCTAssertEqual(productName.label, "ITMO.Widgets")
        attachScreenshot(named: "launch")
    }
}
