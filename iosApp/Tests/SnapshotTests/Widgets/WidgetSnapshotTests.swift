import SwiftUI
import XCTest
@testable import ITMOWidgets

/// The widget entry views (IO-10a) at their family size in all four appearances. WidgetKit's container background is
/// ignored outside a widget, so each entry view paints its own tile; the home screen clips the corners.
final class WidgetSnapshotTests: XCTestCase {
    /// The fixture's generation time: 2026-09-01T08:00:30Z, so the spoiler's noise never changes.
    private let date = Date(timeIntervalSince1970: 1_788_249_630)

    func testQrSpoiler() {
        assertQr(.spoiler, named: "spoiler")
    }

    func testQrRevealed() throws {
        assertQr(.revealed(matrix: try fixtureMatrix(), demo: false), named: "revealed")
    }

    func testQrRevealedInDemo() throws {
        assertQr(.revealed(matrix: try fixtureMatrix(), demo: true), named: "demo")
    }

    func testQrExpired() {
        assertQr(.expired, named: "expired")
    }

    func testQrSignedOut() {
        assertQr(.signedOut, named: "signed-out")
    }

    private func assertQr(
        _ content: QrWidgetContent,
        named name: String,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line
    ) {
        assertAppearances(
            of: QrWidgetEntryView(entry: QrWidgetEntry(date: date, content: content)),
            named: name,
            width: WidgetSizes.small.width,
            height: WidgetSizes.small.height,
            file: file,
            testName: testName,
            line: line
        )
    }

    /// The demo pass the shared writer writes (`iosApp/Tests/UnitTests/Fixtures/qr-pass-v1.json`), read from the
    /// source tree as the references are.
    private func fixtureMatrix() throws -> [[Bool]] {
        let fixture = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../../UnitTests/Fixtures/qr-pass-v1.json")
            .standardized
        return try XCTUnwrap(QrPassSnapshot.decode(Data(contentsOf: fixture))).matrix
    }
}
