import SwiftUI
import WidgetKit

/// The cards widget (`widget_cards_name`), medium and large: a page of `cardsPages` with its title, the arrows that
/// turn the days (`TurnCardsPageIntent`) and a card per lesson left that day, as many as fit; the rest fold into one
/// last row. The current lesson shows its elapsed share as M3 Expressive's wavy indicator (ADR 0021, amended).
struct CardsWidget: Widget {
    /// A stable identifier (`StableIdentifiersTests`): placed widgets keep it, and the app reloads it by this name.
    static let kind = "dev.alllexey.itmowidgets.widget.cards"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: CardsWidgetProvider()) { entry in
            CardsEntryView(entry: entry)
        }
        .configurationDisplayName(Text(.widgetCardsName))
        .description(Text(.widgetCardsDescription))
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

/// The schedule timeline with the turned page: each entry before the page ends on the same first day shows it, and
/// one more entry at its end brings the first page back.
enum CardsWidgetTimeline {
    static func entries(
        now: Date, session: SessionFile?, timeline: ScheduleTimeline?, page: CardsWidgetPage?
    ) -> [ScheduleWidgetEntry] {
        var entries = ScheduleWidgetTimeline.entries(now: now, session: session, timeline: timeline)
        guard let page, page.until > now else { return entries }
        if let shown = entries.last(where: { $0.date <= page.until }), shown.date < page.until {
            let reset = ScheduleWidgetEntry(date: page.until, content: shown.content, palette: shown.palette)
            entries.insert(reset, at: (entries.firstIndex { $0.date > page.until } ?? entries.endIndex))
        }
        return entries.map { entry in
            var turned = entry
            turned.cardsPage = page.page(at: entry.date, first: entry.firstCardsDay)
            return turned
        }
    }

    static func timeline(
        now: Date, session: SessionFile?, timeline: ScheduleTimeline?, page: CardsWidgetPage?
    ) -> Timeline<ScheduleWidgetEntry> {
        let entries = entries(now: now, session: session, timeline: timeline, page: page)
        let last = entries.last?.date ?? now
        return Timeline(
            entries: entries, policy: .after(last > now ? last : now.addingTimeInterval(ScheduleWidgetTimeline.refreshInterval))
        )
    }
}

private extension ScheduleWidgetEntry {
    var firstCardsDay: String? {
        if case let .snapshot(snapshot) = content { return snapshot.cardsPages.first?.date }
        return nil
    }
}

/// Reads the App Group files on every request; it links no Kotlin and makes no network call.
struct CardsWidgetProvider: TimelineProvider {
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
        return CardsWidgetTimeline.timeline(
            now: now,
            session: SessionFile.read(fromContainer: container),
            timeline: ScheduleTimeline.read(fromContainer: container),
            page: CardsWidgetPage.read(fromContainer: container)
        )
    }
}

/// One entry of the cards widget, laid out in the size it gets. A tap on the title or outside a card opens the schedule on today, a card
/// its lesson.
struct CardsEntryView: View {
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
                CardsTile(snapshot: snapshot, page: entry.cardsPage, clock: entry.clock)
                    .environment(\.widgetTextScale, snapshot.fullTextSize.scale)
            case .loading: WidgetMessage(state: .loading)
            case .signedOut: WidgetMessage(state: .signedOut)
            case .error: WidgetMessage(state: .unavailable)
            }
        }
    }
}

/// How many cards fit: the heights (pt at the default text size) the views below take, and Android's rule
/// (`CardsWidgetRenderer.fit`): all that fit, else as many as fit above the row folding the rest, at least one.
enum CardsLayout {
    static let chrome: CGFloat = 80
    static let card: CGFloat = 62
    static let currentCard: CGFloat = 86
    static let more: CGFloat = 36

    static func fit(_ heights: [CGFloat], more: CGFloat, available: CGFloat) -> Int {
        if heights.reduce(0, +) <= available { return heights.count }
        var used = more
        var count = 0
        for height in heights {
            if used + height > available { break }
            used += height
            count += 1
        }
        return max(count, min(1, heights.count))
    }
}

private struct CardsTile: View {
    let snapshot: ScheduleWidgetSnapshot
    let page: Int
    let clock: WidgetClock

    @Environment(\.widgetColors) private var colors
    @Environment(\.widgetTextScale) private var scale
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let pages = snapshot.cardsPages
        let index = min(max(page, 0), pages.count - 1)
        let day = pages[index]
        let offset = snapshot.cardsFirstOffset + index
        // Today's past lessons give their room to the ones left: the widget never scrolls.
        let lessons = day.lessons.filter { offset != 0 || $0.state != .done }
        GeometryReader { geometry in
            VStack(alignment: .leading, spacing: 8) {
                if let date = day.date {
                    header(date: date, day: day, offset: offset, index: index, last: pages.count - 1, base: pages[0].date ?? date)
                }
                if day.kind == .content, !lessons.isEmpty {
                    cards(lessons, now: offset == 0 ? day.now : nil, height: geometry.size.height)
                } else {
                    Text(day.kind == .content ? .scheduleWidgetNoMoreToday : .widgetDayEmpty)
                        .widgetFont(14, weight: .semibold)
                        .foregroundStyle(colors.secondaryText)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 10)
            .padding(.top, 12)
            .padding(.bottom, 10)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
    }

    private func header(date: String, day: DayContent, offset: Int, index: Int, last: Int, base: String) -> some View {
        HStack(alignment: .center, spacing: 6) {
            Link(destination: WidgetRoute.today) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: CardsText.title(date: date, offset: offset))
                        .widgetFont(20, weight: .semibold)
                        .foregroundStyle(colors.primaryText)
                    Text(verbatim: CardsText.subtitle(day: day, offset: offset))
                        .widgetFont(13)
                        .foregroundStyle(colors.secondaryText)
                }
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            CardsArrow(symbol: "chevron.left", shape: AnyShape(Circle()), target: index - 1, enabled: index > 0, base: base)
                .accessibilityLabel(Text(.widgetCardsPrev))
            CardsArrow(
                symbol: "chevron.right", shape: AnyShape(RoundedRectangle(cornerRadius: 12, style: .continuous)),
                target: index + 1, enabled: index < last, base: base
            )
            .accessibilityLabel(Text(.widgetCardsNext))
        }
        .padding(.leading, 6)
    }

    @ViewBuilder
    private func cards(_ lessons: [WidgetLesson], now: Int?, height: CGFloat) -> some View {
        let growth: CGFloat = dynamicTypeSize > .large ? 1.15 : 1
        let unit = CGFloat(scale) * growth
        let heights = lessons.map { ($0.state == .now && now != nil ? CardsLayout.currentCard : CardsLayout.card) * unit }
        let available = height - CardsLayout.chrome * unit
        let count = CardsLayout.fit(heights, more: CardsLayout.more * unit, available: available)
        let highlighted = lessons.firstIndex { $0.state == .now } ?? lessons.firstIndex { !$0.cancelled && !$0.queued }
        let rest = Array(lessons.dropFirst(count))
        VStack(spacing: 4) {
            ForEach(Array(lessons.prefix(count).enumerated()), id: \.offset) { position, lesson in
                Link(destination: WidgetRoute.lesson(lesson)) {
                    CardsLessonCard(
                        lesson: lesson, highlighted: position == highlighted,
                        now: lesson.state == .now ? now : nil, countdown: countdown(for: lesson)
                    )
                }
            }
            if !rest.isEmpty, heights.prefix(count).reduce(0, +) + CardsLayout.more * unit <= available {
                CardsMoreRow(rest: rest)
            }
        }
    }

    /// The live minutes left when the next-lesson content counts down to this lesson's end, else nil (the minutes of
    /// the entry show instead).
    private func countdown(for lesson: WidgetLesson) -> Text? {
        guard snapshot.next.moment == .now, let current = snapshot.next.lesson, current.start == lesson.start,
              let until = snapshot.next.until else { return nil }
        return WidgetCountdown.text({ .widgetNextLeft($0) }, to: until, clock: clock)
    }
}

/// The cards widget's labels, Android's `CardsWidgetRenderer` texts.
enum CardsText {
    /// `schedule_widget_today`, `schedule_widget_tomorrow` or the weekday.
    static func title(date: String, offset: Int) -> String {
        switch offset {
        case 0: return String(localized: .scheduleWidgetToday)
        case 1: return String(localized: .scheduleWidgetTomorrow)
        default: return WidgetText.date(date, template: "EEEE").map(WidgetText.capitalized) ?? date
        }
    }

    /// The date and `widget_cards_remaining` on today (the plain count before the first lesson, `widget_next_over`
    /// after the last); another day's count, or `widget_cards_none`.
    static func subtitle(day: DayContent, offset: Int) -> String {
        let date = day.date.flatMap { WidgetText.date($0, template: "dMMMM") } ?? ""
        return String(localized: .widgetCardsDot(date, count(day: day, offset: offset)))
    }

    private static func count(day: DayContent, offset: Int) -> String {
        guard day.kind == .content, let summary = day.summary else { return String(localized: .widgetCardsNone) }
        if summary.kind == .over || summary.kind == .noneToday {
            return ScheduleWidgetText.summary(summary) ?? String(localized: .widgetCardsNone)
        }
        guard let lessons = ScheduleWidgetText.count(summary) else { return String(localized: .widgetCardsNone) }
        return offset == 0 && summary.kind == .remaining ? String(localized: .widgetCardsRemaining(lessons)) : lessons
    }
}

/// A day arrow: a button that turns the widget while there is a day that way, a dimmed glyph otherwise.
private struct CardsArrow: View {
    let symbol: String
    let shape: AnyShape
    let target: Int
    let enabled: Bool
    let base: String

    @Environment(\.widgetColors) private var colors

    var body: some View {
        if enabled {
            Button(intent: TurnCardsPageIntent(page: target, base: base)) { glyph }
                .buttonStyle(.plain)
        } else {
            glyph.opacity(0.38)
        }
    }

    private var glyph: some View {
        Image(systemName: symbol)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(colors.primaryText)
            .frame(width: 36, height: 36)
            .background(colors.block, in: shape)
    }
}

/// A lesson: the start over the end, the subject, the type chip and `room · teacher`; the current one adds the wavy
/// progress and the minutes left.
private struct CardsLessonCard: View {
    let lesson: WidgetLesson
    let highlighted: Bool
    let now: Int?
    let countdown: Text?

    @Environment(\.widgetColors) private var colors
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let ink = highlighted ? colors.onHero : colors.primaryText
        let quiet = highlighted ? colors.onHero : colors.secondaryText
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: lesson.start)
                        .widgetFont(21, weight: .semibold, monospacedDigits: true)
                        .foregroundStyle(ink)
                    Text(verbatim: lesson.end)
                        .widgetFont(13, monospacedDigits: true)
                        .foregroundStyle(quiet)
                }
                .lineLimit(1)
                .frame(minWidth: 56, alignment: .leading)
                VStack(alignment: .leading, spacing: 5) {
                    Text(verbatim: ScheduleWidgetText.subject(of: lesson))
                        .widgetFont(16, weight: .semibold)
                        .foregroundStyle(ink)
                    HStack(spacing: 8) {
                        chip
                        Text(verbatim: place)
                            .widgetFont(13)
                            .foregroundStyle(lesson.changed ? WidgetColors.warning : quiet)
                    }
                }
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            if let now {
                let total = max(lesson.endMinute - lesson.startMinute, 1)
                HStack(spacing: 12) {
                    WavyProgress(value: min(max(Double(now - lesson.startMinute) / Double(total), 0), 1))
                    (countdown ?? Text(.widgetNextLeft(WidgetText.duration(minutes: max(lesson.endMinute - now, 0)))))
                        .widgetFont(14, weight: .semibold)
                        .foregroundStyle(colors.onHero)
                        .lineLimit(1)
                        .fixedSize()
                }
            }
        }
        .padding(.leading, 14)
        .padding(.trailing, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(highlighted ? colors.hero : colors.block, in: .rect(cornerRadius: 18, style: .continuous))
        .opacity(lesson.cancelled ? 0.5 : 1)
        .accessibilityElement(children: .combine)
    }

    /// The type on a pill of its colour (dark ink on light, light on dark), or `lesson_pill_cancelled` and the queue on the warning
    /// tone.
    @ViewBuilder
    private var chip: some View {
        if lesson.cancelled {
            WidgetPill(text: String(localized: .StringsCommon.lessonPillCancelled), color: WidgetColors.warning, size: 12)
        } else if lesson.queued {
            WidgetPill(text: ScheduleWidgetText.queue(of: lesson), color: WidgetColors.warning, size: 12)
        } else {
            let type = WidgetColors.type(lesson.typeId)
            Text(ScheduleWidgetText.type(of: lesson))
                .widgetFont(12, weight: .semibold)
                .foregroundStyle(scheme == .dark ? type.mix(with: .white, by: 0.5) : type.mix(with: .black, by: 0.45))
                .padding(.horizontal, 9)
                .padding(.vertical, 1)
                .background(type.opacity(0.28), in: .capsule)
                .fixedSize()
        }
    }

    private var place: String {
        let place = lesson.online ? String(localized: .StringsCommon.lessonPillOnline) : lesson.room ?? lesson.building
        return [place, lesson.teacher].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " \u{00B7} ")
    }
}

/// The start and type of the first lesson that did not fit and `widget_cards_more`: the first lesson that did not fit and how many did not.
private struct CardsMoreRow: View {
    let rest: [WidgetLesson]

    @Environment(\.widgetColors) private var colors

    var body: some View {
        let first = rest[0]
        HStack(spacing: 8) {
            Text(.widgetCardsDot(first.start, String(localized: ScheduleWidgetText.type(of: first))))
                .widgetFont(14)
                .foregroundStyle(colors.secondaryText)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(.widgetCardsMore(rest.count))
                .widgetFont(13, weight: .semibold)
                .foregroundStyle(colors.primaryText)
                .padding(.horizontal, 12)
                .padding(.vertical, 3)
                .background(colors.bonus.opacity(0.28), in: .capsule)
                .fixedSize()
        }
        .lineLimit(1)
        .padding(.leading, 14)
        .padding(.trailing, 8)
        .padding(.vertical, 7)
        .background(colors.block, in: .rect(cornerRadius: 18, style: .continuous))
    }
}

/// M3 Expressive's wavy progress: the wave up to `value`, then after a gap the flat track ending in a stop dot.
struct WavyProgress: View {
    let value: Double

    @Environment(\.widgetColors) private var colors

    var body: some View {
        GeometryReader { geometry in
            let width = geometry.size.width
            let middle = geometry.size.height / 2
            let trackStart = width * value + Self.gap
            ZStack(alignment: .leading) {
                WaveLine()
                    .trim(from: 0, to: value)
                    .stroke(colors.accent, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                if trackStart < width - Self.dot * 2 {
                    Path { path in
                        path.move(to: CGPoint(x: trackStart, y: middle))
                        path.addLine(to: CGPoint(x: width - Self.dot * 2, y: middle))
                    }
                    .stroke(colors.onHero.opacity(0.35), style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    Circle()
                        .fill(colors.onHero.opacity(0.35))
                        .frame(width: Self.dot, height: Self.dot)
                        .position(x: width - Self.dot / 2, y: middle)
                }
            }
        }
        .frame(height: 12)
        .accessibilityHidden(true)
    }

    private static let gap: CGFloat = 8
    private static let dot: CGFloat = 5
}

/// A sine across the rect, one wave every 20 pt, inset by the stroke's cap.
struct WaveLine: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let amplitude = rect.height / 2 - 2.5
        let start = rect.minX + 1.5, end = rect.maxX - 1.5
        path.move(to: CGPoint(x: start, y: rect.midY))
        var x = start
        while x < end {
            x = min(x + 1, end)
            path.addLine(to: CGPoint(x: x, y: rect.midY - amplitude * sin((x - start) / 20 * 2 * .pi)))
        }
        return path
    }
}
