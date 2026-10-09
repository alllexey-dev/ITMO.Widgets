@testable import ITMOWidgets
import XCTest

/// The App Group readers the widget extension uses (IO-10a): a missing, corrupt or newer file is nil, never a crash,
/// so the widget shows its placeholder state; a file of this version reads back as written.
final class SnapshotReaderTests: XCTestCase {
    private var container: URL!

    override func setUpWithError() throws {
        container = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: container, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: container)
    }

    // MARK: Missing files

    func testMissingFilesReadAsNothing() {
        XCTAssertNil(SessionFile.read(fromContainer: container))
        XCTAssertNil(QrPassSnapshot.read(fromContainer: container))
        XCTAssertNil(QrWidgetReveal.read(fromContainer: container))
        XCTAssertNil(LessonTimeline.read(fromContainer: container))
    }

    func testNoContainerReadsAsNothing() {
        XCTAssertNil(SessionFile.read(fromContainer: nil))
        XCTAssertNil(QrPassSnapshot.read(fromContainer: nil))
        XCTAssertNil(QrWidgetReveal.read(fromContainer: nil))
        XCTAssertNil(LessonTimeline.read(fromContainer: nil))
    }

    // MARK: Corrupt files

    func testCorruptFilesReadAsNothing() throws {
        for corrupt in ["", "{", "[]", #"{"version": 1}"#, #"{"version": "1", "value": {}}"#, #"{"value": {}}"#] {
            try write(corrupt, to: SessionFile.fileName)
            try write(corrupt, to: QrPassSnapshot.fileName)
            try write(corrupt, to: QrWidgetReveal.fileName)
            try write(corrupt, to: LessonTimeline.fileName)
            XCTAssertNil(SessionFile.read(fromContainer: container), corrupt)
            XCTAssertNil(QrPassSnapshot.read(fromContainer: container), corrupt)
            XCTAssertNil(QrWidgetReveal.read(fromContainer: container), corrupt)
            XCTAssertNil(LessonTimeline.read(fromContainer: container), corrupt)
        }
    }

    func testAValueOfTheWrongShapeIsRejected() throws {
        try write(#"{"version": 1, "value": {"isu": 1, "demo": "yes", "alertsAllowed": false}}"#, to: SessionFile.fileName)
        XCTAssertNil(SessionFile.read(fromContainer: container))

        try write(#"{"version": 1, "value": {"revealedUntil": "tomorrow"}}"#, to: QrWidgetReveal.fileName)
        XCTAssertNil(QrWidgetReveal.read(fromContainer: container))
    }

    // MARK: Future versions

    func testAFutureVersionIsRejected() throws {
        try write(#"{"version": 2, "value": {"isu": 1, "demo": false, "alertsAllowed": true}}"#, to: SessionFile.fileName)
        try write(#"{"version": 2, "value": {"revealedUntil": "2026-09-01T08:00:00Z"}}"#, to: QrWidgetReveal.fileName)
        try write(#"{"version": 2, "value": {"rows": 21}}"#, to: QrPassSnapshot.fileName)
        try write(#"{"version": 2, "value": {"entries": []}}"#, to: LessonTimeline.fileName)

        XCTAssertNil(SessionFile.read(fromContainer: container))
        XCTAssertNil(QrWidgetReveal.read(fromContainer: container))
        XCTAssertNil(QrPassSnapshot.read(fromContainer: container))
        XCTAssertNil(LessonTimeline.read(fromContainer: container))
    }

    // MARK: Files of this version

    func testTheSessionFileReads() throws {
        // What `SessionSnapshotWriter` writes: kotlinx.serialization writes `null` for a missing ISU.
        try write(#"{"version": 1, "value": {"isu": null, "demo": true, "alertsAllowed": false}}"#, to: "session-v1.json")
        XCTAssertEqual(SessionFile.read(fromContainer: container), SessionFile(isu: nil, demo: true, alertsAllowed: false))

        try write(#"{"version": 1, "value": {"isu": 123456, "demo": false, "alertsAllowed": true}}"#, to: "session-v1.json")
        XCTAssertEqual(SessionFile.read(fromContainer: container), SessionFile(isu: 123_456, demo: false, alertsAllowed: true))
    }

    func testTheRevealRoundTrips() throws {
        let reveal = QrWidgetReveal.startingAt(Date(timeIntervalSince1970: 1_788_249_630.25))
        XCTAssertEqual(reveal.revealedUntil.timeIntervalSince1970, 1_788_249_660.25, accuracy: 0.001, "30 s, Android's auto-hide")

        try reveal.write(toContainer: container)

        let read = try XCTUnwrap(QrWidgetReveal.read(fromContainer: container))
        XCTAssertEqual(read.revealedUntil.timeIntervalSince1970, reveal.revealedUntil.timeIntervalSince1970, accuracy: 0.001)
        let text = try String(contentsOf: container.appendingPathComponent("qr-widget-v1.json"), encoding: .utf8)
        XCTAssertTrue(text.contains(#""version":1"#), text)
        XCTAssertTrue(text.contains(#""revealedUntil":"2026-09-01T08:01:00.250Z""#), text)
    }

    func testTheScheduleTimelineReadsFromTheContainer() throws {
        let fixture = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "schedule-widget-timeline-v1", withExtension: "json"))
        let value = try String(contentsOf: fixture, encoding: .utf8)
        try write(#"{"version": 1, "value": \#(value)}"#, to: "schedule-timeline-v1.json")

        let timeline = try XCTUnwrap(LessonTimeline.read(fromContainer: container))
        XCTAssertEqual(timeline.entries.count, 10)
    }

    // MARK: Container

    func testTheGroupCandidatesAreTheOwnGroupThenAltStoresWithoutRepeats() throws {
        let bundle = try infoBundle([
            "AppGroupID": "group.example.own",
            "ALTAppGroups": ["group.example.alt", "group.example.own", ""],
        ])
        XCTAssertEqual(AppGroupSnapshot.groupCandidates(of: bundle), ["group.example.own", "group.example.alt"])
        XCTAssertEqual(AppGroupSnapshot.groupCandidates(of: try infoBundle([:])), [])
    }

    private func write(_ text: String, to name: String) throws {
        try Data(text.utf8).write(to: container.appendingPathComponent(name))
    }

    /// A bundle with only an Info.plist of `info`.
    private func infoBundle(_ info: [String: Any]) throws -> Bundle {
        let url = container.appendingPathComponent("\(UUID().uuidString).bundle")
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        let plist = try PropertyListSerialization.data(fromPropertyList: info, format: .xml, options: 0)
        try plist.write(to: url.appendingPathComponent("Info.plist"))
        return try XCTUnwrap(Bundle(url: url))
    }
}
