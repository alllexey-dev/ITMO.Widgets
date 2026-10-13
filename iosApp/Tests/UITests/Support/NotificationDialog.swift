import XCTest

/// iOS's notification permission dialog, which SpringBoard draws above the app in the simulator's language (English).
///
/// SpringBoard's notification server takes in the installed apps two minutes after SpringBoard starts; an app installed
/// before that never gets the dialog nor its scheduled notifications. `scripts/ios/test.sh ui` waits for that load
/// before it installs the app; run from Xcode, wait two minutes after the simulator boots.
///
/// iOS has no way to reset the answer but deleting the app (`XCUIProtectedResource` and `simctl privacy` have no
/// notifications), so it holds for the rest of the run: `test.sh ui` deletes the app before each run, and
/// `FirstRunUITests`, which asserts the dialog, sorts before `NotificationTapUITests`, which only answers it.
enum NotificationDialog {
    static let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
    static var allow: XCUIElement { springboard.alerts.buttons["Allow"] }
}
