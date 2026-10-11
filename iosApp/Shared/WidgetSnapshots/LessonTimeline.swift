import Foundation

/// The schedule widgets' timeline: `schedule-timeline-v1.json` in the App Group container, written by the shared
/// `ScheduleTimelineWriter` (`:shared:feature-schedule`, iosMain) inside the `{"version": N, "value": ...}` envelope of
/// every App Group snapshot (docs/ios.md, Data sharing). The value is LS-3's `ScheduleWidgetTimeline` (reference
/// fixture `shared/feature-schedule/fixtures/schedule-widget-timeline-v1.json`): entries precomputed from now to the
/// end of tomorrow, each holding from its `validFrom` to the next one's, the last one to `validUntil`. Swift only
/// picks the entry of a moment; it takes no time-zone or academic decision.
struct LessonTimeline: Equatable, Decodable {
    /// The value's own format version, the envelope's version (`ScheduleWidgetTimelineJson.VERSION`).
    let formatVersion: Int
    /// When the app computed the timeline.
    let generatedAt: Date
    /// Past this instant the timeline holds nothing.
    let validUntil: Date
    /// Ordered by `validFrom`, the first one at `generatedAt`.
    let entries: [LessonTimelineEntry]
    /// The app's colours while the widgets follow the theme, nil for the brand scheme (additive in version 1).
    var palette: WidgetPalette? = nil

    /// The file name in the App Group container.
    static let fileName = AppGroupSnapshot.fileName("schedule-timeline", version: version)

    /// The highest timeline version this build reads.
    static let version = 1

    /// The timeline in `data` (the envelope), or nil when it is corrupt, empty or written by a newer version.
    static func decode(_ data: Data) -> LessonTimeline? {
        AppGroupSnapshot.decode(LessonTimeline.self, from: data, maxVersion: version).flatMap(validated)
    }

    /// The timeline in the App Group `container`, or nil when it is missing or unreadable.
    static func read(fromContainer container: URL?) -> LessonTimeline? {
        AppGroupSnapshot.read(LessonTimeline.self, file: fileName, maxVersion: version, in: container)
            .flatMap(validated)
    }

    /// The entry shown at `instant`, as Kotlin's `ScheduleWidgetTimeline.entryAt`: nil before the first entry and
    /// from `validUntil` on.
    func entry(at instant: Date) -> LessonTimelineEntry? {
        guard instant < validUntil else { return nil }
        return entries.last { $0.validFrom <= instant }
    }

    private static func validated(_ timeline: LessonTimeline) -> LessonTimeline? {
        guard timeline.formatVersion <= version, !timeline.entries.isEmpty else { return nil }
        return timeline
    }

    private enum CodingKeys: String, CodingKey {
        case formatVersion = "version"
        case generatedAt, validUntil, entries, palette
    }
}

struct LessonTimelineEntry: Equatable, Decodable {
    let validFrom: Date
    let snapshot: LessonWidgetSnapshot
}

/// What both schedule widgets show during one entry, Kotlin's `ScheduleWidgetSnapshot` (the keys of the Android
/// widget snapshot). The official-only fallback and the pending rows' expiry are not read: the writer already ended
/// the entry where a pending row leaves.
struct LessonWidgetSnapshot: Equatable, Decodable {
    let singleLesson: SingleLessonContent
    let lessonList: [LessonListItem]
    let singleLessonStyle: LessonMarkerStyle
    let lessonListStyle: LessonMarkerStyle
    /// The lesson widget's text size option; absent means normal.
    let compactTextSize: LessonTextSize?
    /// The day widget's text size option; absent means normal.
    let fullTextSize: LessonTextSize?
}

/// The lesson widget: one lesson, or a state without one.
struct SingleLessonContent: Equatable, Decodable {
    enum Kind: String, Decodable {
        case loading = "LOADING"
        case signedOut = "SIGNED_OUT"
        case lesson = "LESSON"
        case emptyToday = "EMPTY_TODAY"
        case noMoreToday = "NO_MORE_TODAY"
        case error = "ERROR"
    }

    let kind: Kind
    let lesson: WidgetLesson?
    /// Lessons after this one today.
    let remainingLessons: Int
}

/// One row of the day widget.
struct LessonListItem: Equatable, Decodable {
    enum Kind: String, Decodable {
        case loading = "LOADING"
        case signedOut = "SIGNED_OUT"
        case header = "HEADER"
        case lesson = "LESSON"
        case emptyToday = "EMPTY_TODAY"
        case emptyTodayAndTomorrow = "EMPTY_TODAY_AND_TOMORROW"
        case noMoreToday = "NO_MORE_TODAY"
        case end = "END"
        case error = "ERROR"
    }

    let kind: Kind
    let lesson: WidgetLesson?
    /// The day of a header, `yyyy-MM-dd`.
    let dateIso: String?
    /// The row belongs to tomorrow's block.
    let tomorrow: Bool
}

/// A lesson as the widgets show it. `start` and `end` are the academic `HH:mm` texts; `room` and `building` are the
/// short titles Android's widget shows.
struct WidgetLesson: Equatable, Decodable {
    enum State: String, Decodable {
        case completed = "COMPLETED"
        case current = "CURRENT"
        case upcoming = "UPCOMING"
    }

    /// A sport row that is not a booking yet.
    enum PendingStatus: String, Decodable {
        case waiting = "WAITING"
        case predicted = "PREDICTED"
    }

    let subject: String
    let start: String
    let end: String
    /// MyITMO's lesson type id (1 lecture, 2 lab, 3 practice, 4-9 assessment, 10 consultation, 11 sport).
    let typeId: Int
    let teacher: String?
    let room: String?
    let building: String?
    let state: State
    let pendingStatus: PendingStatus?
}

/// The type marker before a lesson: a dot or a short line (Android's `LessonStyle`).
enum LessonMarkerStyle: String, Decodable {
    case dot = "DOT"
    case line = "LINE"
}

/// A widget text size option (Android's `WidgetTextSize`).
enum LessonTextSize: String, Decodable {
    case normal = "NORMAL"
    case large = "LARGE"
    case extraLarge = "EXTRA_LARGE"

    /// The factor on every text of the widget.
    var scale: Double {
        switch self {
        case .normal: 1
        case .large: 1.2
        case .extraLarge: 1.4
        }
    }
}
