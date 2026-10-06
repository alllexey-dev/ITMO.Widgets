import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The shell on fixtures (IO-06b) in all four appearances: the home stack with the demo banner, the QR pass pushed
/// over home, the signed-out gate and the damaged-link sheet. The tab bar is system chrome drawn with materials a
/// layer render misses, so it is left to `ShellUITests` screenshots and the stacks are rendered without it.
@MainActor
final class ShellSnapshotTests: XCTestCase {
    /// A full screen at the matrix width: the banner sits at the bottom.
    private let screenHeight: CGFloat = 640

    func testHomeInDemo() {
        assertAppearances(of: stack(.home, mountedRouter(), isDemo: true), named: "home-demo", height: screenHeight)
    }

    func testScheduleRoot() {
        assertAppearances(of: stack(.schedule, mountedRouter(), isDemo: false), named: "schedule", height: screenHeight)
    }

    func testQrPassOverHome() {
        let router = mountedRouter()
        router.open(.qrPass)
        assertAppearances(of: stack(.home, router, isDemo: false), named: "qr-pass", height: screenHeight)
    }

    func testSignedOutGate() {
        let shell = ShellView(router: AppRouter(), session: ShellSession(state: .signedOut))
        assertAppearances(of: shell, named: "gate", height: screenHeight)
    }

    func testLinkUnavailableSheet() {
        assertAppearances(of: ShellSheetView(sheet: .linkUnavailable), named: "sheet", height: 400)
    }

    private func stack(_ tab: ShellTab, _ router: AppRouter, isDemo: Bool) -> some View {
        ShellStack(tab: tab, router: router, isDemo: isDemo, leaveDemo: {})
    }

    private func mountedRouter() -> AppRouter {
        let router = AppRouter()
        router.sessionChanged(ready: true)
        router.shellMounted(true)
        return router
    }
}
