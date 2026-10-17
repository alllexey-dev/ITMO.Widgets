import Foundation

/// The schedule widgets' timeline: `schedule-timeline-v2.json` in the App Group container, written by the shared
/// `ScheduleTimelineWriter` (`:shared:feature-schedule`, iosMain) inside the `{"version": N, "value": ...}` envelope of
/// every App Group snapshot (docs/ios.md, Data sharing). The value is the shared `ScheduleWidgetTimeline` (reference
/// fixture `shared/feature-schedule/fixtures/schedule-widget-timeline-v2.json`): entries precomputed from now to the
/// end of tomorrow, each holding from its `validFrom` to the next one's, the last one to `validUntil`, and inside an
/// entry the now minute of the day widget moving at its `nowTicks`. Swift only picks the snapshot of a moment;
/// it takes no time-zone or academic decision.
struct ScheduleTimeline: Equatable, Decodable {
    /// The value's own format version, the envelope's version (`ScheduleWidgetTimelineJson.VERSION`).
    let formatVersion: Int
    /// When the app computed the timeline.
    let generatedAt: Date
    /// Past this instant the timeline holds nothing.
    let validUntil: Date
    /// Ordered by `validFrom`, the first one at `generatedAt`.
    let entries: [ScheduleTimelineEntry]
    /// The app's colours while the widgets follow the theme, nil for the brand scheme.
    var palette: WidgetPalette? = nil

    /// The file name in the App Group container.
    static let fileName = AppGroupSnapshot.fileName("schedule-timeline", version: version)

    /// The highest timeline version this build reads.
    static let version = 2

    /// The timeline in `data` (the envelope), or nil when it is corrupt, empty or written by a newer version.
    static func decode(_ data: Data) -> ScheduleTimeline? {
        AppGroupSnapshot.decode(ScheduleTimeline.self, from: data, maxVersion: version).flatMap(validated)
    }

    /// The timeline in the App Group `container`, or nil when it is missing or unreadable.
    static func read(fromContainer container: URL?) -> ScheduleTimeline? {
        AppGroupSnapshot.read(ScheduleTimeline.self, file: fileName, maxVersion: version, in: container)
            .flatMap(validated)
    }

    /// The entry shown at `instant`, as Kotlin's `ScheduleWidgetTimeline.entryAt`: nil before the first entry and
    /// from `validUntil` on.
    func entry(at instant: Date) -> ScheduleTimelineEntry? {
        guard instant < validUntil else { return nil }
        return entries.last { $0.validFrom <= instant }
    }

    /// Every moment the drawing changes, in order: each entry's `validFrom` with its snapshot, then each of its
    /// `nowTicks` with the day widget's `now` at the tick's minute.
    var moments: [ScheduleMoment] {
        entries.flatMap { entry in
            [ScheduleMoment(date: entry.validFrom, snapshot: entry.snapshot)]
                + entry.nowTicks.map { tick in
                    ScheduleMoment(date: tick.at, snapshot: entry.snapshot.withNow(tick.minute))
                }
        }
    }

    /// The snapshot shown at `instant`: the last moment at or before it; nil outside the timeline.
    func snapshot(at instant: Date) -> ScheduleWidgetSnapshot? {
        guard instant < validUntil else { return nil }
        return moments.last { $0.date <= instant }?.snapshot
    }

    private static func validated(_ timeline: ScheduleTimeline) -> ScheduleTimeline? {
        guard timeline.formatVersion <= version, !timeline.entries.isEmpty else { return nil }
        return timeline
    }

    private enum CodingKeys: String, CodingKey {
        case formatVersion = "version"
        case generatedAt, validUntil, entries, palette
    }
}

/// From `date` on the widgets draw `snapshot`.
struct ScheduleMoment: Equatable {
    let date: Date
    let snapshot: ScheduleWidgetSnapshot
}

struct ScheduleTimelineEntry: Equatable, Decodable {
    let validFrom: Date
    let snapshot: ScheduleWidgetSnapshot
    /// Inside the entry, where the day widget's now line moves.
    let nowTicks: [NowTick]
}

/// From `at` on the day widget's now line stands at `minute` of the day.
struct NowTick: Equatable, Decodable {
    let at: Date
    let minute: Int
}

/// What both schedule widgets show during one entry, Kotlin's `ScheduleWidgetSnapshot`. The official-only fallback
/// and the pending rows' expiry are not read: the writer already ended the entry where a pending row leaves.
struct ScheduleWidgetSnapshot: Equatable, Decodable {
    /// The next-lesson widget (W1) and the Lock Screen.
    let next: NextLessonContent
    /// The day widget (W2).
    let day: DayContent
    /// The next-lesson widget's text size option.
    let compactTextSize: LessonTextSize
    /// The day widget's text size option.
    let fullTextSize: LessonTextSize

    /// The snapshot with the now line at `minute`.
    func withNow(_ minute: Int) -> ScheduleWidgetSnapshot {
        ScheduleWidgetSnapshot(next: next, day: day.withNow(minute), compactTextSize: compactTextSize, fullTextSize: fullTextSize)
    }
}

/// What a widget shows besides its content (Kotlin's `WidgetContentKind`).
enum WidgetContentKind: String, Decodable {
    case loading = "LOADING"
    case signedOut = "SIGNED_OUT"
    case error = "ERROR"
    case content = "CONTENT"
    case empty = "EMPTY"
}

/// The next-lesson widget's headline: now until its end, the first lesson, the next one, tomorrow's first.
enum NextLessonMoment: String, Decodable {
    case now = "NOW"
    case first = "FIRST"
    case next = "NEXT"
    case tomorrow = "TOMORROW"
}

/// The next-lesson widget (Kotlin's `NextLessonWidgetContent`): the lesson to go to, its countdown target `until`
/// (the end of a current lesson, the start of a coming one, nil for tomorrow's) and `since` (the start of a current
/// one, for its progress), whether the wait is a gap of an hour or more, up to two lessons after it that day, and the
/// end of that day's last lesson.
struct NextLessonContent: Equatable, Decodable {
    let kind: WidgetContentKind
    var moment: NextLessonMoment?
    var lesson: WidgetLesson?
    var until: Date?
    var since: Date?
    var window: Bool
    var rest: [WidgetLesson]
    var lastEnd: String?
    var todayHadLessons: Bool
}

/// A gap of an hour or more between two lessons of the day widget, in minutes of the day.
struct WidgetGap: Equatable, Decodable {
    let startMinute: Int
    let endMinute: Int
}

/// The day widget's subtitle (Kotlin's `DaySummaryKind`).
enum DaySummaryKind: String, Decodable {
    case planned = "PLANNED"
    case remaining = "REMAINING"
    case over = "OVER"
    case noneToday = "NONE_TODAY"
}

/// `lessons` counts the academic lessons the subtitle names, `sport` whether sport is among them; `from` and `to`
/// are the day's `HH:mm` bounds.
struct DaySummary: Equatable, Decodable {
    let kind: DaySummaryKind
    let lessons: Int
    let sport: Bool
    var from: String?
    var to: String?
}

/// The day widget (Kotlin's `DayWidgetContent`): the lessons of `date` (`yyyy-MM-dd`; `tomorrow` once today is
/// over) on a time axis from `axisStart` to `axisEnd` (minutes of the day), the gaps and the now line at `now`.
struct DayContent: Equatable, Decodable {
    let kind: WidgetContentKind
    var date: String?
    var tomorrow: Bool
    var summary: DaySummary?
    var lessons: [WidgetLesson]
    var gaps: [WidgetGap]
    var axisStart: Int
    var axisEnd: Int
    var now: Int?

    func withNow(_ minute: Int) -> DayContent {
        var copy = self
        copy.now = minute
        return copy
    }
}

/// A lesson as the widgets show it. `start` and `end` are the academic `HH:mm` texts with their minutes of the day;
/// `room` and `building` are the short titles of the Android widget. `lesson` (the JSON of `LessonDetailsArgs`) or
/// `sport` is what a tap opens.
struct WidgetLesson: Equatable, Decodable {
    let subject: String
    let start: String
    let end: String
    let startMinute: Int
    let endMinute: Int
    /// MyITMO's lesson type id (1 lecture, 2 lab, 3 practice, 4-9 assessment, 10 consultation, 11 sport).
    let typeId: Int
    let state: WidgetLessonState
    var room: String?
    var building: String?
    var teacher: String?
    var online: Bool
    var changed: Bool
    var cancelled: Bool
    var queued: Bool
    /// The place in a queue and its length, 0 when Backend does not know them.
    var queuePosition: Int
    var queueTotal: Int
    var lesson: JSONValue?
    var sport: PendingSportTarget?
}

enum WidgetLessonState: String, Decodable {
    case done = "DONE"
    case now = "NOW"
    case upcoming = "UPCOMING"
}

/// The part of Kotlin's `PendingSportDetailsArgs` a tap needs: the queued lesson, negated for a predicted one.
struct PendingSportTarget: Equatable, Decodable {
    let lessonId: Int64
    let isPrediction: Bool
}

/// A widget text size option (Kotlin's `WidgetTextSize`).
enum LessonTextSize: String, Decodable {
    case normal = "NORMAL"
    case large = "LARGE"
    case extraLarge = "EXTRA_LARGE"

    /// The factor on every text of the widget.
    var scale: Double {
        switch self {
        case .normal: 1
        case .large: 1.15
        case .extraLarge: 1.3
        }
    }
}
