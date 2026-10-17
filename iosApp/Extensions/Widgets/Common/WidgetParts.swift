import SwiftUI
import WidgetKit

/// The surface colours of every widget but the QR code: the brand scheme's roles, or the app's `palette` while the
/// widgets follow its theme (`settings_widgets_theme_title`). Each follows the widget's light or dark appearance; the
/// entry views put them into the environment.
struct WidgetColors {
    let background: Color
    /// A lesson block, a booking row, the ring's track.
    let block: Color
    /// The current lesson and the next-lesson hero.
    let hero: Color
    let onHero: Color
    let primaryText: Color
    let secondaryText: Color
    let accent: Color
    /// The ring's bonus arc.
    let bonus: Color

    init(palette: WidgetPalette?) {
        guard let palette else {
            background = ItmoColor.surface
            block = ItmoColor.surfaceContainerHigh
            hero = ItmoColor.primaryContainer
            onHero = ItmoColor.onPrimaryContainer
            primaryText = ItmoColor.onSurface
            secondaryText = ItmoColor.onSurfaceVariant
            accent = ItmoColor.primary
            bonus = ItmoColor.tertiary
            return
        }
        func color(_ role: KeyPath<WidgetColorRoles, UInt32>) -> Color {
            Color(light: palette.light[keyPath: role], dark: palette.dark[keyPath: role])
        }
        background = color(\.surface)
        block = color(\.surfaceContainerHigh)
        hero = color(\.primaryContainer)
        onHero = color(\.onPrimaryContainer)
        primaryText = color(\.onSurface)
        secondaryText = color(\.onSurfaceVariant)
        accent = color(\.primary)
        bonus = color(\.tertiary)
    }

    /// The colours no theme changes: a changed room and a queue, a booking, the reached credit, the now line.
    static let warning = ItmoColor.warning
    static let success = ItmoColor.success
    static let nowLine = ItmoColor.error

    /// `lessonTypeColorRes` of Android: the type bar of a lesson.
    static func type(_ typeId: Int) -> Color {
        switch typeId {
        case -1: ItmoColor.lessonTypeFree
        case 1: ItmoColor.lessonTypeLecture
        case 2: ItmoColor.lessonTypeLab
        case 3: ItmoColor.lessonTypePractice
        case 4...9: ItmoColor.lessonTypeAssessment
        case 10: ItmoColor.lessonTypeConsultation
        case 11: ItmoColor.lessonTypeSport
        default: ItmoColor.lessonTypeDefault
        }
    }
}

private struct WidgetColorsKey: EnvironmentKey {
    static let defaultValue = WidgetColors(palette: nil)
}

private struct WidgetTextScaleKey: EnvironmentKey {
    static let defaultValue = 1.0
}

extension EnvironmentValues {
    /// The colours of the entry being drawn.
    var widgetColors: WidgetColors {
        get { self[WidgetColorsKey.self] }
        set { self[WidgetColorsKey.self] = newValue }
    }

    /// The widget's text size option (`LessonTextSize.scale`) on top of Dynamic Type.
    var widgetTextScale: Double {
        get { self[WidgetTextScaleKey.self] }
        set { self[WidgetTextScaleKey.self] = newValue }
    }
}

/// A widget font: `size` points at the default text size, grown by Dynamic Type and the widget's text size option.
private struct WidgetFont: ViewModifier {
    let size: CGFloat
    let weight: Font.Weight
    let design: Font.Design
    let monospacedDigits: Bool

    @ScaledMetric(relativeTo: .body) private var unit: CGFloat = 1
    @Environment(\.widgetTextScale) private var scale

    func body(content: Content) -> some View {
        let font = Font.system(size: size * unit * scale, weight: weight, design: design)
        content.font(monospacedDigits ? font.monospacedDigit() : font)
    }
}

extension View {
    func widgetFont(
        _ size: CGFloat, weight: Font.Weight = .regular, design: Font.Design = .default, monospacedDigits: Bool = false
    ) -> some View {
        modifier(WidgetFont(size: size, weight: weight, design: design, monospacedDigits: monospacedDigits))
    }

    /// The widget's own tile: WidgetKit's container background in a widget, the same colour painted under the view
    /// where WidgetKit ignores it (the app's gallery, snapshot tests).
    func widgetTile(_ color: Color) -> some View {
        background(color).containerBackground(for: .widget) { color }
    }
}

/// The type bar before a lesson.
struct WidgetTypeBar: View {
    let typeId: Int
    var width: CGFloat = 3

    var body: some View {
        RoundedRectangle(cornerRadius: width / 2)
            .fill(WidgetColors.type(typeId))
            .frame(width: width)
            .accessibilityHidden(true)
    }
}

/// A short status in its colour over its wash: a queue, a booking, a cancelled lesson, a gap.
struct WidgetPill: View {
    let text: String
    var color: Color?
    var size: CGFloat = 11

    @Environment(\.widgetColors) private var colors

    var body: some View {
        let tint = color ?? colors.secondaryText
        Text(verbatim: text)
            .widgetFont(size, weight: .semibold)
            .lineLimit(1)
            .foregroundStyle(tint)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background((color ?? colors.block).opacity(color == nil ? 1 : 0.15), in: .capsule)
    }
}

/// A widget without content: an icon, the message and what a tap does.
struct WidgetMessage: View {
    let state: WidgetPlaceholder

    @Environment(\.widgetColors) private var colors

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: state.symbol.systemName)
                .widgetFont(18, weight: .semibold)
                .foregroundStyle(colors.accent)
                .accessibilityHidden(true)
            Text(state.title)
                .widgetFont(14, weight: .semibold)
                .foregroundStyle(colors.primaryText)
            if let hint = state.hint {
                Text(hint)
                    .widgetFont(11)
                    .foregroundStyle(colors.secondaryText)
            }
        }
        .multilineTextAlignment(.center)
        .lineLimit(3)
        .minimumScaleFactor(0.8)
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }
}

/// The states of a widget without its content, calm and never an error dialog.
enum WidgetPlaceholder: Equatable {
    /// No session file: signed out, or a build without an App Group container.
    case signedOut
    /// The demo session: it has no ITMO.ID token, so no data reaches the widgets (docs/features/demo.md).
    case demo
    /// Signed in without a timeline for this moment: none written yet, or the last one ran out.
    case unavailable
    case loading
    /// The schedule widgets: no lesson today or tomorrow.
    case noLessons
    case sportLoading
    case sportError

    var symbol: AppSymbol {
        switch self {
        case .signedOut: .login
        case .demo, .noLessons: .eventNote
        case .unavailable, .sportError: .refresh
        case .loading, .sportLoading: .history
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .signedOut: .scheduleWidgetSignedOut
        case .demo: .StringsAuth.demoEntered
        case .unavailable: .scheduleWidgetError
        case .loading: .scheduleWidgetLoading
        case .noLessons: .scheduleWidgetEmptyTodayAndTomorrow
        case .sportLoading: .widgetSportLoading
        case .sportError: .widgetSportError
        }
    }

    /// What a tap does, for the states where the app has something to show.
    var hint: LocalizedStringResource? {
        switch self {
        case .unavailable, .demo: .scheduleWidgetOpenAppHint
        default: nil
        }
    }
}

/// The texts the widgets build from data; every label is a catalog key, numbers and times come from the files.
enum WidgetText {
    /// The development language: the app runs in Russian on any phone (ADR 0028).
    static let locale = Locale(identifier: "ru")

    /// A length in minutes in hours and minutes, abbreviated in Russian.
    static func duration(minutes: Int) -> String {
        Duration.seconds(minutes * 60).formatted(
            .units(allowed: [.hours, .minutes], width: .abbreviated).locale(locale)
        )
    }

    /// `12:22` for a minute of the day.
    static func clock(minute: Int) -> String {
        String(format: "%02d:%02d", minute / 60, minute % 60)
    }

    /// The calendar date of `iso` (`yyyy-MM-dd`) formatted with `template`; the date is a plain calendar day, so it
    /// is read and written in UTC.
    static func date(_ iso: String, template: String) -> String? {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3,
              let date = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
        else { return nil }
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.locale = locale
        formatter.setLocalizedDateFormatFromTemplate(template)
        return formatter.string(from: date)
    }

    /// The first letter capitalised, for a weekday at the start of a title.
    static func capitalized(_ text: String) -> String {
        text.prefix(1).uppercased(with: locale) + text.dropFirst()
    }
}

/// How a widget draws time: `live` in WidgetKit, where the system's timer views count down by themselves; `frozen`
/// at the sample's moment in the widgets gallery and the snapshot tests, where nothing may move.
enum WidgetClock: Equatable {
    case live
    case frozen(Date)
}

/// The minutes and hours to `target`, in Russian words. Live, it is the system's offset text, which WidgetKit
/// redraws as the minutes pass; Swift computes nothing.
enum WidgetCountdown {
    static func text(to target: Date, clock: WidgetClock, fields: Set<Date.ComponentsFormatStyle.Field> = [.hour, .minute]) -> Text {
        let style = SystemFormatStyle.DateOffset(to: target, allowedFields: fields, maxFieldCount: 2, sign: .never)
            .locale(WidgetText.locale)
        switch clock {
        case .live: return Text(.currentDate, format: style)
        case let .frozen(now): return Text(now, format: style)
        }
    }

    /// A one-placeholder template (`widget_next_left`) around the countdown, as one text: the live countdown cannot be
    /// a format argument, so the template's text before and after the placeholder are interpolated around it.
    static func text(
        _ template: (String) -> LocalizedStringResource, to target: Date, clock: WidgetClock
    ) -> Text {
        let marker = "\u{1}"
        let parts = String(localized: template(marker)).components(separatedBy: marker)
        let prefix = Text(verbatim: parts.first ?? ""), suffix = Text(verbatim: parts.dropFirst().joined())
        return Text("\(prefix)\(text(to: target, clock: clock))\(suffix)", tableName: "WidgetTexts", comment: "Not localized")
    }
}

/// The elapsed part of a lesson from `since` to `until`: the system's timer progress, live; a bar or, on the Lock
/// Screen, a ring.
struct WidgetLessonProgress: View {
    let since: Date
    let until: Date
    let clock: WidgetClock
    var ring = false

    var body: some View {
        switch clock {
        case .live:
            ProgressView(timerInterval: since...until, countsDown: false) {} currentValueLabel: { EmptyView() }
                .progressViewStyle(ring ? AnyProgressViewStyle.circular : .linear)
        case let .frozen(now):
            let elapsed = min(max(now.timeIntervalSince(since) / until.timeIntervalSince(since), 0), 1)
            if ring {
                Gauge(value: elapsed) {}.gaugeStyle(.accessoryCircularCapacity)
            } else {
                ProgressView(value: elapsed).progressViewStyle(.linear)
            }
        }
    }
}

/// The two system progress styles behind one type.
enum AnyProgressViewStyle: ProgressViewStyle {
    case linear
    case circular

    func makeBody(configuration: Configuration) -> some View {
        switch self {
        case .linear: ProgressView(configuration).progressViewStyle(.linear)
        case .circular: ProgressView(configuration).progressViewStyle(.circular)
        }
    }
}
