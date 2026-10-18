import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The first-run flow at the design lab's axes, as Android's `OnboardingScreen` references: the first step with the
/// user's lesson and sport and without them, the permissions asked and granted, the connection before and after, and
/// the consent sheet. `OnboardingContent` is rendered directly from `OnboardingSamples`, in a window so the glass
/// buttons draw as on screen.
@MainActor
final class OnboardingSnapshotTests: XCTestCase {
    private let samples = OnboardingSamples.shared

    func testValue() {
        assertScreenAppearances(of: content("value"), named: "value")
    }

    func testValueWithoutData() {
        assertScreenAppearances(of: content("value-empty"), named: "value-empty", appearances: [.light, .dark])
    }

    func testPermissions() {
        assertScreenAppearances(of: content("permissions"), named: "permissions")
    }

    func testPermissionsNotAsked() {
        assertScreenAppearances(of: content("permissions-none"), named: "permissions-none", appearances: [.light, .dark])
    }

    func testConnection() {
        assertScreenAppearances(of: content("connection"), named: "connection")
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
