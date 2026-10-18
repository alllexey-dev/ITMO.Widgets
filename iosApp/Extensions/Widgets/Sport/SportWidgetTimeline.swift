import Foundation
import WidgetKit

/// What the sport widget shows at one moment.
enum SportWidgetContent: Equatable {
    case placeholder(WidgetPlaceholder)
    case snapshot(SportWidgetSnapshot)
}

struct SportWidgetEntry: TimelineEntry, Equatable {
    let date: Date
    let content: SportWidgetContent
    var palette: WidgetPalette? = nil
}

/// The sport widget's WidgetKit timeline: each entry of the app's file from now on, then the unavailable state from
/// its end; the app reloads the kind on every write (`SportWidgetWriter`).
enum SportWidgetTimeline {
    static let refreshInterval: TimeInterval = 60 * 60

    static func entries(now: Date, session: SessionFile?, timeline: SportTimeline?) -> [SportWidgetEntry] {
        let palette = timeline?.palette
        func entry(_ date: Date, _ content: SportWidgetContent) -> SportWidgetEntry {
            SportWidgetEntry(date: date, content: content, palette: palette)
        }
        guard let session else { return [entry(now, .placeholder(.signedOut))] }
        guard !session.demo else { return [entry(now, .placeholder(.demo))] }
        guard let timeline, now < timeline.validUntil else { return [entry(now, .placeholder(.unavailable))] }

        return [entry(now, timeline.entry(at: now).map { content(of: $0.snapshot) } ?? .placeholder(.unavailable))]
            + timeline.entries.filter { $0.validFrom > now }.map { entry($0.validFrom, content(of: $0.snapshot)) }
            + [entry(timeline.validUntil, .placeholder(.unavailable))]
    }

    static func timeline(now: Date, session: SessionFile?, timeline: SportTimeline?) -> Timeline<SportWidgetEntry> {
        let entries = entries(now: now, session: session, timeline: timeline)
        let last = entries.last?.date ?? now
        return Timeline(entries: entries, policy: .after(last > now ? last : now.addingTimeInterval(refreshInterval)))
    }

    /// The snapshot, or the placeholder of a snapshot without a score.
    static func content(of snapshot: SportWidgetSnapshot) -> SportWidgetContent {
        switch snapshot.kind {
        case .content: .snapshot(snapshot)
        case .loading: .placeholder(.sportLoading)
        case .signedOut: .placeholder(.signedOut)
        case .error: .placeholder(.sportError)
        }
    }
}

struct SportWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> SportWidgetEntry {
        SportWidgetEntry(date: Date(), content: .placeholder(.sportLoading))
    }

    func getSnapshot(in context: Context, completion: @escaping (SportWidgetEntry) -> Void) {
        completion(timeline(now: Date()).entries.first ?? placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SportWidgetEntry>) -> Void) {
        completion(timeline(now: Date()))
    }

    private func timeline(now: Date) -> Timeline<SportWidgetEntry> {
        let container = AppGroupSnapshot.container()
        return SportWidgetTimeline.timeline(
            now: now,
            session: SessionFile.read(fromContainer: container),
            timeline: SportTimeline.read(fromContainer: container)
        )
    }
}
