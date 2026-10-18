import SwiftUI
import WidgetKit

/// W3, the sport score: the medium widget with the ring (visits and bonuses out of 100) and the nearest bookings or
/// queues in the sport tab's words, the small one with the ring alone. A tap opens the sport tab, a booking row its
/// lesson.
struct SportWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): placed widgets keep it, and the app reloads it by this name.
    static let kind = "dev.alllexey.itmowidgets.widget.sport"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: SportWidgetProvider()) { entry in
            SportWidgetView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetSportName))
        .description(Text(.widgetSportDescription))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

private struct SportWidgetView: View {
    let entry: SportWidgetEntry

    @Environment(\.widgetFamily) private var family

    var body: some View {
        SportEntryView(entry: entry, family: family)
    }
}

/// One entry of the sport widget in `family`.
struct SportEntryView: View {
    let entry: SportWidgetEntry
    let family: WidgetFamily

    var body: some View {
        let colors = WidgetColors(palette: entry.palette)
        Group {
            switch entry.content {
            case let .placeholder(state):
                WidgetMessage(state: state)
            case let .snapshot(snapshot):
                if family == .systemMedium {
                    SportMedium(snapshot: snapshot)
                } else {
                    SportRing(snapshot: snapshot, size: 134)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .dynamicTypeSize(...DynamicTypeSize.xxLarge)
        .widgetTile(colors.background)
        .environment(\.widgetColors, colors)
        .widgetURL(WidgetRoute.sport)
    }
}

/// The score ring of the sport tab at widget size: visits in the accent, bonuses in the tertiary role, the number
/// and `widget_sport_of` inside; closed and green once the credit is reached.
struct SportRing: View {
    let snapshot: SportWidgetSnapshot
    let size: CGFloat

    @Environment(\.widgetColors) private var colors

    var body: some View {
        let line = size / 11
        let credit = SportWidgetSnapshot.credit
        let visits = min(max(snapshot.visits, 0), credit)
        let bonus = min(max(snapshot.total - visits, 0), min(snapshot.bonus, credit - visits))
        let visitsShare = CGFloat(visits) / CGFloat(credit), bonusShare = CGFloat(bonus) / CGFloat(credit)
        // Round caps reach half a line past each end: the gap covers both caps and 3 pt of air.
        let gap = (line + 3) / (.pi * (size - line))
        ZStack {
            Group {
                if snapshot.done {
                    Circle().stroke(WidgetColors.success, lineWidth: line)
                } else if visits + bonus == 0 {
                    Circle().stroke(colors.block, lineWidth: line)
                } else {
                    let arcs = [(CGFloat(0), visitsShare, colors.accent), (visitsShare, bonusShare, colors.bonus)].filter { $0.1 > 0 }
                    ForEach(arcs.indices, id: \.self) { index in
                        let (from, length, color) = arcs[index]
                        Circle()
                            .trim(from: from + gap / 2, to: from + max(length - gap / 2, gap / 2 + 0.001))
                            .stroke(color, style: StrokeStyle(lineWidth: line, lineCap: .round))
                    }
                    if 1 - visitsShare - bonusShare > gap {
                        Circle()
                            .trim(from: visitsShare + bonusShare + gap / 2, to: 1 - gap / 2)
                            .stroke(colors.block, style: StrokeStyle(lineWidth: line, lineCap: .round))
                    }
                }
            }
            .rotationEffect(.degrees(-90))
            .padding(line / 2)
            VStack(spacing: -2) {
                Text(verbatim: "\(snapshot.total)")
                    .font(.system(size: size * 0.3, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(colors.primaryText)
                Text(.widgetSportOf(Int(credit)))
                    .font(.caption2)
                    .foregroundStyle(colors.secondaryText)
            }
            .minimumScaleFactor(0.6)
            .lineLimit(1)
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .combine)
    }
}

private struct SportMedium: View {
    let snapshot: SportWidgetSnapshot

    @Environment(\.widgetColors) private var colors

    var body: some View {
        HStack(spacing: 14) {
            SportRing(snapshot: snapshot, size: 106)
            VStack(alignment: .leading, spacing: 6) {
                VStack(alignment: .leading, spacing: 2) {
                    Group {
                        if snapshot.done {
                            Text(.widgetSportDone)
                        } else {
                            Text(.widgetSportRemaining(snapshot.remaining))
                        }
                    }
                    .widgetFont(14, weight: .bold)
                    .foregroundStyle(colors.primaryText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    legend
                        .widgetFont(11)
                        .foregroundStyle(colors.secondaryText)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                if let items = snapshot.items {
                    if items.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: AppSymbol.eventAvailable.systemName).accessibilityHidden(true)
                            Text(.widgetSportEmpty)
                        }
                        .widgetFont(12, weight: .semibold)
                        .foregroundStyle(colors.secondaryText)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 9)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(colors.block, in: .rect(cornerRadius: 12, style: .continuous))
                    } else {
                        ForEach(Array(items.prefix(2).enumerated()), id: \.offset) { index, item in
                            Link(destination: WidgetRoute.sportLesson(id: item.lessonId, predicted: item.predicted)) {
                                SportBookingRow(item: item, lead: index == 0)
                            }
                        }
                    }
                }
            }
            .frame(maxHeight: .infinity)
        }
        .padding(14)
    }

    @ViewBuilder
    private var legend: some View {
        if snapshot.done {
            Text(verbatim: "\(String(localized: .widgetSportVisits(snapshot.visits))), \(String(localized: .widgetSportBonus(snapshot.bonus)))")
        } else {
            HStack(spacing: 8) {
                dot(colors.accent, .widgetSportVisits(snapshot.visits))
                dot(colors.bonus, .widgetSportBonus(snapshot.bonus))
            }
        }
    }

    private func dot(_ color: Color, _ text: LocalizedStringResource) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 7, height: 7).accessibilityHidden(true)
            Text(text)
        }
    }
}

/// Time and status, the sport bar, the section and the building; the nearest one on a tinted block.
private struct SportBookingRow: View {
    let item: SportWidgetItem
    let lead: Bool

    @Environment(\.widgetColors) private var colors

    var body: some View {
        HStack(spacing: 7) {
            WidgetTypeBar(typeId: 11)
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(verbatim: SportWidgetText.when(item))
                        .widgetFont(11, weight: .semibold, monospacedDigits: true)
                        .foregroundStyle(colors.secondaryText)
                        .fixedSize()
                    Spacer(minLength: 4)
                    WidgetPill(text: SportWidgetText.status(item), color: item.status == .booked ? WidgetColors.success : WidgetColors.warning)
                        .fixedSize()
                }
                Text(verbatim: SportWidgetText.place(item))
                    .widgetFont(12, weight: .semibold)
                    .foregroundStyle(colors.primaryText)
            }
            .lineLimit(1)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, lead ? 9 : 2)
        .padding(.vertical, lead ? 6 : 0)
        .background(lead ? colors.block : .clear, in: .rect(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// The sport widget's texts, in the sport tab's words.
enum SportWidgetText {
    /// The day and the time: today, tomorrow or the short weekday (`widget_sport_today`, `_tomorrow`, `_later`).
    static func when(_ item: SportWidgetItem) -> String {
        switch item.day {
        case .today: return String(localized: .widgetSportToday(item.time))
        case .tomorrow: return String(localized: .widgetSportTomorrow(item.time))
        case .later:
            let weekday = WidgetText.date(item.date, template: "EEEEEE").map(WidgetText.capitalized) ?? item.date
            return String(localized: .widgetSportLater(weekday, item.time))
        }
    }

    /// Booked, the place in the queue, a queue without a place, waiting for the timetable.
    static func status(_ item: SportWidgetItem) -> String {
        switch item.status {
        case .booked: String(localized: .StringsCommon.lessonPillBooked)
        case .queue:
            item.position > 0 && item.total > 0
                ? String(localized: .StringsSport.sportQueuePosition(item.position, item.total))
                : String(localized: .StringsSport.sportQueuePill)
        case .unpublished: String(localized: .StringsSport.sportQueueUnpublished)
        }
    }

    /// The section and the building.
    static func place(_ item: SportWidgetItem) -> String {
        [item.section, item.building].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
    }
}
