import XCTest

/// The sport tab and another user's sport on the shared demo session: `Моё` and `Запись` of the SwiftUI root, a
/// booking's lesson sheet with its share action on the system share sheet, the demo's refusal of a booking, the
/// more-filters sheet, `Мои секции` and `Откуда баллы` pushed on the stack, another user's sport from a profile, and
/// `Моё` at the accessibility text size AX1. The texts are Russian on an English iPhone; the ISUs are the demo set's
/// (`DemoPeople`).
final class SportUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testMineOpensABookingsSheetThatShares() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSport(app)
        XCTAssertTrue(element(app, "sport.score").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "my")

        // A later day's booking: the demo's today lesson ends in the afternoon, and an ended lesson shares nothing.
        let booking = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'sport.booking.'"))
            .matching(NSPredicate(format: "NOT (label CONTAINS %@)", Self.todayLabel)).firstMatch
        XCTAssertTrue(booking.waitForExistence(timeout: stepTimeout), "the demo has confirmed visits")
        booking.tap()
        XCTAssertTrue(element(app, "sport.details").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "sport.sheet.action").exists, "a signed booking offers its cancellation")
        attachScreenshot(named: "details")

        let share = element(app, "sport.sheet.share")
        for _ in 0..<3 where !share.isHittable { element(app, "sport.details").swipeUp() }
        share.tap()
        XCTAssertTrue(app.otherElements["ActivityListView"].waitForExistence(timeout: stepTimeout), "the system share sheet")
        attachScreenshot(named: "share")
    }

    func testSignUpRefusesABookingInTheDemoAndOpensTheFilters() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSport(app)
        app.buttons[Self.signUpTab].firstMatch.tap()
        XCTAssertTrue(element(app, "sport.search").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "sign")

        // The list's identifier (`sport.rows`) replaces its buttons' `sport.rowAction`, so the action is found by its label.
        let signUp = app.buttons.matching(NSPredicate(format: "label == %@", Self.signUpLabel)).firstMatch
        for _ in 0..<4 where !signUp.isHittable {
            element(app, "sport.list").swipeUp()
            Thread.sleep(forTimeInterval: 1)
        }
        XCTAssertTrue(signUp.isHittable, "the demo day has a lesson to book")
        signUp.tap()
        let refusal = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", Self.demoUnavailable))
        XCTAssertTrue(refusal.firstMatch.waitForExistence(timeout: stepTimeout), "the demo books nothing")
        attachScreenshot(named: "sign-demo-refusal")

        element(app, "sport.list").swipeDown()
        element(app, "sport.list").swipeDown()
        app.buttons[Self.filtersLabel].firstMatch.tap()
        XCTAssertTrue(element(app, "sport.filters").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "filters")
    }

    /// The sign-up strip's days are drawn, not only hittable, also after a tab switch back to the sport tab.
    func testSignUpWeekStripIsDrawn() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSport(app)
        app.buttons[Self.signUpTab].firstMatch.tap()
        let day = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'kit.weekDay.'"))
            .firstMatch
        XCTAssertTrue(day.waitForExistence(timeout: stepTimeout), "the sign-up strip has days")
        attachScreenshot(named: "strip")
        XCTAssertTrue(day.waitUntilDrawn(timeout: stepTimeout), "the sign-up strip is drawn")

        app.selectTab("home")
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: stepTimeout))
        openSport(app)
        XCTAssertTrue(day.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(day.waitUntilDrawn(timeout: stepTimeout), "the sign-up strip is drawn after a tab switch")
    }

    func testPointsArePushedAndTheDemoSectionIsInMine() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSport(app)
        let score = element(app, "sport.score")
        XCTAssertTrue(score.waitForExistence(timeout: stepTimeout))
        score.tap()
        XCTAssertTrue(element(app, "sport.points").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "points")
        app.navigationBars.buttons.firstMatch.tap()

        let section = element(app, "sport.section.1")
        for _ in 0..<6 where !section.isShown { element(app, "sport.list").swipeUp() }
        XCTAssertTrue(section.isShown, "Anna's volleyball is a section in the demo, drawn")
        attachScreenshot(named: "sections")
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
        let title = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", Self.ivanTitle))
        XCTAssertTrue(title.firstMatch.waitForExistence(timeout: stepTimeout), "the title names the user")
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
    }

    func testMineAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openSport(app)
        let list = element(app, "sport.list")
        XCTAssertTrue(list.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "my-ax1")

        // The page scrolls: `Секции` near its end come clear of the demo banner and the tab bar.
        let sections = element(app, "sport.sections")
        for _ in 0..<12 where !sections.isHittable { list.swipeUp() }
        attachScreenshot(named: "my-ax1-end")
        XCTAssertTrue(sections.isHittable, "the end of «Моё» is reachable at AX1")
    }

    // MARK: Helpers

    /// `DemoPeople.IVAN` (`Кузнецов Иван`), a friend with demo visits, and his sport's title, which names him by his
    /// given name (`user_sport_title`).
    private static let ivan = 999_002
    private static let ivanTitle = "Спорт: Иван"
    /// `sport_tab_sign`, `sport_filters_title`, `sport_lesson_sign_up` and `error_demo_unavailable`.
    private static let signUpTab = "Запись"
    private static let filtersLabel = "Фильтры"
    private static let signUpLabel = "Записаться"
    private static let demoUnavailable = "Недоступно в демо"
    /// The day of a booking dated today.
    private static let todayLabel = "Сегодня"

    private func openSport(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("sport")
        XCTAssertTrue(element(app, "shell.root.sport").waitForExistence(timeout: stepTimeout))
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
