import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The dormitory screen under the native bar on the shared states of `DormitorySamples`: each stage of the
/// application, the rental agreement with a debt, past years and without an application, the stored answer offline,
/// no application and a failure. Drawn without the ViewModel, 375 pt wide.
@MainActor
final class DormitorySnapshotTests: XCTestCase {
    private let samples = DormitorySamples.shared

    func testQueue() {
        assertScreen(samples.queue, named: "queue")
    }

    func testPlaceOffered() {
        assertScreen(samples.placeOffered, named: "place-offered")
    }

    func testDateChosen() {
        assertScreen(samples.dateChosen, named: "date-chosen")
    }

    func testSettled() {
        assertScreen(samples.settled, named: "settled")
    }

    func testDebt() {
        assertScreen(samples.debt, named: "debt")
    }

    func testPastYears() {
        assertScreen(samples.pastYears, named: "past-years")
    }

    func testContractOnly() {
        assertScreen(samples.contractOnly, named: "contract-only")
    }

    func testOffline() {
        assertScreen(samples.offline, named: "offline")
    }

    func testNoApplication() {
        assertScreen(samples.none, named: "none")
    }

    func testFailed() {
        assertScreen(samples.failed, named: "failed")
    }

    private func assertScreen(_ state: DormitoryUiState, named name: String, testName: String = #function) {
        let screen = NavigationStack {
            DormitoryContentView(state: state)
                .itmoDetailHeader(title: AppStrings.string("dormitory_title"), subtitle: nil)
        }
        assertScreenAppearances(of: screen, named: name, appearances: [.light, .dark], settle: 1, testName: testName)
    }
}
