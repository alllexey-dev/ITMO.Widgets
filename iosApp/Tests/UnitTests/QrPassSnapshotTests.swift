@testable import ITMOWidgets
import XCTest

/// The QR widget's App Group file (IO-21) as Swift reads it: `Fixtures/qr-pass-v1.json` is what the shared
/// `QrPassSnapshotWriter` writes for the demo pass (`QrPassSnapshotWriterTest` in `:shared:feature-qr` fails while
/// the two differ), so both sides decode one file.
final class QrPassSnapshotTests: XCTestCase {
    func testTheWritersFixtureDecodes() throws {
        let snapshot = try XCTUnwrap(QrPassSnapshot.decode(fixture()))

        // 2026-09-01T08:00:30.250Z and 09:00:00Z: Kotlin writes a fraction only when there is one.
        XCTAssertEqual(snapshot.generatedAt.timeIntervalSince1970, 1_788_249_630.25, accuracy: 0.001)
        XCTAssertEqual(snapshot.expiresAt.timeIntervalSince1970, 1_788_253_200, accuracy: 0.001)
        XCTAssertTrue(snapshot.demo)
        XCTAssertEqual(snapshot.matrix.count, 21, "a version 1 code")
        XCTAssertTrue(snapshot.matrix.allSatisfy { $0.count == 21 })
        // The finder pattern in the top left corner: a dark ring, a light ring, a dark 3 x 3 centre.
        XCTAssertEqual(snapshot.matrix[0].prefix(7), [true, true, true, true, true, true, true])
        XCTAssertEqual(snapshot.matrix[1].prefix(7), [true, false, false, false, false, false, true])
        XCTAssertEqual(snapshot.matrix[3].prefix(7), [true, false, true, true, true, false, true])
        // The writer's defaults for nothing stored: Android's.
        XCTAssertEqual(snapshot.appearance, .standard)
    }

    func testTheWidgetOptionsFollowTheFile() throws {
        let read = try XCTUnwrap(QrPassSnapshot.decode(fixture(with: [
            "spoiler": false, "dynamicColors": false, "animation": "NONE",
        ])))
        XCTAssertEqual(read.appearance, QrWidgetAppearance(spoiler: false, dynamicColors: false, animation: .none))
        XCTAssertEqual(read.matrix, try XCTUnwrap(QrPassSnapshot.decode(fixture())).matrix)

        let fade = try XCTUnwrap(QrPassSnapshot.decode(fixture(with: ["animation": "FADE"])))
        XCTAssertEqual(fade.appearance.animation, .fade)
    }

    func testAbsentOrUnknownWidgetOptionsAreAndroidsDefaults() throws {
        let absent = try XCTUnwrap(QrPassSnapshot.decode(fixture(with: [
            "spoiler": nil, "dynamicColors": nil, "animation": nil,
        ])))
        XCTAssertEqual(absent.appearance, .standard, "a file without the options, as builds before IO-FIX-QRW wrote")

        let unknown = try XCTUnwrap(QrPassSnapshot.decode(fixture(with: ["animation": "SPIRAL"])))
        XCTAssertEqual(unknown.appearance.animation, .circle, "a newer animation falls back to the default")
    }

    func testTheFileNameIsTheVersionedSnapshotName() {
        XCTAssertEqual(QrPassSnapshot.fileName, "qr-pass-v1.json")
    }

    func testAHigherVersionIsRejectedEvenWithAnotherShape() throws {
        let newer = Data(#"{"version": 2, "value": {"rows": 21}}"#.utf8)
        XCTAssertNil(QrPassSnapshot.decode(newer))

        var envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: fixture()) as? [String: Any])
        envelope["version"] = 2
        XCTAssertNil(QrPassSnapshot.decode(try JSONSerialization.data(withJSONObject: envelope)))
    }

    func testACorruptOrNonSquareFileIsRejected() throws {
        XCTAssertNil(QrPassSnapshot.decode(Data("{".utf8)))
        XCTAssertNil(QrPassSnapshot.decode(Data(#"{"version": 1}"#.utf8)))

        var envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: fixture()) as? [String: Any])
        var value = try XCTUnwrap(envelope["value"] as? [String: Any])
        value["matrix"] = ["101", "010"]
        envelope["value"] = value
        XCTAssertNil(QrPassSnapshot.decode(try JSONSerialization.data(withJSONObject: envelope)))
    }

    func testReadsTheFileFromAContainerAndNothingWithoutIt() throws {
        let container = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: container, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: container) }
        XCTAssertNil(QrPassSnapshot.read(fromContainer: container))

        try fixture().write(to: container.appendingPathComponent(QrPassSnapshot.fileName))

        XCTAssertEqual(QrPassSnapshot.read(fromContainer: container), QrPassSnapshot.decode(try fixture()))
    }

    private func fixture() throws -> Data {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "qr-pass-v1", withExtension: "json"))
        return try Data(contentsOf: url)
    }

    /// The fixture with `fields` of its value replaced; a nil removes the field.
    private func fixture(with fields: [String: Any?]) throws -> Data {
        var envelope = try XCTUnwrap(JSONSerialization.jsonObject(with: fixture()) as? [String: Any])
        var value = try XCTUnwrap(envelope["value"] as? [String: Any])
        for (key, field) in fields {
            value[key] = field
        }
        envelope["value"] = value
        return try JSONSerialization.data(withJSONObject: envelope)
    }
}
