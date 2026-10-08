import XCTest

/// A tapped notification routes by its payload type (IO-13a): the Debug fixture (`-itmoNotificationFixture`) posts a
/// local notification shaped like Backend's push in the demo session, the test taps its banner and the shell opens
/// the tab the payload names, a friendship with the actor's profile on the me tab (IO-09e). The first run answers
/// iOS's notification dialog. On a fresh simulator the notification daemon answers its first requests only after a
/// minute or two, so the dialog and the banner get a long timeout.
final class NotificationTapUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let bannerTimeout: TimeInterval = 180
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testASportBookingTapOpensTheSportTab() {
        tapFixture("sport", title: Self.sportTitle, opens: "shell.root.sport")
    }

    func testAFriendshipTapOpensTheActorsProfile() {
        tapFixture("friendship", title: Self.friendsTitle, opens: "social.profile")
    }

    private func tapFixture(_ fixture: String, title: String, opens root: String) {
        let app = XCUIApplication.itmo(arguments: ["-itmoNotificationFixture", fixture])
        app.launch()
        let banner = waitForBanner(titled: title, in: XCUIApplication(bundleIdentifier: "com.apple.springboard"))
        XCTAssertTrue(banner.exists, "no banner for \(fixture)")
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        XCTAssertFalse(element(app, root).exists)
        attachScreenshot(named: "\(fixture)-banner")
        banner.tap()

        XCTAssertTrue(element(app, root).waitForExistence(timeout: stepTimeout), root)
        attachScreenshot(named: "\(fixture)-routed")
    }

    /// The banner of the fixture, allowing notifications if iOS asks first (the dialog may come late).
    private func waitForBanner(titled title: String, in springboard: XCUIApplication) -> XCUIElement {
        let allow = springboard.alerts.buttons["Allow"]
        let banner = springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", title))
            .firstMatch
        let deadline = Date().addingTimeInterval(bannerTimeout)
        while Date() < deadline {
            if allow.exists {
                allow.tap()
            }
            if banner.waitForExistence(timeout: 1) {
                break
            }
        }
        return banner
    }

    /// `notification_sport_success` and `notification_channel_friends`: the app shows only the Russian catalog.
    private static let sportTitle = "Вы записаны на спорт"
    private static let friendsTitle = "Друзья"

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
