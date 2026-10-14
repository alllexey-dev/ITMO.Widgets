import XCTest

/// A tapped notification routes by its `userInfo`: the Debug fixture (`-itmoNotificationFixture`)
/// posts a local notification in the demo session, shaped like Backend's push or through the app's own notifier, the
/// test taps its banner and the shell opens what Android opens: the tab the push names, a friendship with the actor's
/// profile on the me tab, the found schedule changes, the recordbook, the BARS sign-in. The first test of the class
/// answers iOS's notification dialog unless `FirstRunUITests` already did (`NotificationDialog`).
final class NotificationTapUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10
    /// How long the banner may take to come.
    private let bannerTimeout: TimeInterval = 20

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

    /// Launches the app on home, waits for the fixture's banner and taps where it shows. The banner leaves after a
    /// few seconds, and on the CI runner a slow launch left it only moments: a tap that lands after it has gone opens
    /// nothing, so the test launches again (the app posts on every launch) instead of failing on a vanished element.
    private func tapFixture(_ fixture: String, title: String, opens root: String) {
        let app = XCUIApplication.itmo(arguments: ["-itmoNotificationFixture", fixture])
        for attempt in 1...3 {
            app.launch()
            XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
            XCTAssertFalse(element(app, root).exists)
            let banner = waitForBanner(titled: title)
            XCTAssertTrue(banner.exists, "no banner for \(fixture)")
            let frame = banner.frame
            if attempt == 1 {
                attachScreenshot(named: "\(fixture)-banner")
            }
            NotificationDialog.springboard.coordinate(withNormalizedOffset: .zero)
                .withOffset(CGVector(dx: frame.midX, dy: frame.midY))
                .tap()
            if element(app, root).waitForExistence(timeout: stepTimeout) {
                attachScreenshot(named: "\(fixture)-routed")
                return
            }
        }
        XCTFail("\(root) never opened from the banner")
    }

    /// The fixture's banner, allowing notifications first if iOS asks (the first test of a run on its own).
    private func waitForBanner(titled title: String) -> XCUIElement {
        let allow = NotificationDialog.allow
        let banner = notifications(titled: title)
        let deadline = Date().addingTimeInterval(bannerTimeout)
        while Date() < deadline && !banner.waitForExistence(timeout: 1) {
            if allow.exists {
                allow.tap()
            }
        }
        return banner
    }

    private func notifications(titled title: String) -> XCUIElement {
        NotificationDialog.springboard.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", title))
            .firstMatch
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
