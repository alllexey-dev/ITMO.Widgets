import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The update offer and the My ITMO page's chrome in all four appearances, one reference per state Android's
/// references cover. The content views are rendered directly, without ViewModels.
@MainActor
final class AccountSnapshotTests: XCTestCase {
    private let pageHeight: CGFloat = 480

    // MARK: Update offer

    func testUpdateSupportedWithNote() {
        let state = AppUpdateUiState(
            installed: "2.3.0",
            latest: "2.4.0",
            note: "Синтетическая заметка о версии: новые виджеты.",
            unsupported: false
        )
        assertAppearances(of: AppUpdateContent(state: state), named: "update-supported")
    }

    func testUpdateUnsupported() {
        let state = AppUpdateUiState(installed: "2.3.0", latest: "2.5.0", note: "", unsupported: true)
        assertAppearances(of: AppUpdateContent(state: state), named: "update-unsupported")
    }

    // MARK: My ITMO

    func testMyItmoWebLoading() {
        let page = MyItmoWebContent(load: .loading, progress: 0.4, retry: {}) {
            Color(uiColor: .secondarySystemBackground)
        }
        assertAppearances(of: page, named: "my-itmo-loading", appearances: [.light, .dark], height: pageHeight)
    }

    func testMyItmoWebFailed() {
        let page = MyItmoWebContent(load: .failed, retry: {}) {
            Color(uiColor: .secondarySystemBackground)
        }
        assertScreenAppearances(of: page, named: "my-itmo-failed", height: pageHeight)
    }
}
