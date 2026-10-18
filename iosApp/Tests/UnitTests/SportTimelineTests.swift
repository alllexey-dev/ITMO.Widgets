@testable import ITMOWidgets
import WidgetKit
import XCTest

/// The sport widget's timeline: Swift decodes the shared reference fixture in the App Group envelope, as
/// `SportWidgetWriter` stores it, and WidgetKit's entries switch at its entries.
final class SportTimelineTests: XCTestCase {
    private let session = SessionFile(isu: 123_456, demo: false, alertsAllowed: false)

    func testTheReferenceFixtureDecodes() throws {
        let timeline = try fixture()

        XCTAssertEqual(timeline.formatVersion, 1)
        XCTAssertEqual(timeline.generatedAt, instant("2026-09-07T09:22:00Z"))
        XCTAssertEqual(timeline.validUntil, instant("2026-09-13T21:00:00Z"))
        XCTAssertEqual(timeline.entries.count, 2)

        let queue = try XCTUnwrap(timeline.entries.first).snapshot
        XCTAssertEqual(queue.kind, .content)
        XCTAssertEqual([queue.visits, queue.bonus, queue.total, queue.remaining], [15, 15, 30, 70])
        XCTAssertFalse(queue.done)
        let items = try XCTUnwrap(queue.items)
        XCTAssertEqual(items.map(\.day), [.tomorrow, .later])
        XCTAssertEqual(items.map(\.status), [.queue, .booked])
        XCTAssertEqual([items[0].position, items[0].total], [2, 5])
        XCTAssertEqual(items[0].lessonId, 101)
        XCTAssertEqual(items[1].date, "2026-09-10")

        let done = try XCTUnwrap(timeline.entries.last).snapshot
        XCTAssertTrue(done.done)
        XCTAssertEqual(done.total, 104)
        XCTAssertEqual(done.items, [])
    }

    func testTheTextsReadAsTheSportTab() throws {
        let items = try XCTUnwrap(try fixture().entries.first?.snapshot.items)
        XCTAssertEqual(SportWidgetText.when(items[0]), "Завтра 15:20")
        XCTAssertEqual(SportWidgetText.when(items[1]), "Чт 15:20")
        XCTAssertEqual(SportWidgetText.status(items[0]), "очередь 2 из 5")
        XCTAssertEqual(SportWidgetText.status(items[1]), "записаны")
        XCTAssertEqual(SportWidgetText.place(items[0]), "Плавание, улица Ломоносова, 9", "the fixture is the raw provider data")
    }

    func testABookingRowOpensItsLessonInSport() {
        XCTAssertEqual(WidgetRoute.sportLesson(id: 101, predicted: false).absoluteString, "itmowidgets://route/sport?lesson=101")
        XCTAssertEqual(WidgetRoute.sport.absoluteString, "itmowidgets://route/sport")
    }

    func testWidgetKitSwitchesAtEveryEntryAndEndsUnavailable() throws {
        let timeline = try fixture()
        let now = instant("2026-09-07T12:00:00Z")

        let entries = SportWidgetTimeline.entries(now: now, session: session, timeline: timeline)

        XCTAssertEqual(entries.map(\.date), [now, instant("2026-09-07T21:00:00Z"), timeline.validUntil])
        XCTAssertEqual(entries.map(\.content), [
            .snapshot(timeline.entries[0].snapshot), .snapshot(timeline.entries[1].snapshot), .placeholder(.unavailable),
        ])
        XCTAssertEqual(SportWidgetTimeline.timeline(now: now, session: session, timeline: timeline).policy, .after(timeline.validUntil))
    }

    func testTheSessionAndTheScoreStatesArePlaceholders() throws {
        let timeline = try fixture()
        XCTAssertEqual(SportWidgetTimeline.entries(now: Date(), session: nil, timeline: timeline).map(\.content), [.placeholder(.signedOut)])
        let demo = SessionFile(isu: nil, demo: true, alertsAllowed: false)
        XCTAssertEqual(SportWidgetTimeline.entries(now: Date(), session: demo, timeline: timeline).map(\.content), [.placeholder(.demo)])
        XCTAssertEqual(SportWidgetTimeline.entries(now: Date(), session: session, timeline: nil).map(\.content), [.placeholder(.unavailable)])
        let error = SportWidgetSnapshot(kind: .error, visits: 0, bonus: 0, items: nil)
        XCTAssertEqual(SportWidgetTimeline.content(of: error), .placeholder(.sportError))
    }

    func testANewerTimelineIsRejected() throws {
        let fixture = try fixtureText()
        XCTAssertNil(SportTimeline.decode(envelope(fixture, version: 2)))
        XCTAssertNil(SportTimeline.decode(envelope(fixture.replacingOccurrences(of: #""version": 1,"#, with: #""version": 2,"#))))
    }

    private func fixture() throws -> SportTimeline {
        try XCTUnwrap(SportTimeline.decode(envelope(try fixtureText())))
    }

    private func fixtureText() throws -> String {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "sport-widget-timeline-v1", withExtension: "json"))
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func envelope(_ value: String, version: Int = 1) -> Data {
        Data(#"{"version": \#(version), "value": \#(value)}"#.utf8)
    }

    private func instant(_ text: String) -> Date {
        AppGroupSnapshot.parseInstant(text)!
    }
}
