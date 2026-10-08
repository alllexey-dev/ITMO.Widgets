import XCTest

/// The sport tab and another user's sport on the shared demo session (IO-09c): LP-6's Compose `Мой спорт` and
/// `Запись` as the root of the sport stack, a booking's details in a SwiftUI sheet hosting the shared sheet content
/// with its share action on the system share sheet, the section filter dialog and the week strip's arrows, the demo's
/// refusal of a booking, another user's sport from a profile, and `Мой спорт` at the accessibility text size AX1.
/// Compose maps `testTag` to the accessibility identifier (`SportScreenTestTags`, `SportBookingCardTestTags`,
/// `SportDetailsSheetTestTags`, `SportSignFiltersTestTags`, `SportWeekStripTestTags`, `UserSportScreenTestTags`); the
/// texts are Russian on an English iPhone. The ISUs are the demo set's (`DemoPeople`).
final class SportUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testMySportOpensABookingsDetailsInASheet() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSport(app)
        XCTAssertTrue(element(app, "sport_my_list").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the tab draws its own tabs, as on Android")
        attachScreenshot(named: "my")

        let booking = element(app, "sport_booking_card")
        XCTAssertTrue(booking.waitForExistence(timeout: stepTimeout), "the demo has confirmed visits")
        booking.tap()
        XCTAssertTrue(element(app, "sport.details").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "sport_details_scroll").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "sport_details_action").exists, "a signed booking offers its cancellation")
        attachScreenshot(named: "details")

        app.buttons[Self.closeLabel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(element(app, "sport.details")), "the sheet's own close button dismisses it")
        XCTAssertTrue(element(app, "sport_my_list").exists)
    }

    func testTheDetailsShareTheLessonThroughTheShareSheet() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSport(app)
        // A later day's booking: the demo's today lesson ends in the afternoon, and an ended lesson shares nothing.
        let booking = app.descendants(matching: .any).matching(identifier: "sport_booking_card")
            .matching(NSPredicate(format: "NOT (label CONTAINS %@)", Self.todayLabel)).firstMatch
        XCTAssertTrue(booking.waitForExistence(timeout: stepTimeout))
        booking.tap()

        let share = element(app, "sport_details_share")
        XCTAssertTrue(share.waitForExistence(timeout: stepTimeout))
        share.tap()
        let sheet = app.otherElements["ActivityListView"]
        XCTAssertTrue(sheet.waitForExistence(timeout: stepTimeout), "the system share sheet over the details")
        attachScreenshot(named: "share")
    }

    func testSignPageRefusesABookingInTheDemoPicksASectionAndMovesTheWeek() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSport(app)
        element(app, "sport_tab_sign").tap()
        let list = element(app, "sport_lesson_list")
        XCTAssertTrue(list.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "sign")

        // Today's open lesson (`sport_lesson_sign_up`) below the signed one.
        let signUp = app.buttons[Self.signUpLabel].firstMatch
        for _ in 0..<3 where !signUp.isHittable {
            list.swipeUp()
            // A tap on a list still flinging only stops it.
            Thread.sleep(forTimeInterval: 1)
        }
        XCTAssertTrue(signUp.isHittable, "the demo day has a lesson to book")
        signUp.tap()
        let refusal = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", Self.demoUnavailable))
        XCTAssertTrue(refusal.firstMatch.waitForExistence(timeout: stepTimeout), "the demo books nothing")
        attachScreenshot(named: "sign-demo-refusal")
        list.swipeDown()
        list.swipeDown()

        let sport = element(app, "sport_filters_sport")
        XCTAssertTrue(sport.waitForExistence(timeout: stepTimeout))
        sport.tap()
        XCTAssertTrue(element(app, "sport_section_picker_list").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "section-picker")
        element(app, "sport_section_picker_cancel").tap()
        XCTAssertTrue(waitForAbsence(element(app, "sport_section_picker_list")))

        // The week strip moves by its arrows only (no swipe between weeks or tabs on iOS).
        let today = element(app, "sport_week_strip_month").label
        element(app, "sport_week_strip_next").tap()
        let nextWeek = element(app, "sport_week_strip_day_\(Self.isoDate(daysFromToday: 7))")
        XCTAssertTrue(nextWeek.waitForExistence(timeout: stepTimeout), "the next week's days")
        attachScreenshot(named: "sign-next-week")
        element(app, "sport_week_strip_previous").tap()
        let thisWeek = element(app, "sport_week_strip_day_\(Self.isoDate(daysFromToday: 0))")
        XCTAssertTrue(thisWeek.waitForExistence(timeout: stepTimeout))
        XCTAssertEqual(element(app, "sport_week_strip_month").label, today)
    }

    func testAnotherUsersSportOpensFromTheirProfile() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
        element(app, "friends_row").tap()
        let friend = element(app, "user_list_row_\(Self.ivan)")
        XCTAssertTrue(friend.waitForExistence(timeout: stepTimeout))
        friend.tap()

        let sport = element(app, "user_profile_sport")
        XCTAssertTrue(sport.waitForExistence(timeout: stepTimeout))
        if !sport.isHittable { element(app, "user_profile_list").swipeUp() }
        sport.tap()

        XCTAssertTrue(element(app, "sport.user").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "user_sport_list").waitForExistence(timeout: stepTimeout), "Ivan's demo visits")
        attachScreenshot(named: "user-sport")
        let title = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", Self.ivanFirstName))
        XCTAssertTrue(title.firstMatch.exists, "the title names the user")
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
    }

    func testMySportAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openSport(app)
        let list = element(app, "sport_my_list")
        XCTAssertTrue(list.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "my-ax1")

        // The page scrolls: its last card comes clear of the demo banner and the tab bar.
        for _ in 0..<12 { list.swipeUp() }
        attachScreenshot(named: "my-ax1-end")
        let cards = app.descendants(matching: .any).matching(identifier: "sport_booking_card")
        let last = cards.element(boundBy: cards.count - 1)
        XCTAssertTrue(last.isHittable, "the last booking is reachable at AX1")
        XCTAssertLessThanOrEqual(last.frame.maxY, element(app, "kit.demoBanner").frame.minY)
    }

    // MARK: Helpers

    /// `DemoPeople.IVAN`, a friend with demo visits, and the first name his sport's title shows.
    private static let ivan = 999_002
    private static let ivanFirstName = "Иван"
    /// `common_close`, `sport_lesson_sign_up` and `error_demo_unavailable`.
    private static let closeLabel = "Закрыть"
    private static let signUpLabel = "Записаться"
    private static let demoUnavailable = "Недоступно в демо"
    /// The day label of a booking card dated today.
    private static let todayLabel = "Сегодня"

    private func openSport(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("sport")
        XCTAssertTrue(element(app, "shell.root.sport").waitForExistence(timeout: stepTimeout))
    }

    /// The Moscow date `days` after today as the week strip's day tags spell it (`yyyy-MM-dd`).
    private static func isoDate(daysFromToday days: Int) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Moscow")!
        let date = calendar.date(byAdding: .day, value: days, to: Date())!
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year!, parts.month!, parts.day!)
    }

    private func waitForAbsence(_ element: XCUIElement) -> Bool {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: element)
        return XCTWaiter().wait(for: [gone], timeout: stepTimeout) == .completed
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
