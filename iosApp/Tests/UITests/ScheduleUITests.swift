import XCTest

/// The schedule on the shared demo session (IO-09b): L10's Compose schedule as the root of the schedule stack (the
/// days from today), the lesson sheet with the friends on the lesson and the place handed to Apple Maps, the friend
/// picker's round trip back to the schedule, the found changes from the home card, the `today` route, another user's
/// schedule from a profile, and the schedule and the lesson sheet at the accessibility text size AX1. Compose maps
/// `testTag` to the accessibility identifier (`ScheduleScreenTestTags`, `ScheduleListTestTags`,
/// `LessonDetailsTestTags`, `FriendSelectorTestTags`, `ScheduleChangesTestTags`); the texts are Russian on an English
/// iPhone. The ISUs are the demo set's (`DemoPeople`).
final class ScheduleUITests: XCTestCase {
    /// Today's day card: the demo's days follow the academic clock, Moscow time.
    static var todayTag: String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Moscow") ?? .current
        let day = calendar.dateComponents([.year, .month, .day], from: Date())
        return String(format: "schedule_day_%04d-%02d-%02d", day.year ?? 0, day.month ?? 0, day.day ?? 0)
    }

    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testDemoScheduleOpensOnTodayUnderItsOwnChrome() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)

        let today = element(app, Self.todayTag)
        XCTAssertTrue(today.waitForExistence(timeout: stepTimeout), "the list opens on today")
        XCTAssertTrue(today.isHittable)
        XCTAssertTrue(firstLesson(app).waitForExistence(timeout: stepTimeout), "the demo week has lessons")
        XCTAssertTrue(element(app, "schedule_friends_button").isHittable)
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the schedule draws its own chrome, as on Android")
        attachScreenshot(named: "days")

        element(app, "schedule_list").swipeUp()
        element(app, "schedule_list").swipeUp()
        attachScreenshot(named: "days-later")
    }

    func testLessonSheetShowsTheFriendsAndHandsThePlaceToMaps() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        let sheet = openLessonSheet(app)

        XCTAssertTrue(element(app, "lesson_details_scroll").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "lesson_details_friends").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "lesson")

        let map = app.buttons[Self.openMap]
        XCTAssertTrue(map.waitForExistence(timeout: stepTimeout), "a demo lesson has a place")
        map.tap()
        let maps = XCUIApplication(bundleIdentifier: "com.apple.Maps")
        XCTAssertTrue(maps.wait(for: .runningForeground, timeout: rootTimeout), "Apple Maps took the place")
        attachScreenshot(named: "lesson-maps")

        app.activate()
        XCTAssertTrue(sheet.waitForExistence(timeout: stepTimeout), "the sheet waits for the return")
        app.buttons[Self.close].firstMatch.tap()
        XCTAssertTrue(sheet.waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.schedule").exists)
    }

    func testLessonSheetClosesOnADragDown() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        let sheet = openLessonSheet(app)
        XCTAssertTrue(element(app, "lesson_details_scroll").waitForExistence(timeout: stepTimeout))

        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95)))

        XCTAssertTrue(sheet.waitForNonExistence(timeout: stepTimeout), "the drag did not close the sheet")
        XCTAssertTrue(element(app, "shell.root.schedule").exists)
    }

    func testFriendPickerAnswersTheScheduleAndTheOwnScheduleComesBack() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)

        pickFriend(app, Self.ivan)

        let selected = element(app, "schedule_selected_user")
        XCTAssertTrue(selected.waitForExistence(timeout: stepTimeout), "the picker's choice reached the schedule")
        XCTAssertTrue(app.staticTexts[Self.ivanName].exists)
        attachScreenshot(named: "friend")

        app.buttons[Self.returnToMine].tap()
        XCTAssertTrue(selected.waitForNonExistence(timeout: stepTimeout), "the own schedule is back")
    }

    func testPickerProfileOpensInTheSheetsPlace() {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        element(app, "schedule_friends_button").tap()
        let picker = element(app, "schedule.friendPicker")
        XCTAssertTrue(picker.waitForExistence(timeout: stepTimeout))
        let row = element(app, "friend_picker_row_\(Self.ivan)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "picker")

        row.press(forDuration: 1.0)

        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(picker.exists, "the profile replaced the sheet")
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "shell.root.schedule").waitForExistence(timeout: stepTimeout))
    }

    func testTodayRouteBringsTheOwnScheduleBackOnToday() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        openSchedule(app)
        pickFriend(app, Self.ivan)
        let selected = element(app, "schedule_selected_user")
        XCTAssertTrue(selected.waitForExistence(timeout: stepTimeout))
        for _ in 0..<4 {
            element(app, "schedule_list").swipeUp()
        }
        XCTAssertFalse(element(app, Self.todayTag).isHittable, "the reader left today")

        app.open(try XCTUnwrap(URL(string: "itmowidgets://route/today")))

        XCTAssertTrue(selected.waitForNonExistence(timeout: stepTimeout), "today is the own schedule")
        let today = element(app, Self.todayTag)
        XCTAssertTrue(today.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(waitUntilHittable(today), "the list went back to today")
        attachScreenshot(named: "today")
    }

    func testChangesOpenFromTheHomeCardAndGoBack() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        let card = element(app, "home_card_schedule_changes")
        XCTAssertTrue(card.waitForExistence(timeout: stepTimeout), "the demo's unread changes show on home")

        card.tap()

        XCTAssertTrue(element(app, "schedule.changes").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "schedule_changes_list").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the route draws its own top bar")
        attachScreenshot(named: "changes")
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "schedule.changes").waitForNonExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "shell.root.home").exists)
    }

    func testHomeLessonOpensTheLessonSheet() throws {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        let row = element(app, "home_schedule_row")
        guard row.waitForExistence(timeout: stepTimeout) else {
            // A day with no lesson left (late evening, a free day) shows no schedule row on home.
            throw XCTSkip("no lesson on the demo home card now")
        }

        row.tap()

        XCTAssertTrue(element(app, "schedule.lessonDetails").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "home-lesson")
    }

    func testUserScheduleOpensFromAProfile() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.tabBars.firstMatch.buttons.element(boundBy: 3).tap()
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
        XCTAssertTrue(element(app, "user_schedule_top_bar").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(firstLesson(app).waitForExistence(timeout: stepTimeout), "the friend's week has lessons")
        XCTAssertFalse(element(app, "schedule_friends_button").exists, "no picker on another user's schedule")
        attachScreenshot(named: "user")

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
        XCTAssertTrue(element(app, Self.todayTag).waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "days-ax1")

        // The list's last control, the friends button, stays clear of the demo banner and the tab bar.
        let friends = element(app, "schedule_friends_button")
        XCTAssertTrue(friends.isHittable, "the friends button is reachable at AX1")
        XCTAssertLessThanOrEqual(friends.frame.maxY, element(app, "kit.demoBanner").frame.minY)

        let sheet = openLessonSheet(app)
        XCTAssertTrue(element(app, "lesson_details_scroll").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "lesson-ax1")
        let scroll = element(app, "lesson_details_scroll")
        scroll.swipeUp()
        scroll.swipeUp()
        let friendsSection = element(app, "lesson_details_friends")
        XCTAssertTrue(friendsSection.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "lesson-ax1-end")
        XCTAssertLessThanOrEqual(friendsSection.frame.minY, sheet.frame.maxY, "the sheet's end scrolls into view")
    }

    // MARK: Helpers

    /// `DemoPeople`: a friend in the viewer's group.
    private static let ivan = 999_002
    private static let ivanName = "Иван Кузнецов"
    /// `sport_open_map`, `common_close`, `schedule_return_to_mine`.
    private static let openMap = "Открыть на карте"
    private static let close = "Закрыть"
    private static let returnToMine = "Вернуться к своему"

    /// The schedule tab, the first of the bar while the recordbook has no tab, and its list or state.
    private func openSchedule(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.tabBars.firstMatch.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(element(app, "shell.root.schedule").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "schedule_list").waitForExistence(timeout: rootTimeout), "the list never loaded")
    }

    /// The first lesson row in the tree, by the start of its tag; it can sit above the list's top (the day before
    /// today peeks in).
    private func firstLesson(_ app: XCUIApplication) -> XCUIElement {
        lessons(app).firstMatch
    }

    private func lessons(_ app: XCUIApplication) -> XCUIElementQuery {
        app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH %@", "schedule_lesson_"))
    }

    /// The first lesson row wholly inside the list, so a tap lands on it.
    private func lessonOnScreen(_ app: XCUIApplication) -> XCUIElement? {
        XCTAssertTrue(firstLesson(app).waitForExistence(timeout: stepTimeout))
        let list = element(app, "schedule_list").frame
        let rows = lessons(app)
        return (0..<min(rows.count, 20)).lazy
            .map { rows.element(boundBy: $0) }
            .first { list.contains($0.frame) && $0.isHittable }
    }

    private func openLessonSheet(_ app: XCUIApplication) -> XCUIElement {
        guard let lesson = lessonOnScreen(app) else {
            XCTFail("no lesson row on screen")
            return element(app, "schedule.lessonDetails")
        }
        lesson.tap()
        let sheet = element(app, "schedule.lessonDetails")
        XCTAssertTrue(sheet.waitForExistence(timeout: stepTimeout))
        return sheet
    }

    /// Opens the picker, chooses `isu` among the friends and applies the choice.
    private func pickFriend(_ app: XCUIApplication, _ isu: Int) {
        element(app, "schedule_friends_button").tap()
        let picker = element(app, "schedule.friendPicker")
        XCTAssertTrue(picker.waitForExistence(timeout: stepTimeout))
        let row = element(app, "friend_picker_row_\(isu)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        row.tap()
        element(app, "friend_picker_apply").tap()
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
