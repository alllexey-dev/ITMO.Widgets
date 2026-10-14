import XCTest

/// A tapped notification routes by its `userInfo`: the Debug fixture (`-itmoNotificationFixture`)
/// posts a local notification in the demo session, shaped like Backend's push or through the app's own notifier, the
/// test taps its banner and the shell opens what Android opens: the tab the push names, a friendship with the actor's
/// profile on the me tab, the found schedule changes, the recordbook, the BARS sign-in. The first test of the class
/// answers iOS's notification dialog unless `FirstRunUITests` already did (`NotificationDialog`).
final class NotificationTapUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10
    /// How long one launch waits for its banner, and how long all launches together may take.
    private let bannerTimeout: TimeInterval = 20
    private let launchesTimeout: TimeInterval = 90

    override func setUp() {
        continueAfterFailure = false
    }

    func testASportBookingTapOpensTheSportTab() {
        tapFixture("sport", title: Self.sportTitle, opens: "shell.root.sport")
    }

    func testAFriendshipTapOpensTheActorsProfile() {
        tapFixture("friendship", title: Self.friendsTitle, opens: "social.profile")
    }

    func testAScheduleChangeTapOpensTheChanges() {
        tapFixture("schedule-changes", title: Self.scheduleChangesTitle, opens: "bell.sheet")
    }

    func testAMarksDigestTapOpensTheRecordbook() {
        tapFixture("marks", title: Self.marksTitle, opens: "shell.root.recordbook")
    }

    func testTheBarsReminderTapOpensTheBarsSignIn() {
        tapFixture("bars-login", title: Self.barsTitle, opens: "bars.login")
    }

    private func tapFixture(_ fixture: String, title: String, opens root: String) {
        let app = XCUIApplication.itmo(arguments: ["-itmoNotificationFixture", fixture])
        let banner = launchUntilBanner(app, titled: title, opens: root)
        XCTAssertTrue(banner.exists, "no banner for \(fixture)")
        // A banner leaves after a few seconds: nothing but the screenshot between finding it and the tap.
        attachScreenshot(named: "\(fixture)-banner")
        banner.tap()

        XCTAssertTrue(element(app, root).waitForExistence(timeout: stepTimeout), root)
        attachScreenshot(named: "\(fixture)-routed")
    }

    /// Launches the app on home until the fixture's banner shows, allowing notifications if iOS asks first. The app
    /// posts on every launch, so a banner that came and went while the app launched costs a relaunch.
    private func launchUntilBanner(_ app: XCUIApplication, titled title: String, opens root: String) -> XCUIElement {
        let allow = NotificationDialog.allow
        let banner = NotificationDialog.springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", title))
            .firstMatch
        let deadline = Date().addingTimeInterval(launchesTimeout)
        while Date() < deadline {
            app.launch()
            XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
            XCTAssertFalse(element(app, root).exists)
            let retry = Date().addingTimeInterval(bannerTimeout)
            while Date() < retry {
                if allow.exists {
                    allow.tap()
                }
                if banner.waitForExistence(timeout: 1) {
                    return banner
                }
            }
        }
        return banner
    }

    /// `notification_sport_success`, `notification_channel_friends`, `schedule_changes_notification_title` (one
    /// change), `marks_new_title` and `marks_bars_login_title`: the app shows only the Russian catalog.
    private static let sportTitle = "Вы записаны на спорт"
    private static let friendsTitle = "Друзья"
    private static let scheduleChangesTitle = "Расписание изменилось"
    private static let marksTitle = "Новые оценки"
    private static let barsTitle = "Войдите в БАРС"

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
