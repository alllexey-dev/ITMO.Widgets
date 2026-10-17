import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The scores sheet at its tall height on the shared states of `SheetScoresPreviewSamples` (the Compose previews'
/// samples): the total's review sure, to check and hand-picked, every cell without a total header, the own rows,
/// loading and a failure.
@MainActor
final class SheetScoresSnapshotTests: XCTestCase {
    private let samples = SheetScoresPreviewSamples.shared

    func testTotal() {
        assertSheet(samples.pickTotal, named: "total")
    }

    func testTotalToCheck() {
        assertSheet(samples.pickTotalCheck, named: "total-check", appearances: [.light, .dark])
    }

    func testTotalPickedByHand() {
        assertSheet(samples.pickTotalManual, named: "total-manual", appearances: [.light, .dark])
    }

    func testEveryCell() {
        assertSheet(samples.pickManyTotals, named: "cells", appearances: [.light, .dark])
    }

    func testRows() {
        assertSheet(samples.pickTabRow, named: "rows", appearances: [.light, .dark])
    }

    func testLoading() {
        assertSheet(SheetScoresUiStateLoading.shared, named: "loading", appearances: [.light])
    }

    func testOffline() {
        assertSheet(SheetScoresUiStateFailed(status: .network), named: "offline", appearances: [.light, .dark])
    }

    private func assertSheet(
        _ state: SheetScoresUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) {
            ItmoSheet(title: AppStrings.string("sheet_scores_title"), height: .tall) {
                SheetScoresContent(subject: SheetScoresPreviewSamples.shared.SUBJECT, state: state)
            }
        }
        assertScreenAppearances(
            of: screen, named: name, appearances: appearances, settle: 2.5, file: file, testName: testName, line: line
        )
    }
}
