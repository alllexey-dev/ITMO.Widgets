import SwiftUI
import WidgetKit

/// W1, the lesson to go to: small and medium on the home screen (the medium adds two more lessons), the line beside
/// the date, the rectangle and the countdown ring on the Lock Screen. After the last lesson it shows tomorrow's first,
/// as Kotlin decided. One configuration for every placed widget: the options reach the extension in the timeline file.
struct NextLessonWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): placed widgets keep it, and the app reloads it by this name.
    static let kind = "dev.alllexey.itmowidgets.widget.single-lesson"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: ScheduleWidgetProvider()) { entry in
            NextLessonWidgetView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetSingleLessonName))
        .description(Text(.widgetSingleLessonDescription))
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryInline, .accessoryRectangular, .accessoryCircular])
    }
}

/// Reads the family WidgetKit renders in; the entry view takes it as a value, so the gallery and the tests can set it.
private struct NextLessonWidgetView: View {
    let entry: ScheduleWidgetEntry

    @Environment(\.widgetFamily) private var family

    var body: some View {
        NextLessonEntryView(entry: entry, family: family)
    }
}

/// What the next-lesson widget draws: the lesson with its content, or a placeholder.
enum NextLessonDisplay: Equatable {
    case lesson(NextLessonContent, moment: NextLessonMoment, lesson: WidgetLesson, textSize: LessonTextSize)
    case placeholder(WidgetPlaceholder)

    init(_ content: ScheduleWidgetContent) {
        switch content {
        case let .placeholder(state):
            self = .placeholder(state)
        case let .snapshot(snapshot):
            let next = snapshot.next
            switch next.kind {
            case .content:
                if let moment = next.moment, let lesson = next.lesson {
                    self = .lesson(next, moment: moment, lesson: lesson, textSize: snapshot.compactTextSize)
                } else {
                    self = .placeholder(.unavailable)
                }
            case .empty: self = .placeholder(.noLessons)
            case .loading: self = .placeholder(.loading)
            case .signedOut: self = .placeholder(.signedOut)
            case .error: self = .placeholder(.unavailable)
            }
        }
    }
}

/// One entry of the next-lesson widget in `family`.
struct NextLessonEntryView: View {
    let entry: ScheduleWidgetEntry
    let family: WidgetFamily

    var body: some View {
        let colors = WidgetColors(palette: entry.palette)
        let display = NextLessonDisplay(entry.content)
        Group {
            switch family {
            case .accessoryInline:
                NextLessonInline(display: display)
                    .containerBackground(for: .widget) { Color.clear }
            case .accessoryRectangular:
                NextLessonRectangular(display: display)
                    .containerBackground(for: .widget) { Color.clear }
            case .accessoryCircular:
                NextLessonCircular(display: display, clock: entry.clock)
                    .containerBackground(for: .widget) { AccessoryWidgetBackground() }
            default:
                NextLessonTile(display: display, family: family, clock: entry.clock)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .widgetTile(colors.background)
            }
        }
        .environment(\.widgetColors, colors)
        .widgetURL(url(display))
    }

    private func url(_ display: NextLessonDisplay) -> URL {
        if case let .lesson(_, _, lesson, _) = display { return WidgetRoute.lesson(lesson) }
        return WidgetRoute.today
    }
}

/// The home screen tile: the hero alone (small) or beside the lessons after it (medium).
private struct NextLessonTile: View {
    let display: NextLessonDisplay
    let family: WidgetFamily
    let clock: WidgetClock

    @Environment(\.widgetColors) private var colors

    var body: some View {
        switch display {
        case let .placeholder(state):
            WidgetMessage(state: state)
        case let .lesson(content, moment, lesson, textSize):
            Group {
                if family == .systemMedium {
                    HStack(spacing: 12) {
                        NextLessonHero(content: content, moment: moment, lesson: lesson, clock: clock, hero: true)
                            .padding(12)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(colors.hero, in: .rect(cornerRadius: 16, style: .continuous))
                        NextLessonRest(content: content, moment: moment)
                            .padding(.vertical, 4)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    }
                    .padding(10)
                } else {
                    NextLessonHero(content: content, moment: moment, lesson: lesson, clock: clock, hero: false)
                        .padding(14)
                }
            }
            .environment(\.widgetTextScale, textSize.scale)
        }
    }
}

/// The lesson card's order: headline, type bar with the subject and the type, then the room big with the building
/// small and the countdown footer.
private struct NextLessonHero: View {
    let content: NextLessonContent
    let moment: NextLessonMoment
    let lesson: WidgetLesson
    let clock: WidgetClock
    let hero: Bool

    @Environment(\.widgetColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: ScheduleWidgetText.headline(moment, lesson: lesson))
                .widgetFont(12, weight: .bold)
                .foregroundStyle(colors.accent)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            HStack(spacing: 7) {
                WidgetTypeBar(typeId: lesson.typeId, width: 4)
                VStack(alignment: .leading, spacing: 1) {
                    Text(verbatim: ScheduleWidgetText.subject(of: lesson))
                        .widgetFont(13, weight: .semibold)
                        .foregroundStyle(colors.primaryText)
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                    Text(ScheduleWidgetText.type(of: lesson))
                        .widgetFont(11)
                        .foregroundStyle(colors.secondaryText)
                        .lineLimit(1)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 5)
            Spacer(minLength: 2)
            NextLessonPlace(lesson: lesson, size: hero ? 34 : 28)
            NextLessonFooter(content: content, moment: moment, clock: clock)
                .padding(.top, 3)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }
}

/// The room big and the building small after it; online with a camera; a changed room in the warning
/// colour.
private struct NextLessonPlace: View {
    let lesson: WidgetLesson
    let size: CGFloat

    @Environment(\.widgetColors) private var colors

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            if lesson.online {
                Image(systemName: AppSymbol.videocam.systemName)
                    .widgetFont(size * 0.55, weight: .semibold)
                    .accessibilityHidden(true)
                Text(.StringsCommon.lessonPillOnline)
                    .widgetFont(size * 0.8, weight: .bold, design: .rounded)
            } else if let room = lesson.room {
                Text(verbatim: room)
                    .widgetFont(size, weight: .bold, design: .rounded)
                    .layoutPriority(1)
                if let building = lesson.building {
                    Text(verbatim: building)
                        .widgetFont(15)
                }
            } else if let building = lesson.building {
                Text(verbatim: building)
                    .widgetFont(size * 0.8, weight: .bold, design: .rounded)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.6)
        .foregroundStyle(lesson.changed ? WidgetColors.warning : colors.primaryText)
    }
}

/// The countdown line: progress and the minutes left during a lesson (`widget_next_left`), the minutes to the next
/// one (`widget_next_in`) or of the gap (`widget_next_window`), and whether today is over above tomorrow's.
private struct NextLessonFooter: View {
    let content: NextLessonContent
    let moment: NextLessonMoment
    let clock: WidgetClock

    @Environment(\.widgetColors) private var colors

    var body: some View {
        HStack(spacing: 5) {
            switch moment {
            case .now:
                if let since = content.since, let until = content.until, since < until {
                    WidgetLessonProgress(since: since, until: until, clock: clock)
                        .tint(colors.accent)
                    countdown({ .widgetNextLeft($0) }, until: until)
                        .widgetFont(11, weight: .bold)
                        .foregroundStyle(colors.primaryText)
                        .fixedSize()
                }
            case .first, .next:
                if let until = content.until {
                    symbol(content.window ? .hourglassTop : .schedule)
                    countdown(content.window ? { .widgetNextWindow($0) } : { .widgetNextIn($0) }, until: until)
                        .widgetFont(11, weight: .medium)
                        .foregroundStyle(colors.secondaryText)
                }
            case .tomorrow:
                symbol(.eventAvailable)
                Text(content.todayHadLessons ? .widgetNextOver : .widgetNextNoneToday)
                    .widgetFont(11, weight: .medium)
                    .foregroundStyle(colors.secondaryText)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
    }

    /// The template around the live minutes: the template's text before and after its placeholder.
    private func countdown(_ template: @escaping (String) -> LocalizedStringResource, until: Date) -> some View {
        WidgetTemplated(template: template) { WidgetCountdown(target: until, clock: clock) }
    }

    private func symbol(_ symbol: AppSymbol) -> some View {
        Image(systemName: symbol.systemName)
            .widgetFont(11, weight: .semibold)
            .foregroundStyle(colors.secondaryText)
            .accessibilityHidden(true)
    }
}

/// The medium widget's right column: its label with up to two lessons, `widget_next_last_until` when fewer follow.
private struct NextLessonRest: View {
    let content: NextLessonContent
    let moment: NextLessonMoment

    @Environment(\.widgetColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            if !content.rest.isEmpty {
                Text(verbatim: ScheduleWidgetText.restLabel(moment))
                    .widgetFont(12, weight: .semibold)
                    .foregroundStyle(colors.accent)
                ForEach(Array(content.rest.prefix(2).enumerated()), id: \.offset) { _, lesson in
                    Link(destination: WidgetRoute.lesson(lesson)) {
                        NextLessonRestRow(lesson: lesson)
                    }
                }
            }
            Spacer(minLength: 0)
            if content.rest.count < 2, let lastEnd = content.lastEnd {
                Text(.widgetNextLastUntil(lastEnd))
                    .widgetFont(11)
                    .foregroundStyle(colors.secondaryText)
                    .lineLimit(1)
            }
        }
    }
}

/// A following lesson: time, type bar, subject, then the place (or the queue).
private struct NextLessonRestRow: View {
    let lesson: WidgetLesson

    @Environment(\.widgetColors) private var colors

    var body: some View {
        HStack(spacing: 7) {
            WidgetTypeBar(typeId: lesson.typeId)
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: ScheduleWidgetText.time(of: lesson))
                    .widgetFont(11, monospacedDigits: true)
                    .foregroundStyle(colors.secondaryText)
                Text(verbatim: ScheduleWidgetText.subject(of: lesson))
                    .widgetFont(12, weight: .semibold)
                    .foregroundStyle(colors.primaryText)
                HStack(spacing: 3) {
                    if lesson.queued {
                        Text(.StringsCommon.lessonPillQueue)
                    } else {
                        if lesson.online {
                            Image(systemName: AppSymbol.videocam.systemName).accessibilityHidden(true)
                        }
                        if let place = ScheduleWidgetText.place(of: lesson) { Text(verbatim: place) }
                    }
                }
                .widgetFont(11)
                .foregroundStyle(lesson.queued || lesson.changed ? WidgetColors.warning : colors.secondaryText)
            }
            .lineLimit(1)
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .combine)
    }
}

/// The Lock Screen line beside the date: a cap with the room and its time (`ios_widget_inline_*`).
private struct NextLessonInline: View {
    let display: NextLessonDisplay

    var body: some View {
        switch display {
        case let .lesson(_, moment, lesson, _):
            Label {
                Text(verbatim: ScheduleWidgetText.inline(moment, lesson: lesson))
            } icon: {
                Image(systemName: AppSymbol.school.systemName)
            }
        case let .placeholder(state):
            Text(state.title)
        }
    }
}

/// The Lock Screen rectangle in the system's vibrant style: a bar, the headline, the subject and the place.
private struct NextLessonRectangular: View {
    let display: NextLessonDisplay

    var body: some View {
        switch display {
        case let .lesson(_, moment, lesson, _):
            HStack(spacing: 7) {
                RoundedRectangle(cornerRadius: 1.5)
                    .frame(width: 3)
                    .padding(.vertical, 3)
                    .widgetAccentable()
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: ScheduleWidgetText.headline(moment, lesson: lesson))
                        .font(.caption.weight(.semibold))
                        .opacity(0.8)
                    Text(verbatim: ScheduleWidgetText.subject(of: lesson))
                        .font(.headline)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    if let place = ScheduleWidgetText.place(of: lesson) {
                        Text(verbatim: place)
                            .font(.caption.weight(.medium))
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)
        case let .placeholder(state):
            HStack(spacing: 6) {
                Image(systemName: state.symbol.systemName).accessibilityHidden(true)
                Text(state.title)
                    .font(.headline)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// The Lock Screen ring: the elapsed part of the current lesson with the minutes left, the minutes to the next one,
/// or tomorrow's start.
private struct NextLessonCircular: View {
    let display: NextLessonDisplay
    let clock: WidgetClock

    var body: some View {
        Group {
            switch display {
            case let .lesson(content, moment, lesson, _):
                if moment == .now, let since = content.since, let until = content.until, since < until {
                    WidgetLessonProgress(since: since, until: until, clock: clock, ring: true)
                        .overlay { minutes(until) }
                        .padding(2)
                } else if moment != .tomorrow, let until = content.until {
                    VStack(spacing: 0) {
                        Image(systemName: AppSymbol.schedule.systemName)
                            .font(.caption2)
                            .accessibilityHidden(true)
                        minutes(until)
                    }
                } else {
                    VStack(spacing: 0) {
                        Image(systemName: AppSymbol.school.systemName)
                            .font(.caption2)
                            .accessibilityHidden(true)
                        Text(verbatim: lesson.start)
                            .font(.system(.footnote, design: .rounded).weight(.bold).monospacedDigit())
                    }
                }
            case let .placeholder(state):
                Image(systemName: state.symbol.systemName)
                    .font(.title3)
                    .accessibilityLabel(Text(state.title))
            }
        }
        .widgetAccentable()
    }

    private func minutes(_ until: Date) -> some View {
        WidgetCountdown(target: until, clock: clock, fields: [.minute])
            .font(.system(.caption2, design: .rounded).weight(.bold))
            .multilineTextAlignment(.center)
            .minimumScaleFactor(0.5)
            .lineLimit(2)
            .frame(maxWidth: 44)
    }
}
