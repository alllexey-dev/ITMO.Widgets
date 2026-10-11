import SwiftUI
import WidgetKit

/// The day's lessons (master A9), medium and large, with tomorrow's once today is over when the option is on. One
/// configuration for every placed widget, as `SingleLessonWidget`.
struct DayScheduleWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): placed widgets keep it, and the app reloads it by this name.
    static let kind = "dev.alllexey.itmowidgets.widget.day-schedule"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: LessonWidgetProvider()) { entry in
            DayScheduleWidgetView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetLessonListName))
        .description(Text(.widgetLessonListDescription))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

private struct DayScheduleWidgetView: View {
    let entry: LessonWidgetEntry

    @Environment(\.widgetFamily) private var family

    var body: some View {
        DayScheduleEntryView(entry: entry, family: family)
    }
}

/// One entry of the day widget in `family`. WidgetKit has no scrolling, so the rows that fit are shown: completed
/// lessons leave first, then the rows at the end (`DayScheduleRows`).
struct DayScheduleEntryView: View {
    let entry: LessonWidgetEntry
    let family: WidgetFamily

    var body: some View {
        let colors = LessonWidgetColors(palette: entry.palette)
        DayScheduleTile(content: entry.content, family: family)
            .dynamicTypeSize(...DynamicTypeSize.xLarge)
            .background(colors.background)
            .containerBackground(for: .widget) { colors.background }
            .environment(\.lessonWidgetColors, colors)
            .widgetURL(LessonWidgetRoute.scheduleURL)
    }
}

/// Which rows of the day list fit a widget.
enum DayScheduleRows {
    /// The rows a family holds at the default text size, a lesson counting 1 and any other row one half.
    static func capacity(of family: WidgetFamily) -> Double {
        family == .systemLarge ? 8.5 : 3.5
    }

    /// The rows of `items` within `capacity`: completed lessons leave first, oldest first, then rows from the end; a
    /// header with nothing under it goes too.
    static func visible(_ items: [LessonListItem], capacity: Double) -> [LessonListItem] {
        var rows = items
        while weight(of: rows) > capacity,
              let completed = rows.firstIndex(where: { $0.kind == .lesson && $0.lesson?.state == .completed }) {
            rows.remove(at: completed)
        }
        var visible: [LessonListItem] = []
        var used = 0.0
        for row in rows {
            let next = used + weight(of: row)
            guard next <= capacity else { break }
            visible.append(row)
            used = next
        }
        if visible.last?.kind == .header, visible.count < rows.count {
            visible.removeLast()
        }
        return visible
    }

    private static func weight(of rows: [LessonListItem]) -> Double {
        rows.reduce(0) { $0 + weight(of: $1) }
    }

    private static func weight(of row: LessonListItem) -> Double {
        row.kind == .lesson ? 1 : 0.5
    }
}

private struct DayScheduleTile: View {
    let content: LessonWidgetContent
    let family: WidgetFamily

    @Environment(\.locale) private var locale
    @Environment(\.lessonWidgetColors) private var colors
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .subheadline) private var titleSize: CGFloat = 14
    @ScaledMetric(relativeTo: .footnote) private var timeSize: CGFloat = 13
    @ScaledMetric(relativeTo: .caption2) private var captionSize: CGFloat = 11

    var body: some View {
        Group {
            if let state = wholeState {
                LessonWidgetMessage(symbol: state.symbol, title: state.title, hint: state.hint, scale: scale)
            } else if case let .snapshot(snapshot) = content {
                rows(snapshot)
            }
        }
        .padding(Layout.padding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    /// The state that fills the whole widget: no snapshot, or a list without lessons, whose first state row says why.
    private var wholeState: LessonWidgetState? {
        switch content {
        case .signedOut: return .signedOut
        case .demo: return .demo
        case .unavailable: return .unavailable
        case let .snapshot(snapshot):
            guard !snapshot.lessonList.contains(where: { $0.kind == .lesson }) else { return nil }
            for item in snapshot.lessonList {
                switch item.kind {
                case .signedOut: return .signedOut
                case .loading: return .loading
                case .error: return .unavailable
                case .emptyToday: return .emptyToday
                case .emptyTodayAndTomorrow: return .emptyTodayAndTomorrow
                case .noMoreToday: return .noMoreToday
                case .header, .lesson, .end: continue
                }
            }
            return .unavailable
        }
    }

    private var scale: Double {
        if case let .snapshot(snapshot) = content { return (snapshot.fullTextSize ?? .normal).scale }
        return 1
    }

    private func rows(_ snapshot: LessonWidgetSnapshot) -> some View {
        let growth = dynamicTypeSize > .large ? 1.15 : 1
        let visible = DayScheduleRows.visible(snapshot.lessonList, capacity: DayScheduleRows.capacity(of: family) / (scale * growth))
        return VStack(alignment: .leading, spacing: Layout.spacing) {
            ForEach(Array(visible.enumerated()), id: \.offset) { _, item in
                row(item, style: snapshot.lessonListStyle)
            }
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private func row(_ item: LessonListItem, style: LessonMarkerStyle) -> some View {
        switch item.kind {
        case .header:
            caption(Text(LessonWidgetText.header(item, locale: locale)), weight: .semibold)
                .foregroundStyle(colors.accent)
        case .lesson:
            if let lesson = item.lesson { lessonRow(lesson, style: style) }
        case .end:
            caption(Text(item.tomorrow ? .scheduleWidgetEndTomorrow : .scheduleWidgetEndToday), weight: .regular)
                .foregroundStyle(colors.secondaryText)
        case .emptyToday, .emptyTodayAndTomorrow, .noMoreToday, .signedOut, .loading, .error:
            caption(Text(message(item.kind)), weight: .medium)
                .foregroundStyle(colors.secondaryText)
        }
    }

    private func message(_ kind: LessonListItem.Kind) -> LocalizedStringResource {
        switch kind {
        case .emptyToday: .scheduleWidgetEmptyToday
        case .emptyTodayAndTomorrow: .scheduleWidgetEmptyTodayAndTomorrow
        case .noMoreToday: .scheduleWidgetNoMoreToday
        case .signedOut: .scheduleWidgetSignedOut
        case .loading: .scheduleWidgetLoading
        default: .scheduleWidgetError
        }
    }

    private func caption(_ text: Text, weight: Font.Weight) -> some View {
        text
            .font(.system(size: captionSize * scale, weight: weight))
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .frame(maxWidth: .infinity, alignment: .center)
    }

    private func lessonRow(_ lesson: WidgetLesson, style: LessonMarkerStyle) -> some View {
        let details = LessonWidgetText.details(of: lesson)
        return HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .trailing, spacing: 0) {
                Text(verbatim: lesson.start)
                    .font(.system(size: timeSize * scale, weight: .semibold).monospacedDigit())
                    .foregroundStyle(colors.primaryText)
                Text(verbatim: lesson.end)
                    .font(.system(size: captionSize * scale).monospacedDigit())
                    .foregroundStyle(colors.secondaryText)
            }
            .lineLimit(1)
            .fixedSize()
            LessonMarker(lesson: lesson, style: style, size: 7 * scale)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: LessonWidgetText.subject(of: lesson))
                    .font(.system(size: titleSize * scale, weight: .semibold))
                    .foregroundStyle(colors.primaryText)
                subtitle(lesson, details: details)
                    .font(.system(size: captionSize * scale))
                    .foregroundStyle(colors.secondaryText)
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .opacity(LessonWidgetColor.opacity(of: lesson))
        .accessibilityElement(children: .combine)
    }

    /// The type, then the place and the teacher.
    private func subtitle(_ lesson: WidgetLesson, details: String) -> Text {
        let type = String(localized: LessonWidgetText.type(of: lesson))
        return Text(verbatim: details.isEmpty ? type : "\(type) \u{00B7} \(details)")
    }

    private enum Layout {
        static let padding: CGFloat = 14
        static let spacing: CGFloat = 6
    }
}
