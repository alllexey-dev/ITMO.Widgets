import XCTest

/// The me tab and the social screens on the shared demo session (IO-09e): LA-3's Compose Me tab as the root of the me
/// stack, people search with Compose's text field on the system keyboard, the person profile with the demo's refusal
/// of a friendship action and no reviews section while iOS does not offer reviews (IO-09f), the friends list and a
/// user's friends, the home feed's friend-requests card, and the Me tab at the accessibility text size AX1. Compose maps `testTag` to the accessibility
/// identifier (`MeTestTags`, `UserSearchTestTags`, `UserListTestTags`, `UserProfileTestTags`); the texts are Russian
/// on an English iPhone. The ISUs are the demo set's (`DemoPeople`).
final class SocialUITests: XCTestCase {
    private let rootTimeout: TimeInterval = 30
    private let stepTimeout: TimeInterval = 10

    override func setUp() {
        continueAfterFailure = false
    }

    func testMeTabShowsTheDemoProfileAndItsGroups() {
        let app = XCUIApplication.itmo()
        app.launch()
        openMe(app)

        XCTAssertTrue(element(app, "profile_name").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(app.staticTexts[Self.meName].exists, "the demo's own name")
        XCTAssertTrue(element(app, "friends_row").exists)
        XCTAssertTrue(element(app, "find_people_row").exists)
        XCTAssertFalse(element(app, "debug_tools_row").exists, "the developer tools stay Android-only")
        XCTAssertFalse(app.navigationBars.firstMatch.exists, "the tab draws no top bar, as on Android")
        attachScreenshot(named: "me")
    }

    func testSearchOpensAProfileWhoseFriendRequestTheDemoRefuses() {
        let app = XCUIApplication.itmo()
        app.launch()
        openMe(app)
        element(app, "find_people_row").tap()
        XCTAssertTrue(element(app, "social.search").waitForExistence(timeout: stepTimeout))

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

        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "social.search").waitForExistence(timeout: stepTimeout))
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
    }

    func testTeacherProfileHasNoReviewsSection() {
        let app = XCUIApplication.itmo()
        app.launch()
        openMe(app)
        element(app, "find_people_row").tap()
        XCTAssertTrue(element(app, "social.search").waitForExistence(timeout: stepTimeout))

        search(app, "Корнилов")
        let row = element(app, "user_list_row_\(Self.mathTeacher)")
        XCTAssertTrue(row.waitForExistence(timeout: stepTimeout))
        row.tap()

        XCTAssertTrue(element(app, "user_profile_hero").waitForExistence(timeout: stepTimeout))
        element(app, "user_profile_list").swipeUp()
        attachScreenshot(named: "teacher-profile")
        XCTAssertFalse(element(app, "user_profile_reviews").exists, "no reviews before IO-09f (App Review 1.2)")
        XCTAssertFalse(element(app, "user_profile_write_review").exists)
    }

    func testFriendsOpenAProfileAndItsFriends() {
        let app = XCUIApplication.itmo()
        app.launch()
        openMe(app)
        element(app, "friends_row").tap()
        XCTAssertTrue(element(app, "social.friends").waitForExistence(timeout: stepTimeout))
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
        app.buttons[QrPassUITests.backLabel].tap()
        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
    }

    /// The friend-requests card of the home feed (`socialModule`'s source and renderer, loaded with this card) opens
    /// the requester's profile and all requests on the home stack, as Android's `homeActions`.
    func testHomeFriendRequestsCardOpensTheProfileAndTheFriends() {
        let app = XCUIApplication.itmo()
        app.launch()
        XCTAssertTrue(element(app, "shell.root.home").waitForExistence(timeout: rootTimeout))
        let card = element(app, "home_card_friend_requests")
        XCTAssertTrue(card.waitForExistence(timeout: stepTimeout), "the demo's incoming request shows on home")
        attachScreenshot(named: "home-requests")

        element(app, "home_friend_row").tap()
        XCTAssertTrue(element(app, "social.profile").waitForExistence(timeout: stepTimeout))
        XCTAssertTrue(element(app, "user_profile_primary").waitForExistence(timeout: stepTimeout))
        app.buttons[QrPassUITests.backLabel].tap()

        element(app, "home_friends_all").tap()
        XCTAssertTrue(element(app, "social.friends").waitForExistence(timeout: stepTimeout))
    }

    func testMeTabAtAccessibilityTextSize() {
        let ax1 = ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityM"]
        let app = XCUIApplication.itmo(arguments: ax1)
        app.launch()
        openMe(app)
        XCTAssertTrue(element(app, "profile_name").waitForExistence(timeout: stepTimeout))
        attachScreenshot(named: "me-ax1")

        // The tab scrolls: its last control, the sign-out, comes clear of the demo banner and the tab bar.
        let signOut = element(app, "sign_out_row")
        for _ in 0..<4 where !signOut.isHittable {
            element(app, "main").swipeUp()
        }
        attachScreenshot(named: "me-ax1-end")
        XCTAssertTrue(signOut.isHittable, "the sign-out is reachable at AX1")
        XCTAssertLessThanOrEqual(signOut.frame.maxY, element(app, "kit.demoBanner").frame.minY)
    }

    // MARK: Helpers

    /// `DemoPeople`: the viewer, a friend, the incoming request and a teacher.
    private static let me = 999_001
    private static let ivan = 999_002
    private static let sofia = 999_006
    private static let mathTeacher = 999_101
    private static let meName = "Анна Смирнова"
    /// The demo refusal, `error_demo_unavailable`, inside `friends_action_failed`.
    private static let demoUnavailable = "Недоступно в демо"

    private func openMe(_ app: XCUIApplication) {
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: rootTimeout))
        app.tabBars.firstMatch.buttons.element(boundBy: 3).tap()
        XCTAssertTrue(element(app, "shell.root.me").waitForExistence(timeout: stepTimeout))
    }

    /// Types `query` into the search field with the system keyboard. The route focuses the field when it opens, so a
    /// tap (which would show the edit menu over the list) is needed only while no keyboard is up.
    private func search(_ app: XCUIApplication, _ query: String) {
        let field = element(app, "user_search_field")
        XCTAssertTrue(field.waitForExistence(timeout: stepTimeout))
        if !app.keyboards.firstMatch.waitForExistence(timeout: stepTimeout) { field.tap() }
        field.typeText(query)
    }

    private func element(_ app: XCUIApplication, _ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }
}
