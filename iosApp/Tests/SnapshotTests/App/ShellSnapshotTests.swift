import Shared
import SnapshotTesting
import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The shell on fixtures (IO-06b) in all four appearances: the signed-out gate and the damaged-link sheet. The tab bar
/// is system chrome drawn with materials a layer render misses, so it is left to `ShellUITests` screenshots; so are the
/// tab stacks, whose roots are all Compose screens a snapshot misses (Metal): the recordbook (`RecordbookUITests`), the
/// schedule (`ScheduleUITests`), the home feed (`HomeUITests`), the sport tab (`SportUITests`), the Me tab
/// (`SocialUITests`) and the QR pass (`QrPassUITests`). The demo banner is `DesignSystemSnapshotTests`'.
@MainActor
final class ShellSnapshotTests: XCTestCase {
    /// A full screen at the matrix width: the banner sits at the bottom.
    private let screenHeight: CGFloat = 640

    func testSignedOutGate() {
        let shell = ShellView(router: AppRouter(), session: ShellSession(state: .signedOut))
        assertAppearances(of: shell, named: "gate", height: screenHeight)
    }

    func testLinkUnavailableSheet() {
        assertAppearances(of: ShellSheetView(sheet: .linkUnavailable), named: "sheet", height: 400)
    }
}
