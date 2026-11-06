import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The ISU messages under the native bar on the shared states of `IsuMessagesSamples`: the inbox by month, one message
/// with its attachments, and the sign-in state. Drawn without the ViewModel, 375 pt wide.
@MainActor
final class MessagesSnapshotTests: XCTestCase {
    private let samples = IsuMessagesSamples.shared

    func testInbox() {
        assertList(samples.Inbox, named: "inbox")
    }

    func testSignIn() {
        assertList(samples.SignIn, named: "sign-in")
    }

    func testOpened() {
        guard let detail = samples.Opened.opened else { return XCTFail("the sample has no opened message") }
        let screen = NavigationStack {
            IsuMessageDetailView(detail: detail, downloading: samples.Opened.downloading)
        }
        assertScreenAppearances(of: screen, named: "opened", appearances: [.light, .dark], settle: 1)
    }

    private func assertList(_ state: IsuMessagesUiState, named name: String, testName: String = #function) {
        let screen = NavigationStack {
            IsuMessagesContent(state: state)
                .itmoDetailHeader(title: AppStrings.string("isu_messages_title"), subtitle: state.unreadText?.resolved)
        }
        assertScreenAppearances(of: screen, named: name, appearances: [.light, .dark], settle: 1, testName: testName)
    }
}
