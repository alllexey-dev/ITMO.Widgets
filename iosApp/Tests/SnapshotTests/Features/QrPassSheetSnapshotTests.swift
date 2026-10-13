import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The pass sheet on the shared `QrPassUiState` of every status, presented as the app presents it (a content-height
/// kit sheet), drawn in a window. The holder and the code are synthetic; the code is the demo pass.
@MainActor
final class QrPassSheetSnapshotTests: XCTestCase {
    private let holder = QrPassHolder(name: "Александрова Мария", details: UiTextDynamic(value: "M3205, ИСУ 123456"))
    private let code = QrPassCode(hex: "DEMO000000000000", useDynamicColors: false)

    func testReady() {
        assertSheet(QrPassUiState(holder: holder, code: code, status: QrPassStatusReady(time: "11:44")), named: "ready")
    }

    /// A layer render: the spinners would turn between two window renders.
    func testRefreshing() {
        let state = QrPassUiState(holder: holder, code: code, status: QrPassStatusRefreshing.shared)
        let content = QrPassContent(state: state).padding(ItmoKit.margin)
        assertAppearances(of: content, named: "refreshing", appearances: [.light, .dark])
    }

    func testOffline() {
        let state = QrPassUiState(holder: holder, code: code, status: QrPassStatusOffline(time: "11:44"))
        assertSheet(state, named: "offline", appearances: [.light, .dark])
    }

    /// The user's QR colour setting: the code in the app's scheme.
    func testDynamicColors() {
        let themed = QrPassCode(hex: code.hex, useDynamicColors: true)
        let state = QrPassUiState(holder: holder, code: themed, status: QrPassStatusReady(time: "1 сент., 11:44"))
        assertSheet(state, named: "dynamic", appearances: [.light, .dark])
    }

    func testFailed() {
        let state = QrPassUiState(holder: holder, code: nil, status: QrPassStatusFailed(error: AppErrorNetwork.shared))
        assertSheet(state, named: "failed", appearances: [.light, .dark])
    }

    func testEmpty() {
        let state = QrPassUiState(holder: nil, code: nil, status: QrPassStatusEmpty.shared)
        assertSheet(state, named: "empty", appearances: [.light, .dark])
    }

    private func assertSheet(
        _ state: QrPassUiState,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        let screen = Color(.systemGroupedBackground).sheet(isPresented: .constant(true)) {
            ItmoSheet(title: AppStrings.string("qr_pass_sheet_title"), height: .content) {
                QrPassContent(state: state)
            }
        }
        assertScreenAppearances(
            of: screen, named: name, appearances: appearances, height: 760, settle: 2.5,
            file: file, testName: testName, line: line
        )
    }
}
