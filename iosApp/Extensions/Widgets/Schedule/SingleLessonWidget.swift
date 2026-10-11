import SwiftUI
import WidgetKit

/// The current or next lesson (master A9): small and medium on the home screen, rectangular and inline on the Lock
/// Screen. One configuration for every placed widget: Android keeps the widget options global, and they reach the
/// extension in the timeline file, not through `AppIntentConfiguration`.
struct SingleLessonWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): placed widgets keep it, and the app reloads it by this name.
    static let kind = "dev.alllexey.itmowidgets.widget.single-lesson"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: LessonWidgetProvider()) { entry in
            SingleLessonWidgetView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetSingleLessonName))
        .description(Text(.widgetSingleLessonDescription))
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

/// Where a tap on either schedule widget goes: the schedule tab (`RouteURL`, id `schedule`).
enum LessonWidgetRoute {
    static let scheduleURL = URL(string: "itmowidgets://route/schedule")!
}

/// Reads the family WidgetKit renders in; the entry view takes it as a value, so snapshot tests can set it.
private struct SingleLessonWidgetView: View {
    let entry: LessonWidgetEntry

    @Environment(\.widgetFamily) private var family

    var body: some View {
        SingleLessonEntryView(entry: entry, family: family)
    }
}

/// One entry of the lesson widget in `family`. Degradation against Android: a pending sport row stays until the app
/// writes again, without Android's seven-minute queue refresh (docs/features/widgets.md, iOS).
struct SingleLessonEntryView: View {
    let entry: LessonWidgetEntry
    let family: WidgetFamily

    var body: some View {
        let colors = LessonWidgetColors(palette: entry.palette)
        Group {
            switch family {
            case .accessoryInline:
                inline
            case .accessoryRectangular:
                SingleLessonAccessory(content: entry.content)
                    .containerBackground(for: .widget) { Color.clear }
            default:
                SingleLessonTile(content: entry.content, family: family)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .background(colors.background)
                    .containerBackground(for: .widget) { colors.background }
            }
        }
        .environment(\.lessonWidgetColors, colors)
        .widgetURL(LessonWidgetRoute.scheduleURL)
    }

    /// One line: the lesson's start and subject, or the state's message.
    private var inline: some View {
        Group {
            switch SingleLessonDisplay(entry.content) {
            case let .lesson(lesson, _, _):
                Text(verbatim: "\(lesson.start) \(LessonWidgetText.subject(of: lesson))")
            case let .state(state):
                Text(state.title)
            }
        }
        .containerBackground(for: .widget) { Color.clear }
    }
}

/// What the lesson widget draws: a lesson with its style and the lessons after it, or a state.
enum SingleLessonDisplay {
    case lesson(WidgetLesson, snapshot: LessonWidgetSnapshot, remainingLessons: Int)
    case state(LessonWidgetState)

    init(_ content: LessonWidgetContent) {
        switch content {
        case .signedOut:
            self = .state(.signedOut)
        case .demo:
            self = .state(.demo)
        case .unavailable:
            self = .state(.unavailable)
        case let .snapshot(snapshot):
            let single = snapshot.singleLesson
            switch single.kind {
            case .lesson:
                if let lesson = single.lesson {
                    self = .lesson(lesson, snapshot: snapshot, remainingLessons: single.remainingLessons)
                } else {
                    self = .state(.unavailable)
                }
            case .signedOut: self = .state(.signedOut)
            case .loading: self = .state(.loading)
            case .emptyToday: self = .state(.emptyToday)
            case .noMoreToday: self = .state(.noMoreToday)
            case .error: self = .state(.unavailable)
            }
        }
    }
}

/// The home screen tile: type and time, the subject, place and teacher, then now or next and how many follow.
private struct SingleLessonTile: View {
    let content: LessonWidgetContent
    let family: WidgetFamily

    @Environment(\.lessonWidgetColors) private var colors
    @ScaledMetric(relativeTo: .subheadline) private var titleSize: CGFloat = 15
    @ScaledMetric(relativeTo: .footnote) private var timeSize: CGFloat = 13
    @ScaledMetric(relativeTo: .caption2) private var captionSize: CGFloat = 11

    var body: some View {
        Group {
            switch SingleLessonDisplay(content) {
            case let .lesson(lesson, snapshot, remaining):
                lessonView(lesson, snapshot: snapshot, remaining: remaining)
            case let .state(state):
                LessonWidgetMessage(symbol: state.symbol, title: state.title, hint: state.hint, scale: scale)
            }
        }
        .padding(Layout.padding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var scale: Double {
        if case let .snapshot(snapshot) = content { return (snapshot.compactTextSize ?? .normal).scale }
        return 1
    }

    private func lessonView(_ lesson: WidgetLesson, snapshot: LessonWidgetSnapshot, remaining: Int) -> some View {
        let details = LessonWidgetText.details(of: lesson)
        return VStack(alignment: .leading, spacing: Layout.spacing) {
            if family == .systemSmall {
                typeRow(lesson, style: snapshot.singleLessonStyle)
                time(lesson)
            } else {
                HStack(spacing: Layout.spacing * 2) {
                    typeRow(lesson, style: snapshot.singleLessonStyle)
                    Spacer(minLength: 0)
                    time(lesson)
                }
            }
            Spacer(minLength: 0)
            Text(verbatim: LessonWidgetText.subject(of: lesson))
                .font(.system(size: titleSize * scale, weight: .semibold))
                .foregroundStyle(colors.primaryText)
                .lineLimit(family == .systemSmall ? 3 : 2)
                .minimumScaleFactor(0.85)
            if !details.isEmpty {
                Text(verbatim: details)
                    .font(.system(size: captionSize * scale))
                    .foregroundStyle(colors.secondaryText)
                    .lineLimit(family == .systemSmall ? 2 : 1)
            }
            Text(LessonWidgetText.supporting(state: lesson.state, remainingLessons: remaining))
                .font(.system(size: captionSize * scale, weight: .semibold))
                .foregroundStyle(colors.accent)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .opacity(LessonWidgetColor.opacity(of: lesson))
        .accessibilityElement(children: .combine)
    }

    private func typeRow(_ lesson: WidgetLesson, style: LessonMarkerStyle) -> some View {
        HStack(spacing: 6) {
            LessonMarker(lesson: lesson, style: style, size: 7 * scale)
            Text(LessonWidgetText.type(of: lesson))
                .font(.system(size: captionSize * scale))
                .foregroundStyle(colors.secondaryText)
                .lineLimit(1)
        }
    }

    private func time(_ lesson: WidgetLesson) -> some View {
        Text(verbatim: LessonWidgetText.time(of: lesson))
            .font(.system(size: timeSize * scale, weight: .semibold).monospacedDigit())
            .foregroundStyle(colors.primaryText)
            .lineLimit(1)
    }

    private enum Layout {
        static let padding: CGFloat = 14
        static let spacing: CGFloat = 4
    }
}

/// The Lock Screen rectangle in the system's vibrant style: time and room, the subject, now or next.
private struct SingleLessonAccessory: View {
    let content: LessonWidgetContent

    var body: some View {
        switch SingleLessonDisplay(content) {
        case let .lesson(lesson, _, remaining):
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: [LessonWidgetText.time(of: lesson), lesson.room ?? ""]
                    .filter { !$0.isEmpty }
                    .joined(separator: " \u{00B7} "))
                    .font(.caption.weight(.semibold).monospacedDigit())
                    .widgetAccentable()
                Text(verbatim: LessonWidgetText.subject(of: lesson))
                    .font(.headline)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                Text(LessonWidgetText.supporting(state: lesson.state, remainingLessons: remaining))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
        case let .state(state):
            HStack(spacing: 6) {
                Image(systemName: state.symbol.systemName)
                    .accessibilityHidden(true)
                Text(state.title)
                    .font(.headline)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
