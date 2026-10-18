import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The connection page at the design lab's axes, as Android's `ConnectionScreen` references: connected with the
/// audiences, not connected, Backend failing the audiences with BARS signed out, the end of the page, and the consent
/// sheet. `ConnectionDataContent` is rendered from `ConnectionSamples` under the pushed header, in a window.
@MainActor
final class ConnectionSnapshotTests: XCTestCase {
    private let samples = ConnectionSamples.shared

    func testConnected() {
        assertPushed(ConnectionDataContent(state: samples.state(state: "on")), named: "on")
    }

    func testNotConnected() {
        assertPushed(ConnectionDataContent(state: samples.state(state: "off")), named: "off")
    }

    func testAudiencesFailedAndBarsExpired() {
        assertPushed(
            ConnectionDataContent(state: samples.state(state: "failed")).defaultScrollAnchor(.bottom),
            named: "failed-end",
            appearances: [.light, .dark]
        )
    }

    func testTheEndOfThePage() {
        assertPushed(
            ConnectionDataContent(state: samples.state(state: "on")).defaultScrollAnchor(.bottom),
            named: "on-end",
            appearances: [.light, .dark]
        )
    }

    func testConsent() {
        let screen = NavigationStack {
            ConnectionDataContent(state: samples.state(state: "off"))
                .itmoDetailHeader(title: AppStrings.string("connection_title"), subtitle: nil)
        }
        .sheet(isPresented: .constant(true)) { ServicesConsentSheet(connect: {}) }
        assertScreenAppearances(of: screen, named: "consent", appearances: [.light, .dark], settle: 1.5)
    }

    private func assertPushed<Content: View>(
        _ content: Content,
        named name: String,
        appearances: [SnapshotAppearance] = SnapshotAppearance.all,
        testName: String = #function
    ) {
        let screen = NavigationStack {
            content.itmoDetailHeader(title: AppStrings.string("connection_title"), subtitle: nil)
        }
        assertScreenAppearances(of: screen, named: name, appearances: appearances, settle: 1, testName: testName)
    }
}
