import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The tuition screen under the native bar on the shared states of `TuitionSamples`: an agreement paid on time, one
/// with a debt, past years unfolded, the stored answer offline, no agreement and a failure. Drawn without the
/// ViewModel, 375 pt wide.
@MainActor
final class TuitionSnapshotTests: XCTestCase {
    private let samples = TuitionSamples.shared

    func testOnTime() {
        assertScreen(samples.onTime, named: "on-time")
    }

    func testDebt() {
        assertScreen(samples.debt, named: "debt")
    }

    func testPastYears() {
        assertScreen(samples.pastYears, named: "past-years")
    }

    func testOffline() {
        assertScreen(samples.offline, named: "offline")
    }

    func testNoContracts() {
        assertScreen(samples.none, named: "none")
    }

    func testFailed() {
        assertScreen(samples.failed, named: "failed")
    }

    private func assertScreen(_ state: TuitionUiState, named name: String, testName: String = #function) {
        let screen = NavigationStack {
            TuitionContentView(state: state)
                .itmoDetailHeader(title: AppStrings.string("tuition_title"), subtitle: nil)
        }
        assertScreenAppearances(of: screen, named: name, appearances: [.light, .dark], settle: 1, testName: testName)
    }
}
