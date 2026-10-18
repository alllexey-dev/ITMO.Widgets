import SwiftUI
import WidgetKit

/// The classic list (`widget_lesson_list_name`), the day widget of the first builds: medium and large, the day's header, its lessons
/// in rows and the end marker, from the day widget's content of the same timeline (tomorrow once today is over, as
/// Kotlin decided). A tap anywhere opens the schedule on today.
struct ClassicListWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): the day widget's kind before the day to scale, so the widgets
    /// placed then stay lists.
    static let kind = "dev.alllexey.itmowidgets.widget.day-schedule"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: ScheduleWidgetProvider()) { entry in
            ClassicListWidgetView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetLessonListName))
        .description(Text(.widgetLessonListDescription))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

private struct ClassicListWidgetView: View {
    let entry: ScheduleWidgetEntry

    @Environment(\.widgetFamily) private var family

    var body: some View {
        ClassicListEntryView(entry: entry, family: family)
    }
}

/// One entry of the classic list in `family`.
struct ClassicListEntryView: View {
    let entry: ScheduleWidgetEntry
    let family: WidgetFamily

    var body: some View {
        let colors = WidgetColors(palette: entry.palette)
        content
            .dynamicTypeSize(...DynamicTypeSize.xLarge)
            .widgetTile(colors.background)
            .environment(\.widgetColors, colors)
            .widgetURL(WidgetRoute.today)
    }

    @ViewBuilder
    private var content: some View {
        switch entry.content {
        case let .placeholder(state):
            WidgetMessage(state: state)
        case let .snapshot(snapshot):
            let day = snapshot.day
            switch day.kind {
            case .content where !day.lessons.isEmpty:
                ClassicListTile(day: day, family: family)
                    .environment(\.widgetTextScale, snapshot.fullTextSize.scale)
            case .content, .empty: WidgetMessage(state: ClassicListRows.emptyState(of: day))
            case .loading: WidgetMessage(state: .loading)
            case .signedOut: WidgetMessage(state: .signedOut)
            case .error: WidgetMessage(state: .unavailable)
            }
        }
    }
}

/// The rows of the classic list and which of them fit: WidgetKit has no scrolling, so done lessons leave first, then
/// the rows at the end.
enum ClassicListRows {
    enum Row: Equatable {
        case header
        case lesson(WidgetLesson)
        case end
    }

    /// The rows a family holds at the default text size, a lesson counting 1 and the header or the end one half.
    static func capacity(of family: WidgetFamily) -> Double {
        family == .systemLarge ? 8.5 : 3.5
    }

    /// The day's rows within `capacity`.
    static func visible(_ day: DayContent, capacity: Double) -> [Row] {
        var rows: [Row] = (day.date == nil ? [] : [.header]) + day.lessons.map(Row.lesson) + [.end]
        while weight(of: rows) > capacity, let done = rows.firstIndex(where: \.isDone) {
            rows.remove(at: done)
        }
        var visible: [Row] = []
        var used = 0.0
        for row in rows {
            used += weight(of: row)
            guard used <= capacity else { break }
            visible.append(row)
        }
        return visible
    }

    /// Why a day without lessons is empty, the messages of the Android list.
    static func emptyState(of day: DayContent) -> WidgetPlaceholder {
        if day.tomorrow { return .noLessons }
        return day.summary?.kind == .noneToday ? .noLessonsToday : .noMoreToday
    }

    private static func weight(of rows: [Row]) -> Double {
        rows.reduce(0) { $0 + weight(of: $1) }
    }

    private static func weight(of row: Row) -> Double {
        if case .lesson = row { return 1 }
        return 0.5
    }
}

private extension ClassicListRows.Row {
    var isDone: Bool {
        if case let .lesson(lesson) = self { return lesson.state == .done }
        return false
    }
}

private struct ClassicListTile: View {
    let day: DayContent
    let family: WidgetFamily

    @Environment(\.widgetColors) private var colors
    @Environment(\.widgetTextScale) private var scale
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let growth = dynamicTypeSize > .large ? 1.15 : 1
        let rows = ClassicListRows.visible(day, capacity: ClassicListRows.capacity(of: family) / (scale * growth))
        VStack(alignment: .leading, spacing: 6) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                switch row {
                case .header:
                    caption(Text(verbatim: header), weight: .semibold)
                        .foregroundStyle(colors.accent)
                case let .lesson(lesson):
                    ClassicListLessonRow(lesson: lesson)
                case .end:
                    caption(Text(day.tomorrow ? .scheduleWidgetEndTomorrow : .scheduleWidgetEndToday), weight: .regular)
                        .foregroundStyle(colors.secondaryText)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    /// `schedule_widget_day_header`: today or tomorrow and the date, the Android list's header.
    private var header: String {
        let date = day.date.flatMap { WidgetText.date($0, template: "dMMMM") } ?? ""
        let name = String(localized: day.tomorrow ? .scheduleWidgetTomorrow : .scheduleWidgetToday)
        return String(localized: .scheduleWidgetDayHeader(name, date))
    }

    private func caption(_ text: Text, weight: Font.Weight) -> some View {
        text
            .widgetFont(11, weight: weight)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .frame(maxWidth: .infinity, alignment: .center)
    }
}

/// A lesson: the start over the end, the type dot (a ring for a queue), the subject, then the type, the place and the
/// teacher; a done or cancelled one dimmed.
private struct ClassicListLessonRow: View {
    let lesson: WidgetLesson

    @Environment(\.widgetColors) private var colors
    @Environment(\.widgetTextScale) private var scale
    @ScaledMetric(relativeTo: .body) private var unit: CGFloat = 1

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .trailing, spacing: 0) {
                Text(verbatim: lesson.start)
                    .widgetFont(13, weight: .semibold, monospacedDigits: true)
                    .foregroundStyle(colors.primaryText)
                Text(verbatim: lesson.end)
                    .widgetFont(11, monospacedDigits: true)
                    .foregroundStyle(colors.secondaryText)
            }
            .lineLimit(1)
            .fixedSize()
            marker
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: ScheduleWidgetText.subject(of: lesson))
                    .widgetFont(14, weight: .semibold)
                    .strikethrough(lesson.cancelled)
                    .foregroundStyle(colors.primaryText)
                Text(verbatim: subtitle)
                    .widgetFont(11)
                    .foregroundStyle(colors.secondaryText)
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .opacity(lesson.state == .done || lesson.cancelled ? 0.62 : 1)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var marker: some View {
        let size = 7 * unit * scale
        if lesson.queued {
            Circle().strokeBorder(WidgetColors.type(lesson.typeId), lineWidth: 1.5).frame(width: size, height: size)
                .accessibilityHidden(true)
        } else {
            Circle().fill(WidgetColors.type(lesson.typeId)).frame(width: size, height: size)
                .accessibilityHidden(true)
        }
    }

    /// The type (or the queue, or `lesson_pill_cancelled`), then the place and the teacher.
    private var subtitle: String {
        let status = lesson.cancelled
            ? String(localized: .StringsCommon.lessonPillCancelled)
            : lesson.queued ? ScheduleWidgetText.queue(of: lesson) : String(localized: ScheduleWidgetText.type(of: lesson))
        let place = lesson.online
            ? String(localized: .StringsCommon.lessonPillOnline)
            : [lesson.room, lesson.building].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
        return [status, place, lesson.teacher ?? ""].filter { !$0.isEmpty }.joined(separator: " \u{00B7} ")
    }
}
