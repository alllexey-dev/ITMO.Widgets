import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The first-run flow's two steps, as Android's `OnboardingScreen` references: the permissions under the person
/// asked and granted, the connection before and after (and on an iPhone SE), and the consent sheet. `OnboardingContent` is rendered directly from `OnboardingSamples`, in a window so the glass
/// buttons draw as on screen.
@MainActor
final class OnboardingSnapshotTests: XCTestCase {
    private let samples = OnboardingSamples.shared

    func testPermissions() {
        assertScreenAppearances(of: content("permissions"), named: "permissions")
    }

    func testPermissionsNotAsked() {
        assertScreenAppearances(of: content("permissions-none"), named: "permissions-none", appearances: [.light, .dark])
    }

    func testConnection() {
        assertScreenAppearances(of: content("connection"), named: "connection")
    }

    /// iPhone SE (375 x 667 pt) without its 20 pt status bar: the last step fits without scrolling.
    func testConnectionSmallPhone() {
        assertScreenAppearances(of: content("connection"), named: "connection-small", appearances: [.light, .dark], width: 375, height: 647)
    }

    func testConnected() {
        assertScreenAppearances(of: content("connected"), named: "connected", appearances: [.light, .dark])
    }

    func testConsent() {
        let screen = content("connection").sheet(isPresented: .constant(true)) { ServicesConsentSheet(connect: {}) }
        assertScreenAppearances(of: screen, named: "consent", appearances: [.light, .dark], settle: 1.5)
    }

    private func content(_ step: String) -> some View {
        OnboardingContent(state: samples.state(step: step), actions: OnboardingActions())
    }
}
