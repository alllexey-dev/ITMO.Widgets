import Foundation
import WidgetKit

/// What a schedule widget shows at one moment.
enum ScheduleWidgetContent: Equatable {
    case placeholder(WidgetPlaceholder)
    /// The snapshot of the app's timeline for this moment.
    case snapshot(ScheduleWidgetSnapshot)
}

struct ScheduleWidgetEntry: TimelineEntry, Equatable {
    let date: Date
    let content: ScheduleWidgetContent
    /// The app's colours while the widgets follow its theme (`ScheduleTimeline.palette`); nil for the brand scheme.
    var palette: WidgetPalette? = nil
    /// Live in WidgetKit; frozen at the sample's moment in the gallery and the snapshot tests.
    var clock: WidgetClock = .live

    /// Progress is rendered from this entry's instant; only the countdown text uses the live system clock.
    var progressDate: Date {
        switch clock {
        case .live: date
        case let .frozen(now): now
        }
    }
}

/// Both schedule widgets' WidgetKit timeline from the App Group files. The app precomputed every switch (lesson
/// starts and ends, the early switch, midnight) and every minute the now line moves to; this only turns each
/// moment into an entry and asks for the next timeline when the app's runs out. The app reloads both kinds on every
/// write (`ScheduleTimelineWriter`).
enum ScheduleWidgetTimeline {
    /// How soon a widget without a timeline asks again, Android's widget update period.
    static let refreshInterval: TimeInterval = 60 * 60

    /// The entries from `now`: the moment's snapshot, every later moment, and the unavailable state from
    /// `validUntil`.
    static func entries(now: Date, session: SessionFile?, timeline: ScheduleTimeline?) -> [ScheduleWidgetEntry] {
        let palette = timeline?.palette
        func entry(_ date: Date, _ content: ScheduleWidgetContent) -> ScheduleWidgetEntry {
            ScheduleWidgetEntry(date: date, content: content, palette: palette)
        }
        guard let session else { return [entry(now, .placeholder(.signedOut))] }
        guard !session.demo else { return [entry(now, .placeholder(.demo))] }
        guard let timeline, now < timeline.validUntil else { return [entry(now, .placeholder(.unavailable))] }

        let moments = timeline.moments
        let current = moments.last { $0.date <= now }
        return [entry(now, current.map { .snapshot($0.snapshot) } ?? .placeholder(.unavailable))]
            + moments.filter { $0.date > now && $0.date < timeline.validUntil }.map { entry($0.date, .snapshot($0.snapshot)) }
            + [entry(timeline.validUntil, .placeholder(.unavailable))]
    }

    /// The entries, then a new timeline after the last one: at the app timeline's end, or within `refreshInterval`
    /// when there is none.
    static func timeline(now: Date, session: SessionFile?, timeline: ScheduleTimeline?) -> Timeline<ScheduleWidgetEntry> {
        let entries = entries(now: now, session: session, timeline: timeline)
        let last = entries.last?.date ?? now
        return Timeline(entries: entries, policy: .after(last > now ? last : now.addingTimeInterval(refreshInterval)))
    }
}

/// Reads the App Group files on every request for both schedule widgets; it links no Kotlin and makes no network call.
struct ScheduleWidgetProvider: TimelineProvider {
    /// The system's placeholder while the widget loads: a calm state, never made-up lessons.
    func placeholder(in context: Context) -> ScheduleWidgetEntry {
        ScheduleWidgetEntry(date: Date(), content: .placeholder(.loading))
    }

    func getSnapshot(in context: Context, completion: @escaping (ScheduleWidgetEntry) -> Void) {
        completion(timeline(now: Date()).entries.first ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<ScheduleWidgetEntry>) -> Void) {
        completion(timeline(now: Date()))
    }

    private func timeline(now: Date) -> Timeline<ScheduleWidgetEntry> {
        let container = AppGroupSnapshot.container()
        return ScheduleWidgetTimeline.timeline(
            now: now,
            session: SessionFile.read(fromContainer: container),
            timeline: ScheduleTimeline.read(fromContainer: container)
        )
    }
}
