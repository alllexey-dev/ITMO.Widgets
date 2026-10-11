import SwiftUI
import WidgetKit

/// The texts both schedule widgets build from a lesson, as Android's `ScheduleWidgetRenderer` builds them. Every label
/// is a catalog key; the lesson's own texts come from the file.
enum LessonWidgetText {
    /// The type label: the pending sport status, or the lesson type of MyITMO's `typeId` (`lessonTypeNameRes`).
    static func type(of lesson: WidgetLesson) -> LocalizedStringResource {
        switch lesson.pendingStatus {
        case .waiting: return .scheduleWidgetPendingWaiting
        case .predicted: return .scheduleWidgetPendingPrediction
        case nil: break
        }
        switch lesson.typeId {
        case -1: return .scheduleNoLessons
        case 1: return .scheduleLessonTypeLecture
        case 2: return .scheduleLessonTypeLab
        case 3: return .scheduleLessonTypePractice
        case 5: return .scheduleLessonTypeExam
        case 6: return .scheduleLessonTypeCredit
        case 10: return .scheduleLessonTypeConsultation
        case 11: return .titleSport
        default: return .scheduleLessonTypeDefault
        }
    }

    /// The subject, or the catalog's placeholder for a blank one.
    static func subject(of lesson: WidgetLesson) -> String {
        let subject = lesson.subject.trimmingCharacters(in: .whitespacesAndNewlines)
        return subject.isEmpty ? String(localized: .scheduleUnknownSubject) : subject
    }

    /// `10:00-11:30` with an en dash.
    static func time(of lesson: WidgetLesson) -> String {
        "\(lesson.start)\u{2013}\(lesson.end)"
    }

    /// Room and building, then the teacher as surname and initials, joined by a middle dot.
    static func details(of lesson: WidgetLesson) -> String {
        let location = [lesson.room, lesson.building]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " ")
        return [location, lesson.teacher.map(compactTeacherName) ?? ""]
            .filter { !$0.isEmpty }
            .joined(separator: " \u{00B7} ")
    }

    /// The surname and up to two initials, as Android's `compactTeacherName`; a single word stays as it is.
    static func compactTeacherName(_ name: String) -> String {
        let parts = name.split(whereSeparator: \.isWhitespace).map(String.init)
        guard let surname = parts.first, parts.count >= 2 else { return parts.first ?? "" }
        let initials = parts.dropFirst().prefix(2).compactMap(\.first).map { "\($0)." }
        return ([surname] + initials).joined(separator: " ")
    }

    /// The lesson widget's supporting line: now or next, then how many lessons follow.
    static func supporting(state: WidgetLesson.State, remainingLessons: Int) -> LocalizedStringResource {
        let status = String(localized: state == .current ? .scheduleWidgetStatusNow : .scheduleWidgetStatusNext)
        let remaining = remainingLessons == 0
            ? String(localized: .scheduleWidgetLastLessonCompact)
            : String(localized: .scheduleWidgetMoreLessonsCompact(remainingLessons))
        return .scheduleWidgetSupportingText(status, remaining)
    }

    /// The day and its date for a day header; the date is the file's calendar day, formatted without a time zone.
    static func header(_ item: LessonListItem, locale: Locale) -> LocalizedStringResource {
        let day: LocalizedStringResource = item.tomorrow ? .scheduleWidgetTomorrow : .scheduleWidgetToday
        guard let date = item.dateIso.flatMap({ dayAndMonth($0, locale: locale) }) else { return day }
        return .scheduleWidgetDayHeader(String(localized: day), date)
    }

    /// `10 August` from `2026-08-10` in `locale`: the day is a plain calendar date, so it is read and written in UTC.
    static func dayAndMonth(_ iso: String, locale: Locale) -> String? {
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
        formatter.setLocalizedDateFormatFromTemplate("dMMMM")
        return formatter.string(from: date)
    }
}

/// The surface colours of the schedule widgets: the brand scheme's roles, or the app's `palette` while the widgets
/// follow its theme (`settings_widgets_theme_title`). Each follows the widget's light or dark appearance. The views read it
/// from the environment the entry view sets.
struct LessonWidgetColors {
    let background: Color
    let primaryText: Color
    let secondaryText: Color
    let accent: Color

    init(palette: WidgetPalette?) {
        guard let palette else {
            background = ItmoColor.surfaceContainer
            primaryText = ItmoColor.onSurface
            secondaryText = ItmoColor.onSurfaceVariant
            accent = ItmoColor.primary
            return
        }
        func color(_ role: KeyPath<WidgetColorRoles, UInt32>) -> Color {
            Color(light: palette.light[keyPath: role], dark: palette.dark[keyPath: role])
        }
        background = color(\.surfaceContainer)
        primaryText = color(\.onSurface)
        secondaryText = color(\.onSurfaceVariant)
        accent = color(\.primary)
    }
}

private struct LessonWidgetColorsKey: EnvironmentKey {
    static let defaultValue = LessonWidgetColors(palette: nil)
}

extension EnvironmentValues {
    /// The schedule widget colours of the entry being drawn.
    var lessonWidgetColors: LessonWidgetColors {
        get { self[LessonWidgetColorsKey.self] }
        set { self[LessonWidgetColorsKey.self] = newValue }
    }
}

/// The schedule widgets' colours that no theme changes: the lesson types of the design tokens, the completed fade.
enum LessonWidgetColor {

    /// `lessonTypeColorRes` of Android.
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

    /// Completed lessons fade as Android's (`COMPLETED_ALPHA`).
    static func opacity(of lesson: WidgetLesson) -> Double {
        lesson.state == .completed ? 0.62 : 1
    }
}

/// The type marker before a lesson: a filled dot or a short line in the type colour, an outlined dot for a pending
/// sport row (it is not a booking yet).
struct LessonMarker: View {
    let lesson: WidgetLesson
    let style: LessonMarkerStyle
    var size: CGFloat = 7

    var body: some View {
        let color = LessonWidgetColor.type(lesson.typeId)
        Group {
            if lesson.pendingStatus != nil {
                Circle().strokeBorder(color, lineWidth: 1.5)
                    .frame(width: size + 1, height: size + 1)
            } else if style == .line {
                Capsule().fill(color).frame(width: 3, height: size * 2)
            } else {
                Circle().fill(color).frame(width: size, height: size)
            }
        }
        .accessibilityHidden(true)
    }
}

/// A state without lessons: an icon, the message and what a tap does.
struct LessonWidgetMessage: View {
    let symbol: AppSymbol
    let title: LocalizedStringResource
    let hint: LocalizedStringResource?
    let scale: Double

    @Environment(\.lessonWidgetColors) private var colors
    @ScaledMetric(relativeTo: .subheadline) private var titleSize: CGFloat = 14
    @ScaledMetric(relativeTo: .caption2) private var hintSize: CGFloat = 11

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: symbol.systemName)
                .font(.system(size: titleSize * scale * 1.3))
                .foregroundStyle(colors.accent)
                .accessibilityHidden(true)
            Text(title)
                .font(.system(size: titleSize * scale, weight: .semibold))
                .foregroundStyle(colors.primaryText)
            if let hint {
                Text(hint)
                    .font(.system(size: hintSize * scale))
                    .foregroundStyle(colors.secondaryText)
            }
        }
        .multilineTextAlignment(.center)
        .lineLimit(3)
        .minimumScaleFactor(0.8)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }
}

/// The widgets' states that show no lesson, shared by both widgets and the Lock Screen.
enum LessonWidgetState {
    case signedOut
    case demo
    case unavailable
    case loading
    case emptyToday
    case emptyTodayAndTomorrow
    case noMoreToday

    var symbol: AppSymbol {
        switch self {
        case .signedOut: .login
        case .demo: .eventNote
        case .unavailable: .refresh
        case .loading: .history
        case .emptyToday, .emptyTodayAndTomorrow, .noMoreToday: .eventNote
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .signedOut: .scheduleWidgetSignedOut
        case .demo: .StringsAuth.demoEntered
        case .unavailable: .scheduleWidgetError
        case .loading: .scheduleWidgetLoading
        case .emptyToday: .scheduleWidgetEmptyToday
        case .emptyTodayAndTomorrow: .scheduleWidgetEmptyTodayAndTomorrow
        case .noMoreToday: .scheduleWidgetNoMoreToday
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

/// The sample the widget gallery shows (`TimelineProvider.placeholder`): catalog subjects, no user data.
enum LessonWidgetPreview {
    static var snapshot: LessonWidgetSnapshot {
        let math = WidgetLesson(
            subject: String(localized: .widgetPreviewSubjectMath),
            start: "10:00",
            end: "11:30",
            typeId: 1,
            teacher: String(localized: .StringsSchedule.widgetPreviewTeacher),
            room: "1404",
            building: nil,
            state: .current,
            pendingStatus: nil
        )
        let programming = WidgetLesson(
            subject: String(localized: .widgetPreviewSubjectProgramming),
            start: "11:40",
            end: "13:10",
            typeId: 2,
            teacher: nil,
            room: "2302",
            building: nil,
            state: .upcoming,
            pendingStatus: nil
        )
        let history = WidgetLesson(
            subject: String(localized: .widgetPreviewSubjectHistory),
            start: "13:30",
            end: "15:00",
            typeId: 3,
            teacher: nil,
            room: "1216",
            building: nil,
            state: .upcoming,
            pendingStatus: nil
        )
        return LessonWidgetSnapshot(
            singleLesson: SingleLessonContent(kind: .lesson, lesson: math, remainingLessons: 2),
            lessonList: [
                LessonListItem(kind: .header, lesson: nil, dateIso: nil, tomorrow: false),
                LessonListItem(kind: .lesson, lesson: math, dateIso: nil, tomorrow: false),
                LessonListItem(kind: .lesson, lesson: programming, dateIso: nil, tomorrow: false),
                LessonListItem(kind: .lesson, lesson: history, dateIso: nil, tomorrow: false),
                LessonListItem(kind: .end, lesson: nil, dateIso: nil, tomorrow: false),
            ],
            singleLessonStyle: .dot,
            lessonListStyle: .dot,
            compactTextSize: nil,
            fullTextSize: nil
        )
    }
}
