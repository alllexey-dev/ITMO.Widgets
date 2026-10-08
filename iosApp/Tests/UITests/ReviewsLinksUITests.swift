import XCTest

/// The subject links and the teacher reviews on the shared demo session (IO-09f): L15's Compose sheets in SwiftUI
/// sheets above the shell (all links, the link editor, a link's actions) with the report dialogs as Compose dialogs
/// over what asked for them, and the review editor, a form sheet a drag does not close. The demo shows
/// `DemoSubjectLinks` and `DemoReviews` and refuses every change (`error_demo_unavailable`) without a request. Compose
/// maps `testTag` to the accessibility identifier (`SubjectLinksSheetTestTags`, `LinkEditorSheetTestTags`,
/// `LinkActionsSheetTestTags`, `UserProfileTestTags`, `TeacherReviewTestTags`, `ReviewEditorSheetTestTags`); the texts
/// are Russian on an English iPhone. The ids are the demo set's (`DemoStudy`, `DemoPeople`).
final class ReviewsLinksUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testAllLinksVoteAndAddAndEditALink() {
        let app = XCUIApplication.itmo()
        app.launch()
        openAlgorithms(app)
        XCTAssertTrue(labelled(app, Self.linksTitle).waitForExistence(timeout: stepTimeout), "the links section")
        attachScreenshot(named: "subject-links")

        tapRevealed(app.buttons[Self.allLinks], in: element(app, "recordbook_subject_list"))
        let all = element(app, "resources.links")
        XCTAssertTrue(all.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "subject_links_list").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "all-links")

        // A vote in the demo: the sheet says why nothing changed.
        all.buttons[Self.voteDown].firstMatch.tap()
        XCTAssertTrue(message(app, Self.demoUnavailable).waitForExistence(timeout: stepTimeout), "the demo refusal")
        XCTAssertTrue(waitForAbsence(message(app, Self.demoUnavailable)), "the banner goes after a few seconds")

        // A new link over the list: the demo refuses to save it, and the form stays.
        element(app, "subject_links_add").tap()
        let editor = element(app, "resources.linkEditor")
        XCTAssertTrue(editor.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(labelled(app, Self.newLink).waitForExistence(timeout: stepTimeout))
        let url = element(app, "link_editor_url")
        url.tap()
        url.typeText(Self.newUrl)
        element(app, "link_editor_category_SCORES").tap()
        element(app, "link_editor_save").tap()
        XCTAssertTrue(message(app, Self.demoUnavailable).waitForExistence(timeout: stepTimeout), "the demo refusal")
        XCTAssertTrue(editor.exists, "a refused save keeps the form")
        attachScreenshot(named: "editor-new")
        dragDown(app, editor)
        XCTAssertTrue(waitForAbsence(editor), "a drag down closes the editor")
        XCTAssertTrue(all.exists, "the list stays under it")

        // An own link: its actions over the list, then the editor in their place.
        let own = all.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", Self.ownLink)).firstMatch
        for _ in 0..<4 where !own.isHittable { element(app, "subject_links_list").swipeUp(velocity: .slow) }
        XCTAssertTrue(own.waitForExistence(timeout: stepTimeout))
        Thread.sleep(forTimeInterval: 1)
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
        XCTAssertTrue(all.waitForExistence(timeout: stepTimeout), "the list is still under the editor")

        all.buttons[Self.closeLabel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(all), "the sheet's own close button")
    }

    func testALinksReportIsTheDialogOverItsActions() {
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
        XCTAssertTrue(labelled(app, Self.linkReportTitle).exists, "a refused report keeps the dialog")
        attachScreenshot(named: "link-report")

        app.buttons[Self.cancel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(labelled(app, Self.linkReportTitle)))
        XCTAssertTrue(actions.exists, "the actions stay under the dialog")
        dragDown(app, actions)
        XCTAssertTrue(waitForAbsence(actions))
        XCTAssertTrue(element(app, "recordbook.subject").exists)
    }

    func testAReviewIsWrittenFromAProfileAndAnotherReported() {
        let app = XCUIApplication.itmo()
        app.launch()
        openTeacher(app)

        // The report of another student's review: the Compose dialog over the profile.
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
        let all = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", Self.allLinksPrefix)).firstMatch
        tapRevealed(all, in: element(app, "recordbook_subject_list"), screenshot: "subject-links-ax1")
        let sheet = element(app, "resources.links")
        XCTAssertTrue(sheet.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "all-links-ax1")

        // The add button stays pinned under the list, inside the sheet.
        let add = element(app, "subject_links_add")
        XCTAssertTrue(add.isHittable, "the add button is reachable at AX1")
        XCTAssertLessThanOrEqual(add.frame.maxY, sheet.frame.maxY)
        add.tap()
        XCTAssertTrue(element(app, "resources.linkEditor").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "editor-ax1")
        dragDown(app, element(app, "resources.linkEditor"))
        XCTAssertTrue(waitForAbsence(element(app, "resources.linkEditor")))
        sheet.buttons[Self.closeLabel].firstMatch.tap()
        XCTAssertTrue(waitForAbsence(sheet))

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
    /// `links_title`, `links_all_count`, `links_vote_down`, `links_editor_new`, `links_editor_edit`, `common_close`,
    /// `error_demo_unavailable`, `links_report_title`, `links_report_spam` (`review_report_spam`), `links_report_send`
    /// (`review_report_send`), `common_cancel`, `teacher_review_report`, `review_report_title`, `review_editor_new`,
    /// `review_discard_title`, `review_discard`.
    private static let linksTitle = "Ссылки"
    private static let allLinks = "Все ссылки, 5"
    private static let allLinksPrefix = "Все ссылки"
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
    private static let reviewText = "Лекции понятные, на практике разбирают каждую задачу до конца."

    private func openAlgorithms(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("recordbook")
        XCTAssertTrue(element(app, "recordbook_list").waitForExistence(timeout: rootTimeout), "the list never loaded")
        let row = app.descendants(matching: .any).matching(
            NSPredicate(format: "identifier BEGINSWITH %@", "recordbook_subject_\(Self.algorithms)")
        ).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        if !row.isHittable { element(app, "recordbook_list").swipeUp() }
        row.tap()
        XCTAssertTrue(element(app, "recordbook.subject").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "recordbook_subject_list").waitForExistence(timeout: stepTimeout))
    }

    /// The math teacher's profile through people search, with its reviews section.
    private func openTeacher(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
        element(app, "find_people_row").tap()
        let field = element(app, "user_search_field")
        XCTAssertTrue(field.waitForExistence(timeout: stepTimeout))
        if !app.keyboards.firstMatch.waitForExistence(timeout: stepTimeout) { field.tap() }
        field.typeText("Корнилов")
        let row = element(app, "user_list_row_\(Self.mathTeacher)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        row.tap()
        XCTAssertTrue(element(app, "user_profile_hero").waitForExistence(timeout: stepTimeout))
        let reviews = element(app, "user_profile_reviews")
        for _ in 0..<6 where !reviews.exists { element(app, "user_profile_list").swipeUp(velocity: .slow) }
        XCTAssertTrue(reviews.waitForExistence(timeout: stepTimeout), "the reviews section shows on iOS")
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
