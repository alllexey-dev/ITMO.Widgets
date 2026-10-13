import XCTest

/// The recordbook on the shared demo session: the SwiftUI list as the tab's root under the native root header with the
/// period menu, the subject page pushed on the recordbook stack with its three tabs, `Мои баллы` in a SwiftUI sheet
/// hosting the shared sheet content, and the list at the accessibility text size AX1. The ids are the demo set's
/// (`DemoRecordbook`, `DemoStudy`); the texts are Russian on an English iPhone.
final class RecordbookUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testTheListSwitchesToAnEarlierPeriodInTheMenu() {
        let app = XCUIApplication.itmo()
        app.launch()
        openRecordbook(app)
        XCTAssertTrue(subjectRow(app, Self.algorithms).waitForExistence(timeout: stepTimeout), "the current semester")
        XCTAssertTrue(app.navigationBars.firstMatch.exists, "the root header is the native bar")
        attachScreenshot(named: "list")

        element(app, "recordbook.period").tap()
        let first = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", Self.firstSemester)).firstMatch
        XCTAssertTrue(first.waitForExistence(timeout: stepTimeout), "the first semester is a period of the demo")
        attachScreenshot(named: "period")
        first.tap()

        XCTAssertTrue(subjectRow(app, Self.programming).waitForExistence(timeout: stepTimeout), "the first semester")
        XCTAssertFalse(subjectRow(app, Self.algorithms).exists)
        attachScreenshot(named: "list-first-semester")
    }

    func testTheSubjectPageShowsItsTabsAndOpensMyScores() {
        let app = XCUIApplication.itmo()
        app.launch()
        openRecordbook(app)
        let row = subjectRow(app, Self.algorithms)
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        if !row.isHittable { element(app, "recordbook.list").swipeUp() }
        row.tap()

        XCTAssertTrue(element(app, "recordbook.subject").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "subject.hero").waitForExistence(timeout: stepTimeout), "the scores tab first")
        attachScreenshot(named: "subject-scores")

        let menu = app.buttons[Self.sheetActions].firstMatch
        XCTAssertTrue(menu.waitForExistence(timeout: stepTimeout), "the demo's own total from the stream's sheet")
        menu.tap()
        app.buttons[Self.changeTotalLabel].firstMatch.tap()
        XCTAssertTrue(element(app, "recordbook.sheetScores").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "my-scores")
        dragDown(app, element(app, "recordbook.sheetScores"))
        XCTAssertTrue(waitForAbsence(element(app, "recordbook.sheetScores")), "a drag down dismisses the sheet")

        app.buttons[Self.lessonsTab].firstMatch.tap()
        XCTAssertTrue(labelled(app, Self.teachersTitle).waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "subject-lessons")
        app.buttons[Self.materialsTab].firstMatch.tap()
        XCTAssertTrue(labelled(app, Self.chatsTitle).waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "subject-materials")

        app.navigationBars.buttons.element(boundBy: 0).tap()
        XCTAssertTrue(waitForAbsence(element(app, "recordbook.subject")), "back to the list")
        XCTAssertTrue(element(app, "recordbook.list").waitForExistence(timeout: stepTimeout))
    }

    func testTheListAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openRecordbook(app)
        let list = element(app, "recordbook.list")
        XCTAssertTrue(subjectRow(app, Self.algorithms).waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "list-ax1")

        // The list scrolls: its last subject comes clear of the demo banner and the tab bar.
        for _ in 0..<12 { list.swipeUp() }
        attachScreenshot(named: "list-ax1-end")
        let rows = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", Self.subjectRowPrefix)
        )
        let last = rows.element(boundBy: rows.count - 1)
        XCTAssertTrue(last.isHittable, "the last subject is reachable at AX1")
        XCTAssertLessThanOrEqual(last.frame.maxY, element(app, "kit.demoBanner").frame.minY)
    }

    // MARK: Helpers

    /// `DemoStudy.ALGORITHMS` (the current semester, with the connected sheet) and the first semester's programming
    /// (`DemoRecordbook.FIRST_SEMESTER`); a row's id is the discipline's tenfold plus the semester.
    private static let algorithms = 990_203
    private static let programming = 990_401
    private static let subjectRowPrefix = "recordbook.row.99"
    /// `subject_semester` of the first, `sheet_scores_actions`, `sheet_scores_change_total`, `subject_tab_lessons`,
    /// `subject_tab_materials`, `subject_teachers_title`, `subject_materials_chats`.
    private static let firstSemester = "1 семестр"
    private static let sheetActions = "Действия с таблицей"
    private static let changeTotalLabel = "Изменить итог"
    private static let lessonsTab = "Пары"
    private static let materialsTab = "Материалы"
    private static let teachersTitle = "Преподаватели"
    private static let chatsTitle = "Чаты"

    private func openRecordbook(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("recordbook")
        XCTAssertTrue(element(app, "shell.root.recordbook").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "recordbook.list").waitForExistence(timeout: rootTimeout), "the list never loaded")
    }

    /// The list row of the discipline `id` in whichever semester the demo shows.
    private func subjectRow(_ app: XCUIApplication, _ id: Int) -> XCUIElement {
        element(app, "recordbook.row.\(id)")
    }

    private func labelled(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).firstMatch
    }

    /// A drag from the sheet's top edge to the bottom of the screen, as a person closes a sheet.
    private func dragDown(_ app: XCUIApplication, _ sheet: XCUIElement) {
        sheet.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.01))
            .press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.98)))
    }

    private func waitForAbsence(_ element: XCUIElement) -> Bool {
        let gone = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: element)
        return XCTWaiter().wait(for: [gone], timeout: stepTimeout) == .completed
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
