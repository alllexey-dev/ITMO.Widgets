import SwiftUI
import WidgetKit

/// W2, the day to scale: the large widget with an hour axis, the lessons where they fall, the gaps of an hour or more
/// and the now line; tomorrow once today is over, as Kotlin decided. The medium family is not offered: a
/// time-scaled day does not fit it.
struct DayWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): placed widgets keep it, and the app reloads it by this name. The
    /// first builds' `.day-schedule` stays the classic list's (`ClassicListWidget`).
    static let kind = "dev.alllexey.itmowidgets.widget.day-timeline"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: ScheduleWidgetProvider()) { entry in
            DayEntryView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetDayName))
        .description(Text(.widgetDayDescription))
        .supportedFamilies([.systemLarge])
    }
}

/// One entry of the day widget. A tap on the header or outside a lesson opens the schedule on today, a lesson its
/// sheet.
struct DayEntryView: View {
    let entry: ScheduleWidgetEntry

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
            switch snapshot.day.kind {
            case .content, .empty:
                DayTile(day: snapshot.day)
                    .environment(\.widgetTextScale, snapshot.fullTextSize.scale)
            case .loading: WidgetMessage(state: .loading)
            case .signedOut: WidgetMessage(state: .signedOut)
            case .error: WidgetMessage(state: .unavailable)
            }
        }
    }
}

private struct DayTile: View {
    let day: DayContent

    @Environment(\.widgetColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Link(destination: WidgetRoute.today) {
                VStack(alignment: .leading, spacing: 1) {
                    if let title = ScheduleWidgetText.dayTitle(day) {
                        Text(verbatim: title)
                            .widgetFont(15, weight: .bold)
                            .foregroundStyle(colors.primaryText)
                            .minimumScaleFactor(0.75)
                    }
                    if let summary = day.summary.flatMap(ScheduleWidgetText.summary) {
                        Text(verbatim: summary)
                            .widgetFont(12)
                            .foregroundStyle(colors.secondaryText)
                    }
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if day.kind == .empty || day.lessons.isEmpty || day.axisEnd <= day.axisStart {
                Text(.widgetDayEmpty)
                    .widgetFont(14, weight: .semibold)
                    .foregroundStyle(colors.secondaryText)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                DayTimeline(day: day)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

/// The axis and the lessons to scale (`DayAxis`).
private struct DayTimeline: View {
    let day: DayContent

    @Environment(\.widgetColors) private var colors
    @Environment(\.widgetTextScale) private var scale
    @ScaledMetric(relativeTo: .body) private var unit: CGFloat = 1

    var body: some View {
        GeometryReader { geometry in
            let minimum = Layout.minimumBlock * unit * scale
            let axis = DayAxis(
                day: day, height: geometry.size.height, minimumBlock: minimum,
                maximumDensity: Layout.maximumHour / 60 * unit * scale
            )
            let lessonsWidth = geometry.size.width - Layout.axisWidth
            ZStack(alignment: .topLeading) {
                ForEach(hours(axis), id: \.self) { hour in
                    Text(verbatim: "\(hour)")
                        .widgetFont(10, weight: .medium, monospacedDigits: true)
                        .foregroundStyle(colors.secondaryText.opacity(0.7))
                        .frame(width: Layout.axisWidth - 6, alignment: .leading)
                        .offset(y: axis.y(hour * 60) - 6)
                }
                ForEach(Array(day.gaps.enumerated()), id: \.offset) { _, gap in
                    let top = axis.y(gap.startMinute), bottom = axis.y(gap.endMinute)
                    if bottom - top >= Layout.gapPill * unit * scale {
                        WidgetPill(text: String(localized: .widgetDayWindow(WidgetText.duration(minutes: gap.endMinute - gap.startMinute))))
                            .frame(width: lessonsWidth)
                            .offset(x: Layout.axisWidth, y: (top + bottom) / 2 - Layout.gapPill * unit * scale / 2)
                    }
                }
                // The line runs under the blocks; the current block draws its part up to its text.
                if let now = day.now {
                    Rectangle()
                        .fill(WidgetColors.nowLine)
                        .frame(width: lessonsWidth + 2, height: 1.5)
                        .offset(x: Layout.axisWidth - 2, y: axis.y(now) - 0.75)
                }
                ForEach(Array(day.lessons.enumerated()), id: \.offset) { _, lesson in
                    let top = axis.y(lesson.startMinute), bottom = axis.y(lesson.endMinute)
                    Link(destination: WidgetRoute.lesson(lesson)) {
                        DayBlock(lesson: lesson, now: day.now, axis: axis, height: max(bottom - top - Layout.blockGap, 1))
                    }
                    .frame(width: lessonsWidth, height: max(bottom - top - Layout.blockGap, 1))
                    .offset(x: Layout.axisWidth, y: top)
                }
                if let now = day.now {
                    Text(verbatim: WidgetText.clock(minute: now))
                        .widgetFont(10, weight: .bold, monospacedDigits: true)
                        .foregroundStyle(ItmoColor.onError)
                        .padding(.horizontal, 3)
                        .padding(.vertical, 1)
                        .background(WidgetColors.nowLine, in: .capsule)
                        .fixedSize()
                        .offset(x: -4, y: axis.y(now) - 7 * unit * scale)
                }
            }
            .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
        }
    }

    /// The full hours inside the axis, without the one under the now chip.
    private func hours(_ axis: DayAxis) -> [Int] {
        let first = day.axisStart / 60 + 1, last = day.axisEnd / 60
        guard first <= last else { return [] }
        return (first...last).filter { hour in
            guard let now = day.now else { return true }
            return abs(axis.y(hour * 60) - axis.y(now)) >= 12 * unit * scale
        }
    }

    enum Layout {
        static let axisWidth: CGFloat = 28
        static let minimumBlock: CGFloat = 26
        /// The most an hour takes: a day of one or two lessons stays compact at the top.
        static let maximumHour: CGFloat = 36
        static let blockGap: CGFloat = 3
        static let gapPill: CGFloat = 18
    }
}

/// A lesson to scale: the type bar, the subject and the room on top, the time and the building (or a pill) under
/// them; the current one tinted with its elapsed part shaded, done ones dimmed, a cancelled one struck through.
private struct DayBlock: View {
    let lesson: WidgetLesson
    let now: Int?
    let axis: DayAxis
    let height: CGFloat

    @Environment(\.widgetColors) private var colors
    @Environment(\.widgetTextScale) private var scale
    @ScaledMetric(relativeTo: .body) private var unit: CGFloat = 1

    var body: some View {
        let current = lesson.state == .now && !lesson.cancelled
        HStack(spacing: 7) {
            // Off the block's top and bottom edges, as on Android (3 dp padding and 3 dp margin).
            WidgetTypeBar(typeId: lesson.typeId)
                .padding(.vertical, 6)
            if height >= 30 * unit * scale {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 4) {
                        subject
                        Spacer(minLength: 0)
                        place
                    }
                    HStack(spacing: 5) {
                        Text(verbatim: ScheduleWidgetText.time(of: lesson))
                            .widgetFont(11, monospacedDigits: true)
                            .foregroundStyle(colors.secondaryText)
                        if let teacher = lesson.teacher {
                            Text(verbatim: teacher)
                                .widgetFont(11)
                                .foregroundStyle(colors.secondaryText)
                        }
                        pill
                        Spacer(minLength: 0)
                        if let building = lesson.building, !lesson.online {
                            Text(verbatim: building)
                                .widgetFont(11)
                                .foregroundStyle(colors.secondaryText)
                        }
                    }
                }
            } else {
                HStack(spacing: 5) {
                    Text(verbatim: lesson.start)
                        .widgetFont(11, monospacedDigits: true)
                        .foregroundStyle(colors.secondaryText)
                    subject
                    pill
                    Spacer(minLength: 0)
                    place
                }
            }
        }
        .lineLimit(1)
        .padding(.leading, 4)
        .padding(.trailing, 8)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .background(alignment: .top) {
            ZStack(alignment: .top) {
                current ? colors.hero : colors.block
                if current, let now {
                    // The elapsed part, inset inside the block, down to the now line.
                    UnevenRoundedRectangle(topLeadingRadius: 6, topTrailingRadius: 6, style: .continuous)
                        .fill(colors.accent.opacity(0.25))
                        .frame(height: max(elapsed(now) - Self.inset, 0))
                        .padding([.top, .horizontal], Self.inset)
                }
            }
        }
        .overlay(alignment: .topLeading) {
            // The now line inside the block runs across the colour bar up to the text, never through it.
            if current, let now {
                WidgetColors.nowLine
                    .frame(width: Self.gutter, height: 1.5)
                    .offset(y: min(max(elapsed(now) - 0.75, 0), height - 1.5))
            }
        }
        .clipShape(.rect(cornerRadius: 9, style: .continuous))
        .opacity(lesson.state == .done || lesson.cancelled ? 0.5 : 1)
        .accessibilityElement(children: .combine)
    }

    /// The points of the block above the now line.
    private func elapsed(_ now: Int) -> CGFloat {
        min(max(axis.y(now) - axis.y(lesson.startMinute), 0), height)
    }

    /// The elapsed shade's inset; the gutter before the text (padding 4, bar 3, spacing 7) less 2 points.
    private static let inset: CGFloat = 3
    private static let gutter: CGFloat = 12

    private var subject: some View {
        Text(verbatim: ScheduleWidgetText.subject(of: lesson))
            .widgetFont(12, weight: .semibold)
            .strikethrough(lesson.cancelled)
            .foregroundStyle(colors.primaryText)
    }

    @ViewBuilder
    private var place: some View {
        HStack(spacing: 3) {
            if lesson.online {
                Image(systemName: AppSymbol.videocam.systemName).accessibilityHidden(true)
                Text(.StringsCommon.lessonPillOnline)
            } else if let room = lesson.room {
                Text(verbatim: room)
            }
        }
        .widgetFont(12, weight: .bold)
        .foregroundStyle(lesson.changed ? WidgetColors.warning : colors.primaryText)
        .layoutPriority(1)
    }

    @ViewBuilder
    private var pill: some View {
        if lesson.cancelled {
            WidgetPill(text: String(localized: .StringsCommon.lessonPillCancelled), color: ItmoColor.error, size: 10)
        } else if lesson.queued {
            WidgetPill(text: ScheduleWidgetText.queue(of: lesson), color: WidgetColors.warning, size: 10)
        }
    }
}

/// The day widget's vertical scale: minutes of the day to points from the top. Time runs linearly, except that every
/// lesson gets at least `minimumBlock` points, so a short one stays readable; the rest of the day shares the room left,
/// at most `maximumDensity` points a minute, so a short day keeps compact blocks at the top instead of stretching.
struct DayAxis {
    private let breakpoints: [(minute: Int, y: CGFloat)]

    init(day: DayContent, height: CGFloat, minimumBlock: CGFloat, maximumDensity: CGFloat = .infinity) {
        let start = day.axisStart, end = max(day.axisEnd, day.axisStart + 1)
        let lessons = day.lessons
            .map { (start: min(max($0.startMinute, start), end), end: min(max($0.endMinute, start), end)) }
            .filter { $0.end > $0.start }
        let minutes = Array(Set([start, end] + lessons.flatMap { [$0.start, $0.end] })).sorted()
        // The least points per minute each segment needs for its lessons to reach `minimumBlock`.
        let needs: [CGFloat] = zip(minutes, minutes.dropFirst()).map { from, to in
            lessons.filter { $0.start <= from && $0.end >= to }
                .map { minimumBlock / CGFloat($0.end - $0.start) }
                .max() ?? 0
        }
        let lengths = zip(minutes, minutes.dropFirst()).map { CGFloat($1 - $0) }
        func total(_ base: CGFloat) -> CGFloat {
            zip(lengths, needs).reduce(0) { $0 + $1.0 * max(base, $1.1) }
        }
        var low: CGFloat = 0, high = min(height / CGFloat(end - start), maximumDensity)
        let densities: [CGFloat]
        if total(0) >= height {
            // Too many lessons for their minimum: everything shrinks alike.
            let shrink = height / max(total(0), 1)
            densities = needs.map { $0 * shrink }
        } else {
            for _ in 0..<40 {
                let middle = (low + high) / 2
                if total(middle) > height { high = middle } else { low = middle }
            }
            densities = needs.map { max(low, $0) }
        }
        var points = [(minute: start, y: CGFloat(0))]
        for (index, minute) in minutes.dropFirst().enumerated() {
            points.append((minute, points[index].y + lengths[index] * densities[index]))
        }
        breakpoints = points
    }

    /// The point of `minute`, clamped to the axis.
    func y(_ minute: Int) -> CGFloat {
        guard let first = breakpoints.first, let last = breakpoints.last else { return 0 }
        if minute <= first.minute { return first.y }
        if minute >= last.minute { return last.y }
        let index = breakpoints.lastIndex { $0.minute <= minute } ?? 0
        let from = breakpoints[index], to = breakpoints[index + 1]
        return from.y + (to.y - from.y) * CGFloat(minute - from.minute) / CGFloat(to.minute - from.minute)
    }
}
