import XCTest

/// Профиль and the social screens, all SwiftUI, on the shared demo session: the profile tab and its rows, Друзья with
/// its search, the person card with the demo's refusal of a friendship action and a teacher's reviews section
/// (`ReviewsLinksUITests` covers its editor and report), a user's friends, the bell's friend request, and the profile
/// at the accessibility text size AX1. The identifiers are Compose's test tags kept on the SwiftUI views (`MeTestTags`,
/// `FriendsTestTags`, `UserListTestTags`, `UserProfileTestTags`); the texts are Russian on an English iPhone. The ISUs
/// are the demo set's (`DemoPeople`).
final class SocialUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testProfileShowsTheDemoProfileAndItsRows() {
        let app = XCUIApplication.itmo()
        app.launch()
        openMe(app)

        XCTAssertTrue(element(app, "profile_name").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(app.staticTexts[Self.meName].exists, "the demo's own name")
        for row in ["profile_share_button", "friends_row", "privacy_row", "connection_row", "notifications_row", "widgets_row"] {
            XCTAssertTrue(element(app, row).exists, "\(row) is on the profile")
        }
        XCTAssertFalse(element(app, "debug_tools_row").exists, "the developer tools stay Android-only")
        XCTAssertFalse(element(app, "people_gate").exists, "the demo is connected")
        attachScreenshot(named: "me")
    }

    func testSearchFindsAPersonWhoseFriendRequestTheDemoRefuses() {
        let app = XCUIApplication.itmo()
        app.launch()
        openFriends(app)

        search(app, "Софи")
        let row = element(app, "user_list_row_\(Self.sofia)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout), "the typed Cyrillic query found the demo person")
        attachScreenshot(named: "search")
        row.tap()

        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
        let accept = element(app, "user_profile_primary")
        XCTAssertTrue(accept.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "profile-incoming")
        accept.tap()

        let refusal = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", Self.demoUnavailable)).firstMatch
        XCTAssertTrue(refusal.waitForExistence(timeout: stepTimeout), "the demo sends no friend request")
        attachScreenshot(named: "profile-demo-refusal")

        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(element(app, "social.friends").waitForExistence(timeout: stepTimeout))
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
    }

    func testTeacherProfileShowsTheReviewsSection() {
        let app = XCUIApplication.itmo()
        app.launch()
        openFriends(app)
        search(app, "Корнилов")
        let row = element(app, "user_list_row_\(Self.mathTeacher)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        row.tap()

        let hero = element(app, "user_profile_hero")
        XCTAssertTrue(hero.waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(hero.waitUntilDrawn(timeout: stepTimeout), "the profile hero is drawn")
        XCTAssertTrue(element(app, "user_profile_write_review").waitForExistence(timeout: stepTimeout))
        let reviews = element(app, "user_profile_reviews")
        XCTAssertTrue(reviews.waitForExistence(timeout: stepTimeout), "the reviews section")
        for _ in 0..<6 where !reviews.isShown { element(app, "user_profile_list").swipeUp(velocity: .slow) }
        attachScreenshot(named: "teacher-profile")
        XCTAssertTrue(reviews.isShown, "the reviews section is drawn")
    }

    func testFriendsOpenAProfileAndItsFriends() {
        let app = XCUIApplication.itmo()
        app.launch()
        openFriends(app)
        let friend = element(app, "user_list_row_\(Self.ivan)")
        XCTAssertTrue(friend.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "friends")
        friend.tap()

        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
        let friends = element(app, "user_profile_friends")
        XCTAssertTrue(friends.waitForExistence(timeout: stepTimeout))
        if !friends.isHittable { element(app, "user_profile_list").swipeUp() }
        friends.tap()

        XCTAssertTrue(element(app, "social.userFriends").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "user_list_row_\(Self.me)").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "user-friends")
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
    }

    /// The demo's incoming request is a row of the bell with its answers; the row opens the requester's card.
    func testABellRequestOpensTheProfile() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        element(app, "shell.bell").tap()
        let request = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'bell.row.friend:'")).firstMatch
        XCTAssertTrue(request.waitForExistence(timeout: stepTimeout), "the demo's incoming request is in the bell")
        let accept = app.descendants(matching: .any)
            .matching(NSPredicate(format: "identifier BEGINSWITH 'bell.accept.friend:'")).firstMatch
        XCTAssertTrue(accept.exists)
        attachScreenshot(named: "bell-request")

        request.staticTexts.firstMatch.tap()

        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "user_profile_hero").waitForExistence(timeout: stepTimeout))
    }

    /// My ITMO is a profile row; the demo cannot open it and says so.
    func testMyItmoRowSaysTheDemoCannotOpenIt() {
        let app = XCUIApplication.itmo()
        app.launch()
        openMe(app)
        let row = element(app, "my_itmo_row")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        for _ in 0..<4 where !row.isShown {
            element(app, "main").swipeUp()
        }
        XCTAssertTrue(row.isShown, "the My ITMO row is drawn")

        row.tap()

        let refusal = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", Self.demoUnavailable)).firstMatch
        XCTAssertTrue(refusal.waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "my-itmo-demo")
        XCTAssertTrue(element(app, "shell.root.me").exists, "nothing opens over the profile")
    }

    func testProfileAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openMe(app)
        XCTAssertTrue(element(app, "profile_name").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "me-ax1")

        // The tab scrolls: its last control, the sign-out, comes clear of the demo banner and the tab bar.
        let signOut = element(app, "sign_out_row")
        for _ in 0..<6 where !signOut.isShown {
            element(app, "main").swipeUp()
        }
        attachScreenshot(named: "me-ax1-end")
        XCTAssertTrue(signOut.isShown, "the sign-out is reachable and drawn at AX1")
        XCTAssertLessThanOrEqual(signOut.frame.maxY, element(app, "kit.demoBanner").frame.minY)
    }

    // MARK: Helpers

    /// `DemoPeople`: the viewer, a friend, the incoming request and a teacher.
    private static let me = 999_001
    private static let ivan = 999_002
    private static let sofia = 999_006
    private static let mathTeacher = 999_101
    private static let meName = "Смирнова Анна"
    /// The demo refusal, `error_demo_unavailable`, inside `friends_action_failed`.
    private static let demoUnavailable = "Недоступно в демо"

    private func openMe(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.selectTab("me")
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
    }

    private func openFriends(_ app: XCUIApplication) {
        openMe(app)
        element(app, "friends_row").tap()
        XCTAssertTrue(element(app, "social.friends").waitForExistence(timeout: stepTimeout))
    }

    /// Types `query` into Друзья's search field (`friends_search`, the screen's only text field) with the system keyboard.
    private func search(_ app: XCUIApplication, _ query: String) {
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: stepTimeout))
        field.tap()
        field.typeText(query)
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
