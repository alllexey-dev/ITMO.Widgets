import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The notifications page at the design lab's axes, as Android's `NotificationSettingsScreen` references: allowed
/// with one push type off, denied by the system with the gate, and not connected with Background App Refresh off. `NotificationSettingsContent` is rendered from `NotificationSettingsSamples` under the pushed header.
@MainActor
final class NotificationSettingsSnapshotTests: XCTestCase {
    private let samples = NotificationSettingsSamples.shared

    func testAllowed() {
        assertPushed(samples.state(state: "on"), named: "on")
    }

    func testDeniedInTheSystem() {
        assertPushed(samples.state(state: "off"), named: "off")
    }

    func testNotConnectedWithBackgroundRefreshOff() {
        assertPushed(samples.state(state: "offline"), named: "offline", appearances: [.light, .dark])
    }

    private func assertPushed(
        _ state: NotificationSettingsUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            NotificationSettingsContent(state: state)
                .itmoDetailHeader(title: AppStrings.string("notifications_title"), subtitle: state.subtitle?.resolved)
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}
