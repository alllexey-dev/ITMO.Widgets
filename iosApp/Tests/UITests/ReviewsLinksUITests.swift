import XCTest

/// The subject links and the teacher reviews on the shared demo session: the SwiftUI sheets above the subject page's
/// materials tab (the link editor, a link's actions) with the link report as a form sheet over the actions; on a
/// teacher's card the report of a review, a form sheet, and the review editor, a form sheet a drag does not close. The
/// demo shows `DemoSubjectLinks` and `DemoReviews` and refuses every change (`error_demo_unavailable`) without a
/// request. The texts are Russian on an English iPhone. The ids are the demo set's (`DemoStudy`, `DemoPeople`).
final class ReviewsLinksUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testVoteAndAddAndEditALinkOnTheMaterialsTab() {
        let app = XCUIApplication.itmo()
        app.launch()
        openAlgorithms(app)
        XCTAssertTrue(labelled(app, Self.chatsTitle).waitForExistence(timeout: stepTimeout), "the chats come first")
        attachScreenshot(named: "subject-materials")

        // A vote in the demo: the page says why nothing changed.
        app.buttons[Self.voteDown].firstMatch.tap()
        XCTAssertTrue(message(app, Self.demoUnavailable).waitForExistence(timeout: stepTimeout), "the demo refusal")
        XCTAssertTrue(waitForAbsence(message(app, Self.demoUnavailable)), "the banner goes after a few seconds")

        // A new link from the bar: the demo refuses to save it, and the form stays.
        element(app, "subject.addLink").tap()
        let editor = element(app, "resources.linkEditor")
        XCTAssertTrue(editor.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(labelled(app, Self.newLink).waitForExistence(timeout: stepTimeout))
        let url = element(app, "link_editor_url")
        url.tap()
        url.typeText(Self.newUrl)
        app.buttons[Self.scoresCategory].firstMatch.tap()
        element(app, "link_editor_save").tap()
        XCTAssertTrue(message(app, Self.demoUnavailable).waitForExistence(timeout: stepTimeout), "the demo refusal")
        XCTAssertTrue(editor.exists, "a refused save keeps the form")
        attachScreenshot(named: "editor-new")
        dragDown(app, editor)
        XCTAssertTrue(waitForAbsence(editor), "a drag down closes the editor")

        // An own link: its actions on a long press, then the editor in their place.
        let own = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", Self.ownLink)).firstMatch
        for _ in 0..<4 where !own.isHittable { element(app, "recordbook.subject").swipeUp(velocity: .slow) }
        XCTAssertTrue(own.waitForExistence(timeout: stepTimeout))
        own.press(forDuration: 1.2)
        let actions = element(app, "resources.linkActions")
        XCTAssertTrue(actions.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "link_actions_edit").exists, "an own link can be edited")
        XCTAssertFalse(element(app, "link_actions_report").exists, "and is not reported")
        attachScreenshot(named: "actions-own")
        element(app, "link_actions_edit").tap()
        XCTAssertTrue(waitForAbsence(actions), "the actions give way to the editor")
        XCTAssertTrue(labelled(app, Self.editLink).waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "editor-edit")
        dragDown(app, element(app, "resources.linkEditor"))
        XCTAssertTrue(waitForAbsence(element(app, "resources.linkEditor")))
        XCTAssertTrue(element(app, "recordbook.subject").exists, "the page is still under the editor")
    }

    func testALinksReportIsAFormOverItsActions() {
        let app = XCUIApplication.itmo()
        app.launch()
        openAlgorithms(app)

        let link = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", Self.sharedLink)).firstMatch
        XCTAssertTrue(link.waitForExistence(timeout: stepTimeout))
        link.press(forDuration: 1.2)
        let actions = element(app, "resources.linkActions")
        XCTAssertTrue(actions.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "link_actions_author").exists, "another student's link names its author")
        attachScreenshot(named: "actions-shared")

        element(app, "link_actions_report").tap()
        XCTAssertTrue(labelled(app, Self.linkReportTitle).waitForExistence(timeout: stepTimeout))
        XCTAssertFalse(app.buttons[Self.send].isEnabled, "a reason first")
        app.buttons[Self.spam].firstMatch.tap()
        app.buttons[Self.send].firstMatch.tap()
        XCTAssertTrue(message(app, Self.demoUnavailable).waitForExistence(timeout: stepTimeout), "under the comment")
        XCTAssertTrue(labelled(app, Self.linkReportTitle).exists, "a refused report keeps the form")
        attachScreenshot(named: "link-report")

        app.buttons[Self.cancel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(labelled(app, Self.linkReportTitle)))
        XCTAssertTrue(actions.exists, "the actions stay under the form")
        dragDown(app, actions)
        XCTAssertTrue(waitForAbsence(actions))
        XCTAssertTrue(element(app, "recordbook.subject").exists)
    }

    func testAReviewIsWrittenFromAProfileAndAnotherReported() {
        let app = XCUIApplication.itmo()
        app.launch()
        openTeacher(app)

        // The report of another student's review: the SwiftUI form sheet over the card.
        tapRevealed(element(app, "teacher_review_more"), in: element(app, "user_profile_list"))
        let report = labelled(app, Self.reportLabel)
        XCTAssertTrue(report.waitForExistence(timeout: stepTimeout))
        report.tap()
        XCTAssertTrue(labelled(app, Self.reviewReportTitle).waitForExistence(timeout: stepTimeout))
        app.buttons[Self.spam].firstMatch.tap()
        app.buttons[Self.send].firstMatch.tap()
        XCTAssertTrue(message(app, Self.demoUnavailable).waitForExistence(timeout: stepTimeout), "under the comment")
        attachScreenshot(named: "review-report")
        app.buttons[Self.cancel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(labelled(app, Self.reviewReportTitle)))

        // A new review: a form sheet the demo refuses to send and a drag does not close.
        tapRevealed(element(app, "user_profile_write_review"), in: element(app, "user_profile_list"))
        let editor = element(app, "reviews.editor")
        XCTAssertTrue(editor.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(labelled(app, Self.newReview).waitForExistence(timeout: stepTimeout))
        let text = element(app, "review_editor_text")
        text.tap()
        text.typeText(Self.reviewText)
        element(app, "review_editor_send").tap()
        XCTAssertTrue(message(app, Self.demoUnavailable).waitForExistence(timeout: stepTimeout), "the demo refusal")
        attachScreenshot(named: "review-editor")
        dragDown(app, editor)
        XCTAssertTrue(editor.exists, "a drag does not close a form")

        editor.buttons[Self.closeLabel].firstMatch.tap()
        XCTAssertTrue(labelled(app, Self.discardTitle).waitForExistence(timeout: stepTimeout), "unsaved changes ask")
        attachScreenshot(named: "review-discard")
        app.buttons[Self.discard].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(editor))
        XCTAssertTrue(element(app, "social.profile").exists)
    }

    func testTheSheetsAndTheReviewsAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openAlgorithms(app)
        attachScreenshot(named: "subject-materials-ax1")
        element(app, "subject.addLink").tap()
        XCTAssertTrue(element(app, "resources.linkEditor").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "editor-ax1")
        dragDown(app, element(app, "resources.linkEditor"))
        XCTAssertTrue(waitForAbsence(element(app, "resources.linkEditor")))

        openTeacher(app)
        let list = element(app, "user_profile_list")
        for _ in 0..<12 { list.swipeUp() }
        attachScreenshot(named: "reviews-ax1-end")
        let reviews = app.descendants(matching: .any).matching(identifier: "teacher_review")
        let last = reviews.element(boundBy: reviews.count - 1)
        XCTAssertTrue(last.exists, "the last review is reachable at AX1")
        XCTAssertLessThanOrEqual(last.frame.maxY, element(app, "kit.demoBanner").frame.minY)
    }

    // MARK: Helpers

    /// `DemoStudy.ALGORITHMS` in the current semester (the recordbook row's id is the discipline's tenfold plus the
    /// semester); `DemoPeople.MATH_TEACHER`, whose reviews `DemoReviews` has.
    private static let algorithms = 990_203
    private static let mathTeacher = 999_101
    /// `subject_materials_chats`, `subject_tab_materials`, `links_vote_down`, `links_editor_new`, `links_editor_edit`, `common_close`,
    /// `error_demo_unavailable`, `links_report_title`, `links_report_spam` (`review_report_spam`), `links_report_send`
    /// (`review_report_send`), `common_cancel`, `teacher_review_report`, `review_report_title`, `review_editor_new`,
    /// `review_discard_title`, `review_discard`.
    private static let chatsTitle = "Чаты"
    private static let materialsTab = "Материалы"
    private static let voteDown = "Бесполезная ссылка"
    private static let newLink = "Новая ссылка"
    private static let editLink = "Изменить ссылку"
    private static let closeLabel = "Закрыть"
    private static let demoUnavailable = "Недоступно в демо"
    private static let linkReportTitle = "Жалоба на ссылку"
    private static let spam = "Спам"
    private static let send = "Отправить"
    private static let cancel = "Отмена"
    private static let reportLabel = "Пожаловаться"
    private static let reviewReportTitle = "Жалоба на отзыв"
    private static let newReview = "Новый отзыв"
    private static let discardTitle = "Не сохранять отзыв?"
    private static let discard = "Не сохранять"
    /// `DemoSubjectLinks`: Anna's own notes and another student's lecture notes of the algorithms.
    private static let ownLink = "Мои заметки к экзамену"
    private static let sharedLink = "Конспекты лекций"
    private static let newUrl = "https://disk.yandex.ru/d/demo-notes"
    /// `links_category_scores`.
    private static let scoresCategory = "Таблица баллов"
    private static let reviewText = "Лекции понятные, на практике разбирают каждую задачу до конца."

    /// The algorithms page on its materials tab.
    private func openAlgorithms(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("recordbook")
        XCTAssertTrue(element(app, "shell.root.recordbook").waitForExistence(timeout: rootTimeout), "the list never loaded")
        let row = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "recordbook.row.\(Self.algorithms)")
        ).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        if !row.isHittable { element(app, "shell.root.recordbook").swipeUp() }
        row.tap()
        XCTAssertTrue(element(app, "recordbook.subject").waitForExistence(timeout: stepTimeout))
        app.buttons[Self.materialsTab].firstMatch.tap()
    }

    /// The math teacher's card through the search of Друзья, with its reviews section.
    private func openTeacher(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
        element(app, "friends_row").tap()
        XCTAssertTrue(element(app, "social.friends").waitForExistence(timeout: stepTimeout))
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: stepTimeout))
        field.tap()
        field.typeText("Корнилов")
        let row = element(app, "user_list_row_\(Self.mathTeacher)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        row.tap()
        XCTAssertTrue(element(app, "user_profile_hero").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "user_profile_reviews").waitForExistence(timeout: stepTimeout), "the reviews section shows on iOS")
    }

    /// Scrolls `list` in short drags until `target` lies wholly inside it (a lazy list composes a row only near the
    /// screen), waits until it stops moving (a tap during a fling only stops it), then taps the centre of `target`: XCUITest's
    /// element tap does not open the menu of a Compose icon. `screenshot` names a capture of the settled screen.
    private func tapRevealed(_ target: XCUIElement, in list: XCUIElement, screenshot: String? = nil) {
        XCTAssertTrue(list.waitForExistence(timeout: stepTimeout))
        let top = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
        let bottom = list.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.7))
        for _ in 0..<16 {
            if target.exists, list.frame.contains(target.frame) { break }
            if target.exists, target.frame.minY < list.frame.minY {
                top.press(forDuration: 0.05, thenDragTo: bottom)
            } else {
                bottom.press(forDuration: 0.05, thenDragTo: top)
            }
        }
        var frame = target.frame
        for _ in 0..<10 {
            Thread.sleep(forTimeInterval: 0.5)
            if target.frame == frame { break }
            frame = target.frame
        }
        if let screenshot { attachScreenshot(named: screenshot) }
        XCTAssertTrue(list.frame.contains(target.frame), "\(target) is on the screen")
        XCTAssertTrue(target.isDrawn, "\(target) is drawn")
        target.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }

    /// A message by its text: the Swift banner over a sheet or the line under a report's comment.
    private func message(_ app: XCUIApplication, _ text: String) -> XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
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
