import Foundation
import WidgetKit

/// What a schedule widget shows at one moment.
enum LessonWidgetContent: Equatable {
    /// No session file: signed out, or a build without an App Group container.
    case signedOut
    /// The demo session: it has no ITMO.ID token, so no schedule reaches the widgets (docs/features/demo.md).
    case demo
    /// Signed in without a timeline for this moment: none written yet, or the last one ran out.
    case unavailable
    /// The entry of the app's timeline for this moment.
    case snapshot(LessonWidgetSnapshot)
}

struct LessonWidgetEntry: TimelineEntry, Equatable {
    let date: Date
    let content: LessonWidgetContent
}

/// Both schedule widgets' WidgetKit timeline from the App Group files. The app precomputed every switch (lesson starts
/// and ends, the early switch, midnight); this only turns each `validFrom` into an entry and asks for the next timeline
/// when the app's runs out. The app reloads both kinds on every write (`ScheduleTimelineWriter`).
enum LessonWidgetTimeline {
    /// How soon a widget without a timeline asks again, Android's widget update period.
    static let refreshInterval: TimeInterval = 60 * 60

    /// The entries from `now`: the entry of the moment, every later one, and the unavailable state from `validUntil`.
    static func entries(now: Date, session: SessionFile?, timeline: LessonTimeline?) -> [LessonWidgetEntry] {
        guard let session else { return [LessonWidgetEntry(date: now, content: .signedOut)] }
        guard !session.demo else { return [LessonWidgetEntry(date: now, content: .demo)] }
        guard let timeline, now < timeline.validUntil else {
            return [LessonWidgetEntry(date: now, content: .unavailable)]
        }

        var entries = [LessonWidgetEntry(date: now, content: content(of: timeline.entry(at: now)))]
        for entry in timeline.entries where entry.validFrom > now {
            entries.append(LessonWidgetEntry(date: entry.validFrom, content: .snapshot(entry.snapshot)))
        }
        entries.append(LessonWidgetEntry(date: timeline.validUntil, content: .unavailable))
        return entries
    }

    /// The entries, then a new timeline after the last one: at the app timeline's end, or within `refreshInterval`
    /// when there is none.
    static func timeline(now: Date, session: SessionFile?, timeline: LessonTimeline?) -> Timeline<LessonWidgetEntry> {
        let entries = entries(now: now, session: session, timeline: timeline)
        let last = entries.last?.date ?? now
        return Timeline(
            entries: entries,
            policy: .after(last > now ? last : now.addingTimeInterval(refreshInterval))
        )
    }

    /// The content shown at a moment whose timeline entry is `entry`.
    static func content(of entry: LessonTimelineEntry?) -> LessonWidgetContent {
        entry.map { .snapshot($0.snapshot) } ?? .unavailable
    }
}

/// Reads the App Group files on every request for both schedule widgets; it links no Kotlin and makes no network call.
struct LessonWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> LessonWidgetEntry {
        LessonWidgetEntry(date: Date(), content: .snapshot(LessonWidgetPreview.snapshot))
    }

    /// The gallery shows the sample lessons, never the user's schedule.
    func getSnapshot(in context: Context, completion: @escaping (LessonWidgetEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
        } else {
            completion(timeline(now: Date()).entries.first ?? placeholder(in: context))
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LessonWidgetEntry>) -> Void) {
        completion(timeline(now: Date()))
    }

    private func timeline(now: Date) -> Timeline<LessonWidgetEntry> {
        let container = AppGroupSnapshot.container()
        return LessonWidgetTimeline.timeline(
            now: now,
            session: SessionFile.read(fromContainer: container),
            timeline: LessonTimeline.read(fromContainer: container)
        )
    }
}
