import SnapshotTesting
import SwiftUI
import XCTest

/// The harness proof (IO-17): the sample card's references in all four appearances, and a one-pixel change that
/// the shared precision must reject.
final class SampleSnapshotTests: XCTestCase {
    func testSampleCard() {
        assertAppearances(of: SampleCard.long, named: "card")
    }

    func testOnePixelChangeFails() throws {
        let changed = SampleCard.long.overlay(alignment: .topLeading) {
            // One pixel at the fixed display scale, on whole-pixel coordinates.
            Color.red
                .frame(width: 1 / SnapshotMatrix.displayScale, height: 1 / SnapshotMatrix.displayScale)
                .offset(x: 2, y: 2)
        }
        let prepared = SnapshotMatrix.prepare(changed, .light, width: SnapshotMatrix.width, height: nil)
        let failure = try XCTUnwrap(
            verifySnapshot(
                of: prepared.view,
                as: prepared.strategy,
                named: "card-light",
                record: .never,
                file: #filePath,
                testName: "testSampleCard"
            ),
            "a one-pixel change matched the reference of testSampleCard"
        )
        XCTAssertTrue(failure.contains("does not match reference"), failure)
    }
}

/// A test-only card in the shape of the app's list rows: an SF Symbol, a title that wraps, a secondary line and a
/// trailing badge, all in Dynamic Type text styles and system colours.
private struct SampleCard: View {
    let title: String
    let subtitle: String
    let badge: String

    static let long = SampleCard(
        title: SnapshotFixtures.longSubjectName,
        subtitle: SnapshotFixtures.longPersonName,
        badge: "92"
    )

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "graduationcap")
                .font(.title2)
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: title)
                    .font(.headline)
                Text(verbatim: subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Text(verbatim: badge)
                .font(.subheadline.monospacedDigit().weight(.semibold))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(.tint.opacity(0.15), in: Capsule())
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
        .padding(16)
    }
}
