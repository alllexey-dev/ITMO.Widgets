import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Профиль in the shell (the static root header with `Поделиться`, the glass tab bar), Друзья and another user's
/// friends under the native bar, the person card for each kind of person and the report of a review, on the shared
/// states of `MeSamples` and `SocialSamples` (the design lab's synthetic people). Drawn in a window, 375 pt wide.
@MainActor
final class SocialSnapshotTests: XCTestCase {
    private let me = MeSamples.shared
    private let social = SocialSamples.shared

    func testProfileWithRequests() {
        assertProfile(me.state(connection: "on", requests: "yes"), named: "profile")
    }

    func testProfileWithoutRequests() {
        assertProfile(me.state(connection: "on", requests: "no"), named: "profile-no-requests", appearances: [.light, .dark])
    }

    func testProfileNotConnected() {
        assertProfile(me.state(connection: "off", requests: "no"), named: "profile-off", appearances: [.light, .dark])
    }

    func testProfileLongName() {
        assertProfile(me.longName, named: "profile-long-name", appearances: [.light, .lightAX1])
    }

    func testFriends() {
        assertFriends(social.friends(state: "list"), named: "friends")
    }

    func testFriendsEmpty() {
        assertFriends(social.friends(state: "empty"), named: "friends-empty", appearances: [.light, .dark])
    }

    func testFriendsSearch() {
        assertFriends(social.search, named: "friends-search", appearances: [.light, .dark])
    }

    func testFriendsDisabled() {
        assertFriends(social.friendsDisabled, named: "friends-disabled", appearances: [.light, .dark])
    }

    func testCardOfAStudent() {
        assertCard(social.card(who: "app", relation: "none", teaches: "no"), named: "card-app")
    }

    func testCardOfAFriend() {
        assertCard(social.card(who: "app", relation: "friend", teaches: "no"), named: "card-friend", appearances: [.light, .dark])
    }

    func testCardOfAStudentWithoutTheApp() {
        assertCard(social.card(who: "noapp", relation: "none", teaches: "no"), named: "card-noapp", appearances: [.light, .dark])
    }

    func testCardOfATeacher() {
        assertCard(
            social.card(who: "teacher", relation: "none", teaches: "no"),
            named: "card-teacher",
            appearances: [.light, .dark],
            height: 1500
        )
    }

    func testCardOfATeacherWhoTeachesYou() {
        assertCard(social.card(who: "teacher", relation: "none", teaches: "yes"), named: "card-teacher-teaches", height: 1600)
    }

    func testCardOfAFriendWhoTeachesYou() {
        assertCard(
            social.card(who: "both", relation: "friend", teaches: "yes"),
            named: "card-both-friend",
            appearances: [.light, .dark],
            height: 1300
        )
    }

    func testCardWithAnIncomingRequest() {
        assertCard(social.incomingCard, named: "card-incoming", appearances: [.light, .dark, .lightAX1])
    }

    func testOwnCard() {
        assertCard(social.ownCard, named: "card-own", appearances: [.light, .dark])
    }

    func testCardNotFound() {
        assertCard(social.notFound, named: "card-not-found", appearances: [.light, .dark])
    }

    func testUserFriends() {
        let screen = NavigationStack {
            UserFriendsContent(state: social.userFriends)
                .itmoDetailHeader(title: AppStrings.string("friends_title"), subtitle: "Каримов Тимур")
        }
        assertScreenAppearances(of: screen, named: "user-friends", appearances: [.light, .dark], settle: 1)
    }

    func testReportReview() {
        let form = NavigationStack {
            ReportReviewForm(reason: .constant(.wrongTeacher), comment: .constant("Ведёт другой предмет"))
                .navigationTitle(Text(verbatim: AppStrings.string("review_report_title")))
                .navigationBarTitleDisplayMode(.inline)
        }
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) { form }
        assertScreenAppearances(of: screen, named: "report-review", appearances: [.light, .dark], settle: 1.5)
    }

    private func assertProfile(
        _ state: MeUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = ShellTabs(selection: .constant(.profile), openPass: {}) { _ in
            NavigationStack {
                MeContent(state: state)
                    .itmoRootHeader(title: ShellTab.profile.title, subtitle: nil) { MeShareButton {} }
            }
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }

    private func assertFriends(
        _ state: FriendsUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            FriendsContent(state: state, query: .constant(state.query))
                .itmoDetailHeader(
                    title: AppStrings.string("friends_title"),
                    subtitle: (state.lists as? FriendListsContent)?.subtitle?.resolved
                )
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }

    private func assertCard(
        _ state: any UserProfileUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        height: CGFloat = SnapshotMatrix.screenHeight,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            UserProfileContent(state: state)
                .userProfileBar(state as? UserProfileUiStateContent) { _ in }
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, height: height, settle: 1, testName: testName)
    }
}
