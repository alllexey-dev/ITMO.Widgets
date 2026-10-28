@testable import ITMOWidgets
import XCTest

/// The cards widget on the shared reference fixture (the sample Monday 7 September at 12:22, Moscow): its pages, the
/// page a tap turns to and when it returns, and how many cards fit.
final class CardsWidgetTests: XCTestCase {
    private let session = SessionFile(isu: 123_456, demo: false, alertsAllowed: false)

    func testThePagesAreTodayThenTheSixDaysAfterIt() throws {
        let snapshot = try XCTUnwrap(try fixture().entries.first?.snapshot)

        XCTAssertEqual(snapshot.cardsPages.compactMap(\.date), (7...13).map { "2026-09-\(String(format: "%02d", $0))" })
        XCTAssertEqual(snapshot.cardsFirstOffset, 0)
    }

    func testOnceTodayIsOverThePagesStartTomorrow() throws {
        let evening = try XCTUnwrap(try fixture().entry(at: instant("2026-09-07T16:40:00Z"))).snapshot

        XCTAssertEqual(evening.cardsPages.first?.date, "2026-09-08")
        XCTAssertEqual(evening.cardsPages.count, 6)
        XCTAssertEqual(evening.cardsFirstOffset, 1)
    }

    func testATurnedPageHoldsFiveMinutesThenTheFirstPageReturns() throws {
        let timeline = try fixture()
        let now = timeline.generatedAt
        let page = CardsWidgetPage.startingAt(now, page: 2, base: "2026-09-07")

        let entries = CardsWidgetTimeline.entries(now: now, session: session, timeline: timeline, page: page)

        XCTAssertEqual(entries.first?.cardsPage, 2)
        let reset = try XCTUnwrap(entries.first { $0.date == page.until })
        XCTAssertEqual(reset.cardsPage, 0)
        XCTAssertEqual(entries.map(\.date), entries.map(\.date).sorted())
        XCTAssertTrue(entries.filter { $0.date >= page.until }.allSatisfy { $0.cardsPage == 0 })
    }

    func testAPageTurnedOnAnotherFirstDayShowsTheFirstPage() throws {
        let timeline = try fixture()
        let now = timeline.generatedAt
        let page = CardsWidgetPage.startingAt(now, page: 2, base: "2026-09-06")

        let entries = CardsWidgetTimeline.entries(now: now, session: session, timeline: timeline, page: page)

        XCTAssertTrue(entries.allSatisfy { $0.cardsPage == 0 })
    }

    func testTheCardsThatDoNotFitFoldIntoTheLastRow() {
        XCTAssertEqual(CardsLayout.fit([60, 60, 60], more: 36, available: 180), 3)
        XCTAssertEqual(CardsLayout.fit([86, 60, 60, 60], more: 36, available: 190), 2)
        XCTAssertEqual(CardsLayout.fit([86, 60], more: 36, available: 90), 1)
        XCTAssertEqual(CardsLayout.fit([], more: 36, available: 90), 0)
    }

    func testTheSubtitleCountsWhatIsLeft() throws {
        let snapshot = try XCTUnwrap(try fixture().entries.first?.snapshot)
        let pages = snapshot.cardsPages

        XCTAssertEqual(CardsText.title(date: "2026-09-07", offset: 0), "Сегодня")
        XCTAssertEqual(CardsText.title(date: "2026-09-08", offset: 1), "Завтра")
        XCTAssertEqual(CardsText.title(date: "2026-09-09", offset: 2), "Среда")
        XCTAssertEqual(CardsText.subtitle(day: pages[0], offset: 0), "7 сентября · ещё 2 пары и спорт")
        XCTAssertEqual(CardsText.subtitle(day: pages[1], offset: 1), "8 сентября · 2 пары и спорт")
        XCTAssertEqual(CardsText.subtitle(day: pages[2], offset: 2), "9 сентября · пар нет")
    }

    private func fixture() throws -> ScheduleTimeline {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "schedule-widget-timeline-v2", withExtension: "json"))
        let value = try String(contentsOf: url, encoding: .utf8)
        return try XCTUnwrap(ScheduleTimeline.decode(Data(#"{"version": 2, "value": \#(value)}"#.utf8)))
    }

    private func instant(_ text: String) -> Date {
        AppGroupSnapshot.parseInstant(text)!
    }
}
