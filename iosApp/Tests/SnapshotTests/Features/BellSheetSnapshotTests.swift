import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The bell's sheet over Сегодня at its tall height, on the shared `BellUiState` of `TodaySamples`: new events with
/// their dots, nothing new, and empty.
@MainActor
final class BellSheetSnapshotTests: XCTestCase {
    private let samples = TodaySamples.shared

    func testNew() {
        assertBell(samples.BellNew, named: "new")
    }

    func testNothingNew() {
        assertBell(samples.BellRead, named: "read", appearances: [.light, .dark])
    }

    func testEmpty() {
        assertBell(samples.BellEmpty, named: "empty", appearances: [.light, .dark])
    }

    private func assertBell(
        _ state: BellUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) {
            ItmoSheet(title: "Уведомления", height: .tall) {
                BellContent(state: state)
            }
        }
        assertScreenAppearances(
            of: screen, named: name, appearances: appearances, settle: 2.5, file: file, testName: testName, line: line
        )
    }
}
