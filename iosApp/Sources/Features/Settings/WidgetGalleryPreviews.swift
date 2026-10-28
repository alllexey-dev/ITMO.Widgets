import Foundation
import Shared
import WidgetKit

/// The live previews of the widgets gallery: the real widget entries on the fixed sample (`IosWidgetPreviews`),
/// decoded by the same readers the widget extension uses and frozen at the sample's moment (12:22 on the sample
/// Monday), so every setting and the widget theme show as a placed widget would draw them.
struct WidgetGalleryPreviews: Equatable {
    var next: ScheduleWidgetEntry?
    var day: ScheduleWidgetEntry?
    var cards: ScheduleWidgetEntry?
    var list: ScheduleWidgetEntry?
    var sport: SportWidgetEntry?
    var pass: QrWidgetEntry?

    /// The previews of `cards`, one Kotlin sample per card.
    @MainActor
    static func load(_ cards: [WidgetCard]) async -> WidgetGalleryPreviews {
        var previews = WidgetGalleryPreviews()
        let moment = Date(timeIntervalSince1970: IosWidgetPreviews.shared.seconds(moment: .lesson))
        for card in cards {
            switch onEnum(of: card.preview) {
            case let .schedule(schedule):
                let json = try? await IosWidgetPreviews.shared.schedule(settings: schedule.appearance, moment: .lesson, theme: schedule.theme)
                let entry = json.flatMap { scheduleEntry(Data($0.utf8), at: moment) }
                switch card.format {
                case .day: previews.day = entry
                case .cards: previews.cards = entry
                case .list: previews.list = entry
                default: previews.next = entry
                }
            case let .sport(sport):
                let json = try? await IosWidgetPreviews.shared.sport(state: .queue, theme: sport.theme)
                previews.sport = json.flatMap { sportEntry(Data($0.utf8), at: moment) }
            case let .qr(qr):
                let json = IosWidgetPreviews.shared.pass(settings: qr.appearance, theme: qr.theme)
                previews.pass = passEntry(Data(json.utf8), at: moment)
            }
        }
        return previews
    }

    /// The schedule widgets' entry of `file` at `moment`.
    static func scheduleEntry(_ file: Data, at moment: Date) -> ScheduleWidgetEntry? {
        guard let timeline = ScheduleTimeline.decode(file), let snapshot = timeline.snapshot(at: moment) else { return nil }
        return ScheduleWidgetEntry(date: moment, content: .snapshot(snapshot), palette: timeline.palette, clock: .frozen(moment))
    }

    /// The sport widget's entry of `file` at `moment`.
    static func sportEntry(_ file: Data, at moment: Date) -> SportWidgetEntry? {
        guard let timeline = SportTimeline.decode(file), let entry = timeline.entry(at: moment) else { return nil }
        return SportWidgetEntry(date: moment, content: SportWidgetTimeline.content(of: entry.snapshot), palette: timeline.palette)
    }

    /// The pass widget's entry of `file`: under the spoiler while it is on, the sample code otherwise.
    static func passEntry(_ file: Data, at moment: Date) -> QrWidgetEntry? {
        guard let pass = QrPassSnapshot.decode(file) else { return nil }
        let content: QrWidgetContent = pass.appearance.spoiler ? .spoiler : .revealed(matrix: pass.matrix, demo: false)
        return QrWidgetEntry(date: moment, content: content, appearance: pass.appearance)
    }
}
