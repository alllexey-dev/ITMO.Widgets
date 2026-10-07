@testable import ITMOWidgets
import WidgetKit
import XCTest

/// The schedule widgets' timeline (IO-10b): Swift decodes LS-3's reference fixture as the shared writer leaves it in
/// the App Group (the fixture in the `{"version", "value"}` envelope, `ScheduleTimelineWriterTest`), picks for every
/// sampled instant the entry Kotlin's `ScheduleWidgetTimeline.entryAt` picks, and WidgetKit's entries switch exactly
/// at the timeline's `validFrom` instants.
final class ScheduleTimelineTests: XCTestCase {
    private let session = SessionFile(isu: 123_456, demo: false, alertsAllowed: false)

    // MARK: Decoding

    func testTheReferenceFixtureDecodes() throws {
        let timeline = try fixture()

        XCTAssertEqual(timeline.formatVersion, 1)
        XCTAssertEqual(timeline.generatedAt, instant("2026-08-10T07:00:30.250Z"))
        XCTAssertEqual(timeline.validUntil, instant("2026-08-11T21:00:00Z"))
        XCTAssertEqual(timeline.entries.count, 10)
        XCTAssertEqual(timeline.entries.first?.validFrom, timeline.generatedAt)

        let first = try XCTUnwrap(timeline.entries.first?.snapshot)
        XCTAssertEqual(first.singleLessonStyle, .dot)
        XCTAssertEqual(first.lessonListStyle, .line)
        XCTAssertEqual(first.compactTextSize, .normal)
        XCTAssertEqual(first.fullTextSize, .large)
        XCTAssertEqual(
            first.singleLesson.lesson,
            WidgetLesson(
                subject: "Математика", start: "10:00", end: "11:30", typeId: 1, teacher: "Тестовый преподаватель",
                room: "1404", building: nil, state: .current, pendingStatus: nil
            )
        )
        XCTAssertEqual(first.lessonList.map(\.kind), [.header, .lesson, .lesson, .lesson, .end])
        XCTAssertEqual(first.lessonList[0].dateIso, "2026-08-10")
        XCTAssertEqual(first.lessonList[3].lesson?.pendingStatus, .predicted)
    }

    func testAnUnknownFieldKeepsTheFileReadableAndANewerTimelineIsRejected() throws {
        let fixture = try fixtureText()
        let extended = fixture.replacingOccurrences(of: #""version": 1,"#, with: #""version": 1, "addedLater": true,"#)
        XCTAssertNotNil(LessonTimeline.decode(envelope(extended)))

        let newer = fixture.replacingOccurrences(of: #""version": 1,"#, with: #""version": 2,"#)
        XCTAssertNil(LessonTimeline.decode(envelope(newer)), "a newer value inside a version 1 envelope")
        XCTAssertNil(LessonTimeline.decode(envelope(fixture, version: 2)))
    }

    func testATimelineWithoutEntriesIsRejected() {
        let empty = #"{"version": 1, "generatedAt": "2026-08-10T07:00:00Z", "validUntil": "2026-08-11T21:00:00Z", "entries": []}"#
        XCTAssertNil(LessonTimeline.decode(envelope(empty)))
    }

    // MARK: The entry of an instant

    /// The entries Kotlin's `ScheduleWidgetTimelineTest` checks against `ScheduleWidgetSelector.select` at the same
    /// boundaries: the early switch 15 minutes before a lesson ends, the pending row leaving at its start, midnight.
    func testTheEntryOfAnInstantIsKotlinsEntryAt() throws {
        let timeline = try fixture()
        let expectations: [(String, String?)] = [
            ("2026-08-10T07:00:30.250Z", "LESSON Математика CURRENT 2"),
            ("2026-08-10T08:14:59.999Z", "LESSON Математика CURRENT 2"),
            ("2026-08-10T08:15:00Z", "LESSON Физика UPCOMING 1"),
            ("2026-08-10T08:40:00Z", "LESSON Физика CURRENT 1"),
            ("2026-08-10T09:55:00Z", "LESSON Секция 7 UPCOMING 0 PREDICTED"),
            ("2026-08-10T12:29:59.999Z", "LESSON Секция 7 UPCOMING 0 PREDICTED"),
            ("2026-08-10T12:30:00Z", "NO_MORE_TODAY"),
            ("2026-08-10T21:00:00Z", "LESSON Алгоритмы UPCOMING 0"),
            ("2026-08-11T05:20:00Z", "LESSON Алгоритмы CURRENT 0"),
            ("2026-08-11T06:50:00Z", "NO_MORE_TODAY"),
            ("2026-08-11T20:59:59.999Z", "NO_MORE_TODAY"),
            ("2026-08-11T21:00:00Z", nil),
            ("2026-08-10T07:00:30.249Z", nil),
        ]
        for (time, expected) in expectations {
            XCTAssertEqual(timeline.entry(at: instant(time)).map { describe($0.snapshot.singleLesson) }, expected, time)
        }

        let afterPending = try XCTUnwrap(timeline.entry(at: instant("2026-08-10T12:30:00Z")))
        XCTAssertEqual(afterPending.snapshot.lessonList.first?.dateIso, "2026-08-11")
        XCTAssertEqual(afterPending.snapshot.lessonList.first?.tomorrow, true)
    }

    /// For every 37 s of the timeline and both sides of every switch, the WidgetKit entry on screen holds the same
    /// snapshot as the timeline's entry of that instant, from any `now` the widget asked at.
    func testWidgetKitShowsTheTimelinesEntryAtEverySampledInstant() throws {
        let timeline = try fixture()
        let switches = timeline.entries.map(\.validFrom) + [timeline.validUntil]
        let samples = stride(from: timeline.generatedAt, to: timeline.validUntil, by: 37).map { $0 }
            + switches.flatMap { [$0, $0.addingTimeInterval(-0.001)] }
        let nows = [timeline.generatedAt, instant("2026-08-10T08:20:00Z"), instant("2026-08-10T23:59:00Z")]

        for now in nows {
            let entries = LessonWidgetTimeline.entries(now: now, session: session, timeline: timeline)
            XCTAssertEqual(entries.map(\.date), entries.map(\.date).sorted())
            for sample in samples where sample >= now {
                let shown = try XCTUnwrap(entries.last { $0.date <= sample })
                XCTAssertEqual(shown.content, LessonWidgetTimeline.content(of: timeline.entry(at: sample)), "\(sample)")
            }
        }
    }

    // MARK: WidgetKit timeline

    func testEveryLaterSwitchIsAnEntryAndTheTimelineEndsUnavailable() throws {
        let timeline = try fixture()
        let now = instant("2026-08-10T08:20:00Z")

        let entries = LessonWidgetTimeline.entries(now: now, session: session, timeline: timeline)

        XCTAssertEqual(
            entries.map(\.date),
            [now] + timeline.entries.map(\.validFrom).filter { $0 > now } + [timeline.validUntil]
        )
        XCTAssertEqual(entries.first?.content, .snapshot(timeline.entries[1].snapshot))
        XCTAssertEqual(entries.last?.content, .unavailable)
        XCTAssertEqual(
            LessonWidgetTimeline.timeline(now: now, session: session, timeline: timeline).policy,
            .after(timeline.validUntil)
        )
    }

    func testWithoutASessionTheWidgetsAreSignedOut() throws {
        XCTAssertEqual(
            LessonWidgetTimeline.entries(now: Date(), session: nil, timeline: try fixture()).map(\.content),
            [.signedOut]
        )
    }

    func testTheDemoSessionShowsTheDemoState() throws {
        let demo = SessionFile(isu: nil, demo: true, alertsAllowed: false)
        XCTAssertEqual(
            LessonWidgetTimeline.entries(now: Date(), session: demo, timeline: try fixture()).map(\.content),
            [.demo]
        )
    }

    func testWithoutAUsableTimelineTheWidgetsAskAgainWithinAnHour() throws {
        let stale = instant("2026-08-11T21:00:00Z")
        for timeline in [nil, try fixture()] {
            let result = LessonWidgetTimeline.timeline(now: stale, session: session, timeline: timeline)
            XCTAssertEqual(result.entries.map(\.content), [.unavailable])
            XCTAssertEqual(result.policy, .after(stale.addingTimeInterval(3600)))
        }
    }

    // MARK: Fixture

    /// LS-3's fixture (a test resource by path, project.yml) inside the App Group envelope, as the writer stores it.
    private func fixture() throws -> LessonTimeline {
        try XCTUnwrap(LessonTimeline.decode(envelope(try fixtureText())))
    }

    private func fixtureText() throws -> String {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "schedule-widget-timeline-v1", withExtension: "json"))
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func envelope(_ value: String, version: Int = 1) -> Data {
        Data(#"{"version": \#(version), "value": \#(value)}"#.utf8)
    }

    private func instant(_ text: String) -> Date {
        AppGroupSnapshot.parseInstant(text)!
    }

    private func describe(_ content: SingleLessonContent) -> String {
        guard let lesson = content.lesson else { return content.kind.rawValue }
        return [content.kind.rawValue, lesson.subject, lesson.state.rawValue, String(content.remainingLessons),
                lesson.pendingStatus?.rawValue]
            .compactMap { $0 }
            .joined(separator: " ")
    }
}

/// The texts and the rows the schedule widgets build from a snapshot (IO-10b), as Android's renderers build them.
final class LessonWidgetLayoutTests: XCTestCase {
    func testTeacherNamesShortenToTheSurnameAndInitials() {
        XCTAssertEqual(LessonWidgetText.compactTeacherName("Иванов Иван Иванович"), "Иванов И. И.")
        XCTAssertEqual(LessonWidgetText.compactTeacherName("  Иванов   Иван  "), "Иванов И.")
        XCTAssertEqual(LessonWidgetText.compactTeacherName("Иванов"), "Иванов")
        XCTAssertEqual(LessonWidgetText.compactTeacherName(" "), "")
    }

    func testDetailsJoinThePlaceAndTheTeacher() {
        XCTAssertEqual(LessonWidgetText.details(of: lesson(room: "1404", building: "Кронва", teacher: "Иванов Иван")), "1404 Кронва · Иванов И.")
        XCTAssertEqual(LessonWidgetText.details(of: lesson(room: nil, building: nil, teacher: "Иванов Иван")), "Иванов И.")
        XCTAssertEqual(LessonWidgetText.details(of: lesson(room: "1404", building: " ", teacher: nil)), "1404")
        XCTAssertEqual(LessonWidgetText.details(of: lesson(room: nil, building: nil, teacher: nil)), "")
    }

    func testTheDayHeaderDateIsTheFilesCalendarDay() {
        XCTAssertEqual(LessonWidgetText.dayAndMonth("2026-08-10", locale: Locale(identifier: "ru_RU")), "10 августа")
        XCTAssertEqual(LessonWidgetText.dayAndMonth("2026-01-01", locale: Locale(identifier: "ru_RU")), "1 января")
        XCTAssertNil(LessonWidgetText.dayAndMonth("tomorrow", locale: Locale(identifier: "ru_RU")))
    }

    func testRowsThatFitKeepEverything() {
        let rows = [header, row(.current), row(.upcoming), end]
        XCTAssertEqual(DayScheduleRows.visible(rows, capacity: 3.5), rows)
    }

    func testCompletedLessonsLeaveFirstThenRowsFromTheEnd() {
        let rows = [header, row(.completed, "A"), row(.completed, "B"), row(.current, "C"), row(.upcoming, "D"), end]
        XCTAssertEqual(DayScheduleRows.visible(rows, capacity: 3.5), [header, row(.current, "C"), row(.upcoming, "D"), end])
        XCTAssertEqual(DayScheduleRows.visible(rows, capacity: 2), [header, row(.current, "C")])
    }

    func testAHeaderWithNothingUnderItIsDropped() {
        let rows = [header, row(.upcoming, "A"), end, header, row(.upcoming, "B")]
        XCTAssertEqual(DayScheduleRows.visible(rows, capacity: 2.5), [header, row(.upcoming, "A"), end])
    }

    private var header: LessonListItem { LessonListItem(kind: .header, lesson: nil, dateIso: "2026-08-10", tomorrow: false) }
    private var end: LessonListItem { LessonListItem(kind: .end, lesson: nil, dateIso: nil, tomorrow: false) }

    private func row(_ state: WidgetLesson.State, _ subject: String = "Физика") -> LessonListItem {
        LessonListItem(kind: .lesson, lesson: lesson(subject: subject, state: state), dateIso: nil, tomorrow: false)
    }

    private func lesson(
        subject: String = "Физика",
        state: WidgetLesson.State = .upcoming,
        room: String? = nil,
        building: String? = nil,
        teacher: String? = nil
    ) -> WidgetLesson {
        WidgetLesson(
            subject: subject, start: "10:00", end: "11:30", typeId: 1, teacher: teacher, room: room,
            building: building, state: state, pendingStatus: nil
        )
    }
}
