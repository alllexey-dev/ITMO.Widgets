import XCTest

/// The recordbook on the shared demo session (IO-09d2): L12's Compose list as the first tab's root, the period picker
/// and `Мои баллы` in SwiftUI sheets hosting the shared sheet content, the subject page pushed on the recordbook stack
/// with its control points and the demo's own total from the stream's sheet, no links section while iOS does not offer
/// subject links (IO-09f), the demo's refusal of BARS, and the list at the accessibility text size AX1. Compose maps
/// `testTag` to the accessibility identifier (`RecordbookTestTags`, `RecordbookPeriodSheetTestTags`,
/// `RecordbookSubjectTestTags`, `SubjectSheetTotalTestTags`); the texts are Russian on an English iPhone. The ids
/// are the demo set's (`DemoRecordbook`, `DemoStudy`).
final class RecordbookUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testTheListSwitchesToAnEarlierPeriodInThePicker() {
        let app = XCUIApplication.itmo()
        app.launch()
        openRecordbook(app)
        XCTAssertTrue(subjectRow(app, Self.algorithms).waitForExistence(timeout: stepTimeout), "the current semester")
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the list draws its own top bar, as on Android")
        attachScreenshot(named: "list")

        element(app, "recordbook_period").tap()
        XCTAssertTrue(element(app, "recordbook.period").waitForExistence(timeout: stepTimeout))
        let first = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH 'recordbook_period_' AND identifier ENDSWITH '_1'")
        ).firstMatch
        XCTAssertTrue(first.waitForExistence(timeout: stepTimeout), "the first semester is a period of the demo")
        attachScreenshot(named: "period")
        first.tap()

        XCTAssertTrue(waitForAbsence(element(app, "recordbook.period")), "a pick closes the picker")
        XCTAssertTrue(subjectRow(app, Self.programming).waitForExistence(timeout: stepTimeout), "the first semester")
        XCTAssertFalse(subjectRow(app, Self.algorithms).exists)
        attachScreenshot(named: "list-first-semester")
    }

    func testThePeriodPickerClosesByItsButtonAndByADrag() {
        let app = XCUIApplication.itmo()
        app.launch()
        openRecordbook(app)
        XCTAssertTrue(subjectRow(app, Self.algorithms).waitForExistence(timeout: stepTimeout))

        element(app, "recordbook_period").tap()
        XCTAssertTrue(element(app, "recordbook.period").waitForExistence(timeout: stepTimeout))
        app.buttons[Self.closeLabel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(element(app, "recordbook.period")), "the sheet's own close button dismisses it")

        element(app, "recordbook_period").tap()
        XCTAssertTrue(element(app, "recordbook.period").waitForExistence(timeout: stepTimeout))
        dragDown(app, element(app, "recordbook.period"))
        XCTAssertTrue(waitForAbsence(element(app, "recordbook.period")), "a drag down dismisses it")
        XCTAssertTrue(subjectRow(app, Self.algorithms).exists, "the period stays as it was")
    }

    func testBarsSaysItIsNotInTheDemo() {
        let app = XCUIApplication.itmo()
        app.launch()
        openRecordbook(app)
        XCTAssertTrue(subjectRow(app, Self.algorithms).waitForExistence(timeout: stepTimeout))

        // The chip's state outlives a launch: turned on it asks BARS at once, so at most two taps show the refusal.
        let refusal = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", Self.demoUnavailable))
            .firstMatch
        for _ in 0..<2 where !refusal.waitForExistence(timeout: 3) {
            element(app, "recordbook_bars").tap()
        }
        XCTAssertTrue(refusal.waitForExistence(timeout: stepTimeout), "the demo has no BARS account")
        attachScreenshot(named: "bars-demo")
    }

    func testTheSubjectPageShowsTheControlsAndTheSheetTotalAndOpensMyScores() {
        let app = XCUIApplication.itmo()
        app.launch()
        openRecordbook(app)
        let row = subjectRow(app, Self.algorithms)
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        if !row.isHittable { element(app, "recordbook_list").swipeUp() }
        row.tap()

        XCTAssertTrue(element(app, "recordbook.subject").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "recordbook_subject_list").waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the page draws its own top bar")
        let total = element(app, "subject_sheet_total")
        XCTAssertTrue(total.waitForExistence(timeout: stepTimeout), "the demo's own total from the stream's sheet")
        XCTAssertTrue(labelled(app, Self.controlsTitle).waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(labelled(app, Self.linksTitle).exists, "no subject links before IO-09f (App Review 1.2)")
        XCTAssertFalse(labelled(app, Self.chatsTitle).exists)
        attachScreenshot(named: "subject")

        element(app, "subject_sheet_total_menu").tap()
        let change = labelled(app, Self.changeTotalLabel)
        XCTAssertTrue(change.waitForExistence(timeout: stepTimeout))
        change.tap()
        XCTAssertTrue(element(app, "recordbook.sheetScores").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(labelled(app, Self.myScoresTitle).waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "my-scores")
        dragDown(app, element(app, "recordbook.sheetScores"))
        XCTAssertTrue(waitForAbsence(element(app, "recordbook.sheetScores")), "a drag down dismisses the sheet")

        app.buttons[Self.backLabel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(element(app, "recordbook.subject")), "the page's own back button")
        XCTAssertTrue(element(app, "recordbook_list").waitForExistence(timeout: stepTimeout))
    }

    func testTheListAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openRecordbook(app)
        let list = element(app, "recordbook_list")
        XCTAssertTrue(list.waitForExistence(timeout: stepTimeout))
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
    private static let subjectRowPrefix = "recordbook_subject_99"
    /// `common_close`, `recordbook_back`, `error_demo_unavailable`, `sheet_scores_change_total`, `sheet_scores_title`,
    /// `subject_controls_title`, `links_title` and `links_chats`.
    private static let closeLabel = "Закрыть"
    private static let backLabel = "К зачётке"
    private static let demoUnavailable = "Недоступно в демо"
    private static let changeTotalLabel = "Изменить итог"
    private static let myScoresTitle = "Мои баллы"
    private static let controlsTitle = "Контрольные точки"
    private static let linksTitle = "Ссылки"
    private static let chatsTitle = "Чаты"

    private func openRecordbook(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("recordbook")
        XCTAssertTrue(element(app, "shell.root.recordbook").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "recordbook_list").waitForExistence(timeout: rootTimeout), "the list never loaded")
    }

    /// The list row of the discipline `id` in whichever semester the demo shows.
    private func subjectRow(_ app: XCUIApplication, _ id: Int) -> XCUIElement {
        app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "recordbook_subject_\(id)")
        ).firstMatch
    }

    /// The first element whose label is `text` (a Compose heading is no static text).
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
