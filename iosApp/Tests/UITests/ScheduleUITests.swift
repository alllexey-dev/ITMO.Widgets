import XCTest

/// The schedule on the shared demo session: the SwiftUI feed as the root of the schedule stack (opening on today),
/// the lesson sheet, the friend picker's one tap and the way back to the own schedule, the month sheet, a found
/// change from the bell, the today hero's lesson, the `today` route, another user's schedule from a profile, and the
/// feed and the lesson sheet at the accessibility text size AX1. The ISUs are the demo set's (`DemoPeople`).
final class ScheduleUITests: XCTestCase {
    /// Today's date as the feed's row ids carry it: the demo's days follow the academic clock, Moscow time.
    static var today: String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Moscow") ?? .current
        let day = calendar.dateComponents([.year, .month, .day], from: Date())
        return String(format: "%04d-%02d-%02d", day.year ?? 0, day.month ?? 0, day.day ?? 0)
    }

    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testDemoScheduleOpensOnTodayUnderTheRootHeader() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)

        let today = todayRow(app)
        XCTAssertTrue(today.waitForExistence(timeout: stepTimeout), "the feed opens on \(Self.today), shows \(rowIds(app))")
        XCTAssertTrue(waitUntilHittable(today))
        XCTAssertTrue(firstLesson(app).waitForExistence(timeout: stepTimeout), "the demo week has lessons")
        XCTAssertTrue(element(app, "schedule.friends").isHittable)
        XCTAssertTrue(element(app, "schedule.strip").exists)
        attachScreenshot(named: "days")

        element(app, "schedule.list").swipeUp()
        element(app, "schedule.list").swipeUp()
        attachScreenshot(named: "days-later")
    }

    /// The strip's days are drawn, not only hittable: on launch and after a tab switch back to the schedule.
    func testWeekStripIsDrawnOnLaunchAndAfterATabSwitch() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        let today = element(app, "kit.weekDay.\(Self.today)")
        XCTAssertTrue(waitUntilHittable(today))
        attachScreenshot(named: "strip-launch")
        XCTAssertTrue(today.waitUntilDrawn(timeout: stepTimeout), "today's day of the strip is drawn on launch")

        app.selectTab("home")
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: stepTimeout))
        app.selectTab("schedule")
        XCTAssertTrue(waitUntilHittable(today))
        attachScreenshot(named: "strip-tab-switch")
        XCTAssertTrue(today.waitUntilDrawn(timeout: stepTimeout), "today's day of the strip is drawn after a tab switch")
    }

    func testFastPagingDoesNotLoseTheSelectedDay() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Europe/Moscow"))
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "yyyy-MM-dd"
        let today = try XCTUnwrap(formatter.date(from: Self.today))
        let weekdayOffset = (calendar.component(.weekday, from: today) + 5) % 7
        let target = try XCTUnwrap(calendar.date(byAdding: .day, value: 14 - weekdayOffset + 2, to: today))
        let iso = formatter.string(from: target)
        let strip = element(app, "schedule.strip")
        let weekday = element(app, "kit.weekDay.\(Self.today)")
        XCTAssertTrue(waitUntilHittable(weekday))
        // The scroll view can extend under the root header; swipe through the actual day buttons, not its frame's center.
        let y = weekday.frame.midY
        let start = app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: strip.frame.maxX - 24, dy: y))
        let end = app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: strip.frame.minX + 24, dy: y))
        start.press(forDuration: 0.01, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: 0)
        start.press(forDuration: 0.01, thenDragTo: end, withVelocity: .fast, thenHoldForDuration: 0)
        let chosen = element(app, "kit.weekDay.\(iso)")
        attachScreenshot(named: "week-after-swipes")
        XCTAssertTrue(waitUntilHittable(chosen), "two fast swipes preserve the expected week")
        chosen.tap()
        let row = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'schedule.row.' AND identifier CONTAINS %@", iso))
            .firstMatch
        XCTAssertTrue(waitUntilHittable(row), "the chosen weekday must not jump back to Monday")
        let selected = XCTNSPredicateExpectation(predicate: NSPredicate(format: "selected == true"), object: chosen)
        XCTAssertEqual(XCTWaiter.wait(for: [selected], timeout: stepTimeout), .completed)

        attachScreenshot(named: "chosen-day")
        // safeAreaBar inherits the screen identifier on its buttons; choose the header action by label and bounds.
        let todayAction = app.buttons.matching(NSPredicate(format: "label == %@", "Сегодня"))
            .allElementsBoundByIndex.first { $0.frame.maxY < strip.frame.maxY }
        try XCTUnwrap(todayAction).tap()
        let todayRow = todayRow(app)
        if calendar.component(.weekday, from: today) == 1 && !todayRow.exists {
            let saturday = try XCTUnwrap(calendar.date(byAdding: .day, value: -1, to: today))
            let weekend = element(app, "schedule.row.weekend-\(formatter.string(from: saturday))")
            XCTAssertTrue(waitUntilHittable(weekend), "Today opens the current free weekend")
        } else {
            XCTAssertTrue(waitUntilHittable(todayRow), "Today returns after fast week paging")
        }
    }

    func testLessonSheetShowsTheLessonAndCloses() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        let sheet = openLessonSheet(app)
        attachScreenshot(named: "lesson")

        app.buttons[Self.close].firstMatch.tap()
        XCTAssertTrue(sheet.waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.schedule").exists)
    }

    func testFriendPickerAnswersTheScheduleAndTheOwnScheduleComesBack() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)

        pickFriend(app, Self.ivan)

        let back = element(app, "schedule.backToMine")
        XCTAssertTrue(back.waitForExistence(timeout: stepTimeout), "the picker's choice reached the schedule")
        attachScreenshot(named: "friend")

        back.tap()
        XCTAssertTrue(back.waitForNonExistence(timeout: stepTimeout), "the own schedule is back")
    }

    func testTheMonthSheetOpensADay() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)

        element(app, "kit.rootHeader").tap()
        let month = element(app, "schedule.month")
        XCTAssertTrue(month.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "month")

        element(app, "schedule.month.open").tap()
        XCTAssertTrue(month.waitForNonExistence(timeout: stepTimeout), "Открыть closes the sheet")
    }

    func testTodayRouteBringsTheOwnScheduleBackOnToday() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        pickFriend(app, Self.ivan)
        let back = element(app, "schedule.backToMine")
        XCTAssertTrue(back.waitForExistence(timeout: stepTimeout))
        for _ in 0..<4 {
            element(app, "schedule.list").swipeUp()
        }

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/today")))

        XCTAssertTrue(back.waitForNonExistence(timeout: stepTimeout), "today is the own schedule")
        let today = todayRow(app)
        XCTAssertTrue(today.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(waitUntilHittable(today), "the feed went back to today")
        attachScreenshot(named: "today")
    }

    /// A found change is an event of the bell (there is no history screen) that opens its lesson.
    func testAChangeInTheBellOpensItsLesson() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        element(app, "shell.bell").tap()
        let changes = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'bell.row.change:'"))
        XCTAssertTrue(changes.firstMatch.waitForExistence(timeout: stepTimeout), "the demo's changes are in the bell")
        attachScreenshot(named: "bell-changes")

        changes.firstMatch.tap()

        XCTAssertTrue(element(app, "schedule.lessonSheet").waitForExistence(timeout: stepTimeout))
    }

    func testTheTodayHeroOpensTheLessonSheet() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        let hero = element(app, "today.hero")
        guard hero.waitForExistence(timeout: stepTimeout) else {
            // Nothing to go to today or tomorrow in the demo's week now.
            throw XCTSkip("no hero on Сегодня now")
        }

        hero.tap()

        XCTAssertTrue(element(app, "schedule.lessonSheet").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "today-lesson")
    }

    func testUserScheduleOpensFromAProfile() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        XCTAssertTrue(element(app, "friends_row").waitForExistence(timeout: stepTimeout))
        element(app, "friends_row").tap()
        let friend = element(app, "user_list_row_\(Self.ivan)")
        XCTAssertTrue(friend.waitForExistence(timeout: stepTimeout))
        friend.tap()
        let schedule = element(app, "user_profile_schedule")
        XCTAssertTrue(schedule.waitForExistence(timeout: stepTimeout))
        if !schedule.isHittable { element(app, "user_profile_list").swipeUp() }

        schedule.tap()

        XCTAssertTrue(element(app, "schedule.user").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(firstLesson(app).waitForExistence(timeout: stepTimeout), "the friend's week has lessons")
        XCTAssertFalse(element(app, "schedule.friends").exists, "no picker on another user's schedule")
        attachScreenshot(named: "user")
        XCTAssertTrue(
            element(app, "kit.weekDay.\(Self.today)").waitUntilDrawn(timeout: stepTimeout), "the pushed schedule's strip is drawn"
        )

        _ = openLessonSheet(app)
        app.buttons[Self.close].firstMatch.tap()
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
    }

    func testScheduleAndLessonSheetAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openSchedule(app)
        XCTAssertTrue(todayRow(app).waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "days-ax1")
        XCTAssertTrue(element(app, "schedule.friends").isHittable, "the friends button is reachable at AX1")

        _ = openLessonSheet(app)
        attachScreenshot(named: "lesson-ax1")
    }

    // MARK: Helpers

    /// `DemoPeople`: a friend in the viewer's group.
    private static let ivan = 999_002
    /// `common_close`.
    private static let close = "Закрыть"

    /// The schedule tab and its feed.
    private func openSchedule(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("schedule")
        XCTAssertTrue(element(app, "shell.root.schedule").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "schedule.list").waitForExistence(timeout: rootTimeout), "the feed never loaded")
    }

    /// The feed's realized rows, for a failure message.
    private func rowIds(_ app: XCUIApplication) -> [String] {
        app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'schedule.row.'"))
            .allElementsBoundByIndex.map(\.identifier)
    }

    /// Today's row of the feed: its day header, its `пар нет` row or its weekend.
    private func todayRow(_ app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'schedule.row.' AND identifier CONTAINS %@", Self.today))
            .firstMatch
    }

    private func lessons(_ app: XCUIApplication) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "timeline.lesson."))
    }

    private func firstLesson(_ app: XCUIApplication) -> XCUIElement {
        lessons(app).firstMatch
    }

    /// The first lesson wholly inside the feed, so a tap lands on it.
    private func lessonOnScreen(_ app: XCUIApplication) -> XCUIElement? {
        XCTAssertTrue(firstLesson(app).waitForExistence(timeout: stepTimeout))
        let list = element(app, "schedule.list").frame
        let rows = lessons(app)
        return (0..<min(rows.count, 20)).lazy
            .map { rows.element(boundBy: $0) }
            .first { list.contains($0.frame) && $0.isHittable }
    }

    private func openLessonSheet(_ app: XCUIApplication) -> XCUIElement {
        if lessonOnScreen(app) == nil { element(app, "schedule.list").swipeUp() }
        guard let lesson = lessonOnScreen(app) else {
            XCTFail("no lesson row on screen")
            return element(app, "schedule.lessonSheet")
        }
        lesson.tap()
        let sheet = element(app, "schedule.lessonSheet")
        XCTAssertTrue(sheet.waitForExistence(timeout: stepTimeout))
        return sheet
    }

    /// Opens the picker and chooses `isu` with one tap.
    private func pickFriend(_ app: XCUIApplication, _ isu: Int) {
        element(app, "schedule.friends").tap()
        let picker = element(app, "schedule.friendPicker")
        XCTAssertTrue(picker.waitForExistence(timeout: stepTimeout))
        let row = element(app, "friendPicker.friend.\(isu)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "picker")
        row.tap()
        XCTAssertTrue(picker.waitForNonExistence(timeout: stepTimeout), "the picker closes once it answers")
    }

    private func waitUntilHittable(_ element: XCUIElement) -> Bool {
        let deadline = Date().addingTimeInterval(stepTimeout)
        while !element.isHittable && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.25))
        }
        return element.isHittable
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
