@testable import ITMOWidgets
import WidgetKit
import XCTest

/// The schedule widgets' timeline: Swift decodes the shared reference fixture as the shared writer leaves it in the
/// App Group (the fixture in the `{"version", "value"}` envelope, `ScheduleTimelineWriterTest`), picks for every
/// sampled instant the entry Kotlin's `ScheduleWidgetTimeline.entryAt` picks, moves the day widget's `now` at every
/// tick, and WidgetKit's entries switch exactly at those instants.
final class ScheduleTimelineTests: XCTestCase {
    private let session = SessionFile(isu: 123_456, demo: false, alertsAllowed: false)

    // MARK: Decoding

    func testTheReferenceFixtureDecodes() throws {
        let timeline = try fixture()

        XCTAssertEqual(timeline.formatVersion, 2)
        XCTAssertEqual(timeline.generatedAt, instant("2026-09-07T09:22:00Z"))
        XCTAssertEqual(timeline.validUntil, instant("2026-09-08T21:00:00Z"))
        XCTAssertEqual(timeline.entries.count, 13)
        XCTAssertEqual(timeline.entries.first?.validFrom, timeline.generatedAt)
        XCTAssertNil(timeline.palette, "the reference fixture has none: the brand scheme")

        let first = try XCTUnwrap(timeline.entries.first)
        XCTAssertEqual(first.snapshot.compactTextSize, .normal)
        XCTAssertEqual(first.snapshot.fullTextSize, .large)
        XCTAssertEqual(first.nowTicks.first, NowTick(at: instant("2026-09-07T09:25:00Z"), minute: 745))

        let next = first.snapshot.next
        XCTAssertEqual(next.kind, .content)
        XCTAssertEqual(next.moment, .now)
        XCTAssertEqual(next.until, instant("2026-09-07T10:10:00Z"))
        XCTAssertEqual(next.since, instant("2026-09-07T08:40:00Z"))
        XCTAssertEqual(next.lastEnd, "18:30")
        XCTAssertEqual(next.rest.map(\.subject), ["Английский язык", "Плавание"])
        XCTAssertTrue(next.rest[0].online)
        let lesson = try XCTUnwrap(next.lesson)
        XCTAssertEqual(lesson.subject, "Программирование")
        XCTAssertEqual([lesson.startMinute, lesson.endMinute], [700, 790])
        XCTAssertEqual(lesson.state, .now)
        XCTAssertEqual(lesson.room, "1506")
        XCTAssertEqual(lesson.lesson?.text?.contains(#""pairId":3"#), true)

        let day = first.snapshot.day
        XCTAssertEqual(day.date, "2026-09-07")
        XCTAssertEqual(day.summary, DaySummary(kind: .remaining, lessons: 2, sport: true, from: nil, to: "18:30"))
        XCTAssertEqual(day.lessons.map(\.state), [.done, .done, .now, .upcoming, .upcoming])
        XCTAssertTrue(day.lessons[1].changed)
        XCTAssertEqual(day.gaps, [WidgetGap(startMinute: 790, endMinute: 920)])
        XCTAssertEqual([day.axisStart, day.axisEnd], [480, 1115])
        XCTAssertEqual(day.now, 742)
    }

    func testTomorrowsQueueAndTheEmptyEndDecode() throws {
        let timeline = try fixture()
        let evening = try XCTUnwrap(timeline.entry(at: instant("2026-09-07T16:40:00Z"))).snapshot
        XCTAssertEqual(evening.next.moment, .tomorrow)
        XCTAssertNil(evening.next.until)
        XCTAssertTrue(evening.day.tomorrow)
        XCTAssertNil(evening.day.now)
        XCTAssertEqual(evening.day.lessons.last?.queued, true)
        let queue = try XCTUnwrap(evening.day.lessons.last)
        XCTAssertEqual([queue.queuePosition, queue.queueTotal], [2, 5])
        XCTAssertEqual(ScheduleWidgetText.queue(of: queue), "очередь 2 из 5")

        let over = try XCTUnwrap(timeline.entries.last).snapshot
        XCTAssertEqual(over.next.kind, .empty)
        XCTAssertEqual(over.day.kind, .empty)
    }

    func testAnUnknownFieldKeepsTheFileReadableAndANewerTimelineIsRejected() throws {
        let fixture = try fixtureText()
        let extended = fixture.replacingOccurrences(of: #""version": 2,"#, with: #""version": 2, "addedLater": true,"#)
        XCTAssertNotNil(ScheduleTimeline.decode(envelope(extended)))

        let newer = fixture.replacingOccurrences(of: #""version": 2,"#, with: #""version": 3,"#)
        XCTAssertNil(ScheduleTimeline.decode(envelope(newer)), "a newer value inside a version 2 envelope")
        XCTAssertNil(ScheduleTimeline.decode(envelope(fixture, version: 3)))
    }

    func testATimelineWithoutEntriesIsRejected() {
        let empty = #"{"version": 2, "generatedAt": "2026-09-07T09:22:00Z", "validUntil": "2026-09-08T21:00:00Z", "entries": []}"#
        XCTAssertNil(ScheduleTimeline.decode(envelope(empty)))
    }

    // MARK: The snapshot of an instant

    /// The entries Kotlin's `ScheduleWidgetTimelineTest` checks: lesson starts and ends, the gap, midnight, the end.
    func testTheEntryOfAnInstantIsKotlinsEntryAt() throws {
        let timeline = try fixture()
        let expectations: [(String, String?)] = [
            ("2026-09-07T09:22:00Z", "NOW Программирование"),
            ("2026-09-07T10:09:59.999Z", "NOW Программирование"),
            ("2026-09-07T10:10:00Z", "NEXT Английский язык window"),
            ("2026-09-07T12:20:00Z", "NOW Английский язык"),
            ("2026-09-07T15:30:00Z", "TOMORROW Алгоритмы и структуры данных"),
            ("2026-09-07T21:00:00Z", "FIRST Алгоритмы и структуры данных"),
            ("2026-09-08T12:20:00Z", "EMPTY"),
            ("2026-09-08T21:00:00Z", nil),
            ("2026-09-07T09:21:59.999Z", nil),
        ]
        for (time, expected) in expectations {
            XCTAssertEqual(timeline.entry(at: instant(time)).map { describe($0.snapshot.next) }, expected, time)
        }
    }

    /// Within an entry the `now` minute is the last tick's, the entry's own before the first tick.
    func testTheNowLineMovesAtEveryTick() throws {
        let timeline = try fixture()
        XCTAssertEqual(timeline.snapshot(at: instant("2026-09-07T09:24:59Z"))?.day.now, 742)
        XCTAssertEqual(timeline.snapshot(at: instant("2026-09-07T09:25:00Z"))?.day.now, 745)
        XCTAssertEqual(timeline.snapshot(at: instant("2026-09-07T10:09:00Z"))?.day.now, 785)
        XCTAssertEqual(timeline.snapshot(at: instant("2026-09-07T10:10:00Z"))?.day.now, 790)
        XCTAssertNil(timeline.snapshot(at: instant("2026-09-08T21:00:00Z")))

        let moments = timeline.moments
        XCTAssertEqual(moments.count, timeline.entries.count + timeline.entries.map(\.nowTicks.count).reduce(0, +))
        XCTAssertEqual(moments.map(\.date), moments.map(\.date).sorted())
    }

    /// For every 37 s of the timeline and both sides of every moment, the WidgetKit entry on screen holds the snapshot
    /// of that instant, from any `now` the widget asked at.
    func testWidgetKitShowsTheSnapshotOfEverySampledInstant() throws {
        let timeline = try fixture()
        let switches = timeline.moments.map(\.date) + [timeline.validUntil]
        let samples = stride(from: timeline.generatedAt, to: timeline.validUntil, by: 37).map { $0 }
            + switches.flatMap { [$0, $0.addingTimeInterval(-0.001)] }
        let nows = [timeline.generatedAt, instant("2026-09-07T10:12:30Z"), instant("2026-09-07T23:59:00Z")]

        for now in nows {
            let entries = ScheduleWidgetTimeline.entries(now: now, session: session, timeline: timeline)
            XCTAssertEqual(entries.map(\.date), entries.map(\.date).sorted())
            for sample in samples where sample >= now {
                let shown = try XCTUnwrap(entries.last { $0.date <= sample })
                let expected: ScheduleWidgetContent = timeline.snapshot(at: sample).map { .snapshot($0) }
                    ?? .placeholder(.unavailable)
                XCTAssertEqual(shown.content, expected, "\(sample)")
            }
        }
    }

    // MARK: WidgetKit timeline

    func testEveryLaterMomentIsAnEntryAndTheTimelineEndsUnavailable() throws {
        let timeline = try fixture()
        let now = instant("2026-09-07T10:12:30Z")

        let entries = ScheduleWidgetTimeline.entries(now: now, session: session, timeline: timeline)

        XCTAssertEqual(
            entries.map(\.date),
            [now] + timeline.moments.map(\.date).filter { $0 > now } + [timeline.validUntil]
        )
        XCTAssertEqual(entries.first?.content, .snapshot(timeline.entries[1].snapshot))
        XCTAssertEqual(entries.dropFirst().first?.content, .snapshot(timeline.entries[1].snapshot.withNow(795)))
        XCTAssertEqual(entries.last?.content, .placeholder(.unavailable))
        XCTAssertTrue(entries.allSatisfy { $0.clock == .live })
        XCTAssertEqual(
            ScheduleWidgetTimeline.timeline(now: now, session: session, timeline: timeline).policy,
            .after(timeline.validUntil)
        )
    }

    func testEveryEntryCarriesTheTimelinesPalette() throws {
        let roles = #"{"surface": 16055288, "surfaceContainer": 15331309, "onSurface": 1449244, "onSurfaceVariant": 4147527, "outlineVariant": 12503494, "primary": 27232}"#
        let themed = try fixtureText().replacingOccurrences(
            of: #""version": 2,"#, with: #""version": 2, "palette": {"light": \#(roles), "dark": \#(roles)},"#
        )
        let timeline = try XCTUnwrap(ScheduleTimeline.decode(envelope(themed)))
        XCTAssertEqual(timeline.palette?.light.primary, 0x006A60)
        XCTAssertEqual(timeline.palette?.light.primaryContainer, 15_331_309, "an older file: the container role")
        XCTAssertEqual(timeline.palette?.light.tertiary, 0x006A60, "an older file: the primary role")

        let entries = ScheduleWidgetTimeline.entries(now: instant("2026-09-07T10:12:30Z"), session: session, timeline: timeline)
        XCTAssertTrue(entries.allSatisfy { $0.palette == timeline.palette })
    }

    func testWithoutASessionTheWidgetsAreSignedOut() throws {
        XCTAssertEqual(
            ScheduleWidgetTimeline.entries(now: Date(), session: nil, timeline: try fixture()).map(\.content),
            [.placeholder(.signedOut)]
        )
    }

    func testTheDemoSessionShowsTheDemoState() throws {
        let demo = SessionFile(isu: nil, demo: true, alertsAllowed: false)
        XCTAssertEqual(
            ScheduleWidgetTimeline.entries(now: Date(), session: demo, timeline: try fixture()).map(\.content),
            [.placeholder(.demo)]
        )
    }

    func testWithoutAUsableTimelineTheWidgetsAskAgainWithinAnHour() throws {
        let stale = instant("2026-09-08T21:00:00Z")
        for timeline in [nil, try fixture()] {
            let result = ScheduleWidgetTimeline.timeline(now: stale, session: session, timeline: timeline)
            XCTAssertEqual(result.entries.map(\.content), [.placeholder(.unavailable)])
            XCTAssertEqual(result.policy, .after(stale.addingTimeInterval(3600)))
        }
    }

    // MARK: Fixture

    /// The reference fixture (a test resource by path, project.yml) inside the App Group envelope, as the writer stores it.
    private func fixture() throws -> ScheduleTimeline {
        try XCTUnwrap(ScheduleTimeline.decode(envelope(try fixtureText())))
    }

    private func fixtureText() throws -> String {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "schedule-widget-timeline-v2", withExtension: "json"))
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func envelope(_ value: String, version: Int = 2) -> Data {
        Data(#"{"version": \#(version), "value": \#(value)}"#.utf8)
    }

    private func instant(_ text: String) -> Date {
        AppGroupSnapshot.parseInstant(text)!
    }

    private func describe(_ content: NextLessonContent) -> String {
        guard let moment = content.moment, let lesson = content.lesson else { return content.kind.rawValue }
        return [moment.rawValue, lesson.subject, content.window ? "window" : nil].compactMap { $0 }.joined(separator: " ")
    }
}

/// The texts, the routes and the scale the schedule widgets build from a snapshot.
final class ScheduleWidgetLayoutTests: XCTestCase {
    func testTheDayTitleAndSummaryReadAsTheDesign() {
        XCTAssertEqual(ScheduleWidgetText.dayTitle(day(date: "2026-09-07", tomorrow: false)), "Понедельник, 7 сентября")
        XCTAssertEqual(ScheduleWidgetText.dayTitle(day(date: "2026-09-08", tomorrow: true)), "Завтра, 8 сентября")
        XCTAssertEqual(
            ScheduleWidgetText.summary(DaySummary(kind: .remaining, lessons: 2, sport: true, from: nil, to: "18:30")),
            "ещё 2 пары и спорт до 18:30"
        )
        XCTAssertEqual(
            ScheduleWidgetText.summary(DaySummary(kind: .planned, lessons: 4, sport: true, from: "08:20", to: "18:30")),
            "4 пары и спорт, 08:20–18:30"
        )
        XCTAssertEqual(
            ScheduleWidgetText.summary(DaySummary(kind: .remaining, lessons: 0, sport: true, from: nil, to: "18:30")),
            "ещё спорт до 18:30"
        )
        XCTAssertEqual(ScheduleWidgetText.summary(DaySummary(kind: .over, lessons: 0, sport: false)), "на сегодня всё")
        XCTAssertEqual(ScheduleWidgetText.summary(DaySummary(kind: .noneToday, lessons: 0, sport: false)), "сегодня пар нет")
    }

    func testTheHeadlinesAndTheLockScreenLine() {
        let lesson = lesson(room: "1506", building: "Кронва")
        XCTAssertEqual(ScheduleWidgetText.headline(.now, lesson: lesson), "Сейчас, до 13:10")
        XCTAssertEqual(ScheduleWidgetText.headline(.first, lesson: lesson), "Первая пара в 11:40")
        XCTAssertEqual(ScheduleWidgetText.headline(.next, lesson: lesson), "Далее в 11:40")
        XCTAssertEqual(ScheduleWidgetText.headline(.tomorrow, lesson: lesson), "Завтра в 11:40")
        XCTAssertEqual(ScheduleWidgetText.inline(.now, lesson: lesson), "1506 до 13:10")
        XCTAssertEqual(ScheduleWidgetText.inline(.first, lesson: lesson), "1506 в 11:40")
        XCTAssertEqual(ScheduleWidgetText.place(of: lesson), "1506, Кронва")
        var online = lesson
        online.online = true
        XCTAssertEqual(ScheduleWidgetText.place(of: online), "онлайн")
        XCTAssertEqual(WidgetText.duration(minutes: 130), "2 ч 10 мин")
        XCTAssertEqual(WidgetText.duration(minutes: 48), "48 мин")
        XCTAssertEqual(WidgetText.clock(minute: 742), "12:22")
    }

    /// A lesson row holds its `LessonDetailsArgs` and the app reads it back into the lesson sheet; a queued sport
    /// row opens its lesson in the sport tab.
    func testATapOnALessonOpensItsSheetAndAQueueItsSportLesson() throws {
        var lesson = lesson(room: "1506", building: nil)
        lesson.lesson = .object(["pairId": .integer(3), "date": .string("2026-09-07"), "subjectName": .string("Физика")])
        let url = WidgetRoute.lesson(lesson)
        XCTAssertTrue(url.absoluteString.hasPrefix("itmowidgets://route/lesson?args="), url.absoluteString)
        let json = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value)
            .flatMap(WidgetRoute.data(base64URL:))
            .flatMap { String(data: $0, encoding: .utf8) }
        XCTAssertEqual(json, #"{"date":"2026-09-07","pairId":3,"subjectName":"Физика"}"#)

        lesson.sport = PendingSportTarget(lessonId: -42, isPrediction: true)
        XCTAssertEqual(WidgetRoute.lesson(lesson).absoluteString, "itmowidgets://route/sport?lesson=42&predicted=1")
        lesson.sport = nil
        lesson.lesson = nil
        XCTAssertEqual(WidgetRoute.lesson(lesson), WidgetRoute.today)
    }

    /// Time runs linearly on the axis, but no lesson is shorter than the minimum block.
    func testTheAxisKeepsEveryLessonReadable() {
        var content = day(date: "2026-09-07", tomorrow: false)
        content.axisStart = 480
        content.axisEnd = 1140
        content.lessons = [
            lesson(start: 500, end: 590),
            lesson(start: 600, end: 610),
            lesson(start: 1000, end: 1090),
        ]
        let axis = DayAxis(day: content, height: 300, minimumBlock: 26)
        XCTAssertEqual(axis.y(480), 0)
        XCTAssertEqual(axis.y(1140), 300, accuracy: 0.01)
        XCTAssertGreaterThanOrEqual(axis.y(610) - axis.y(600), 26 - 0.01)
        let long = axis.y(590) - axis.y(500)
        XCTAssertEqual(axis.y(1090) - axis.y(1000), long, accuracy: 0.01, "equal lengths, equal heights")
        XCTAssertLessThan(axis.y(700), axis.y(800))

        let uniform = DayAxis(day: content, height: 660, minimumBlock: 0)
        XCTAssertEqual(uniform.y(810), 330, accuracy: 0.01)
    }

    /// A short day does not stretch over the widget: an hour takes at most the maximum and the rest stays empty below.
    func testTheAxisKeepsAShortDayCompact() {
        var content = day(date: "2026-09-07", tomorrow: false)
        content.axisStart = 540
        content.axisEnd = 810
        content.lessons = [lesson(start: 590, end: 680), lesson(start: 690, end: 780)]
        let axis = DayAxis(day: content, height: 300, minimumBlock: 26, maximumDensity: 0.6)
        XCTAssertEqual(axis.y(810), 162, accuracy: 0.01)
        XCTAssertEqual(axis.y(680) - axis.y(590), 54, accuracy: 0.01)
    }

    private func day(date: String, tomorrow: Bool) -> DayContent {
        DayContent(kind: .content, date: date, tomorrow: tomorrow, summary: nil, lessons: [], gaps: [], axisStart: 0, axisEnd: 0, now: nil)
    }

    private func lesson(room: String? = nil, building: String? = nil, start: Int = 700, end: Int = 790) -> WidgetLesson {
        WidgetLesson(
            subject: "Физика", start: "11:40", end: "13:10", startMinute: start, endMinute: end, typeId: 1, state: .upcoming,
            room: room, building: building, teacher: nil, online: false, changed: false, cancelled: false, queued: false,
            queuePosition: 0, queueTotal: 0, lesson: nil, sport: nil
        )
    }
}
