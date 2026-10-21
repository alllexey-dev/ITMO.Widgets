import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// Another user's sport pushed under the native bar on the shared `SportSamples` (Мария's synthetic Monday): Иван's
/// two Thursday bookings, no bookings, and the lock of a user who hides their sport.
@MainActor
final class UserSportSnapshotTests: XCTestCase {
    private let samples = SportSamples.shared

    func testBookings() {
        assertUserSport(UserSportUiStateContent(bookings: samples.friendBookings, refreshing: false), named: "content")
    }

    func testNoBookings() {
        assertUserSport(
            UserSportUiStateContent(bookings: [], refreshing: false), givenName: "Дмитрий", named: "empty",
            appearances: [.light, .dark]
        )
    }

    func testHidden() {
        assertUserSport(UserSportUiStateError(error: AppErrorForbidden.shared), named: "lock", appearances: [.light, .dark])
    }

    private func assertUserSport(
        _ state: any UserSportUiState,
        givenName: String = "Иван",
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            UserSportContent(state: state, time: samples.time)
                .itmoDetailHeader(title: AppStrings.string("user_profile_sport_title"), subtitle: givenName)
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}
