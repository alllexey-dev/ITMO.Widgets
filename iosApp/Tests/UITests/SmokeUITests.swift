import XCTest

/// Launches the app and checks the first screen (IO-17): the shell on home (IO-06b). Runs with
/// `scripts/ios/test.sh ui SmokeUITests` and in scripts/ios/screenshots.sh.
final class SmokeUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    func testLaunchShowsTheShellOnHome() {
        let app = XCUIApplication.itmo()
        app.launch()

        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 30), "the tab bar did not appear")
        XCTAssertTrue(app.descendants(matching: .any)["shell.root.home"].exists, "home is not selected at launch")
        attachScreenshot(named: "launch")
    }
}
