import Foundation

/// The sport widget's timeline: `sport-widget-v1.json` in the App Group container, written by the shared
/// `SportWidgetWriter` (`:shared:feature-sport`, iosMain) in the App Group envelope (docs/ios.md, Data sharing). The
/// value is the shared `SportWidgetTimeline` (reference fixture `shared/feature-sport/fixtures/sport-widget-timeline-v1.json`):
/// one entry per change (a booking's end, a midnight that renames its day), each holding from its `validFrom` to the
/// next one's, the last one to `validUntil`. Swift only picks the entry of a moment.
struct SportTimeline: Equatable, Decodable {
    let formatVersion: Int
    let generatedAt: Date
    let validUntil: Date
    let entries: [SportTimelineEntry]
    /// The app's colours while the widgets follow the theme, nil for the brand scheme.
    var palette: WidgetPalette? = nil

    /// The file name in the App Group container.
    static let fileName = AppGroupSnapshot.fileName("sport-widget", version: version)

    /// The highest timeline version this build reads (`SportWidgetTimelineJson.VERSION`).
    static let version = 1

    static func decode(_ data: Data) -> SportTimeline? {
        AppGroupSnapshot.decode(SportTimeline.self, from: data, maxVersion: version).flatMap(validated)
    }

    static func read(fromContainer container: URL?) -> SportTimeline? {
        AppGroupSnapshot.read(SportTimeline.self, file: fileName, maxVersion: version, in: container).flatMap(validated)
    }

    /// The entry shown at `instant`: nil before the first entry and from `validUntil` on.
    func entry(at instant: Date) -> SportTimelineEntry? {
        guard instant < validUntil else { return nil }
        return entries.last { $0.validFrom <= instant }
    }

    private static func validated(_ timeline: SportTimeline) -> SportTimeline? {
        guard timeline.formatVersion <= version, !timeline.entries.isEmpty else { return nil }
        return timeline
    }

    private enum CodingKeys: String, CodingKey {
        case formatVersion = "version"
        case generatedAt, validUntil, entries, palette
    }
}

struct SportTimelineEntry: Equatable, Decodable {
    let validFrom: Date
    let snapshot: SportWidgetSnapshot
}

/// Kotlin's `SportWidgetSnapshot`: `visits` and the credited `bonus` make the total; `items` are up to two bookings or
/// queues, nil when they could not be loaded (the ring still shows).
struct SportWidgetSnapshot: Equatable, Decodable {
    enum Kind: String, Decodable {
        case loading = "LOADING"
        case signedOut = "SIGNED_OUT"
        case error = "ERROR"
        case content = "CONTENT"
    }

    let kind: Kind
    let visits: Int
    let bonus: Int
    var items: [SportWidgetItem]?

    /// The points of the credit, as the sport tab ring counts them (`SportWidgetSnapshot.CREDIT`).
    static let credit = 100

    var total: Int { visits + bonus }
    var done: Bool { total >= Self.credit }
    var remaining: Int { max(Self.credit - total, 0) }
}

/// A booking or a queue: its day seen from the snapshot's instant, the academic `HH:mm`, the section and the short
/// building; `lessonId` opens its sheet in the sport tab, as the catalog lesson a `predicted` one repeats.
struct SportWidgetItem: Equatable, Decodable {
    enum Day: String, Decodable {
        case today = "TODAY"
        case tomorrow = "TOMORROW"
        case later = "LATER"
    }

    enum Status: String, Decodable {
        case booked = "BOOKED"
        case queue = "QUEUE"
        case unpublished = "UNPUBLISHED"
    }

    let lessonId: Int64
    /// `yyyy-MM-dd`.
    let date: String
    let day: Day
    let time: String
    let section: String
    var building: String?
    let status: Status
    let position: Int
    let total: Int
    let predicted: Bool
}
