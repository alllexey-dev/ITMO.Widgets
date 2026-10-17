import Foundation

/// The texts both schedule widgets build from a snapshot. Every label is a catalog key; the lesson's own texts and
/// the times come from the file.
enum ScheduleWidgetText {
    /// The lesson type of MyITMO's `typeId` (`lessonTypeNameRes`).
    static func type(of lesson: WidgetLesson) -> LocalizedStringResource {
        switch lesson.typeId {
        case -1: .scheduleNoLessons
        case 1: .scheduleLessonTypeLecture
        case 2: .scheduleLessonTypeLab
        case 3: .scheduleLessonTypePractice
        case 5: .scheduleLessonTypeExam
        case 6: .scheduleLessonTypeCredit
        case 10: .scheduleLessonTypeConsultation
        case 11: .titleSport
        default: .scheduleLessonTypeDefault
        }
    }

    /// The subject, or the catalog's placeholder for a blank one.
    static func subject(of lesson: WidgetLesson) -> String {
        let subject = lesson.subject.trimmingCharacters(in: .whitespacesAndNewlines)
        return subject.isEmpty ? String(localized: .scheduleUnknownSubject) : subject
    }

    /// The start and the end joined by an en dash.
    static func time(of lesson: WidgetLesson) -> String {
        "\(lesson.start)\u{2013}\(lesson.end)"
    }

    /// The room and the building, online, or nil without a place.
    static func place(of lesson: WidgetLesson) -> String? {
        if lesson.online { return String(localized: .StringsCommon.lessonPillOnline) }
        let parts = [lesson.room, lesson.building].compactMap { $0?.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }

    /// A queue's pill: the place in it when Backend knows it.
    static func queue(of lesson: WidgetLesson) -> String {
        lesson.queuePosition > 0 && lesson.queueTotal > 0
            ? String(localized: .StringsCommon.lessonPillQueuePosition(lesson.queuePosition, lesson.queueTotal))
            : String(localized: .StringsCommon.lessonPillQueue)
    }

    /// The next-lesson headline of each moment (`widget_next_now`, `_first`, `_next`, `_tomorrow`).
    static func headline(_ moment: NextLessonMoment, lesson: WidgetLesson) -> String {
        switch moment {
        case .now: String(localized: .widgetNextNow(lesson.end))
        case .first: String(localized: .widgetNextFirst(lesson.start))
        case .next: String(localized: .widgetNextNext(lesson.start))
        case .tomorrow: String(localized: .widgetNextTomorrow(lesson.start))
        }
    }

    /// The label over the lessons after the hero (`widget_next_rest_*`).
    static func restLabel(_ moment: NextLessonMoment) -> String {
        switch moment {
        case .now, .first: String(localized: .widgetNextRestNext)
        case .next: String(localized: .widgetNextRestThen)
        case .tomorrow: String(localized: .widgetNextRestTomorrow)
        }
    }

    /// The Lock Screen line beside the date: the room until the end or from the start, or tomorrow's start.
    static func inline(_ moment: NextLessonMoment, lesson: WidgetLesson) -> String {
        let place = lesson.online ? String(localized: .StringsCommon.lessonPillOnline) : lesson.room ?? subject(of: lesson)
        switch moment {
        case .now: return String(localized: .iosWidgetInlineUntil(place, lesson.end))
        case .first, .next: return String(localized: .iosWidgetInlineAt(place, lesson.start))
        case .tomorrow: return String(localized: .widgetNextTomorrow(lesson.start))
        }
    }

    /// The day widget's title: the weekday and the date, or `widget_day_tomorrow` with the date.
    static func dayTitle(_ day: DayContent) -> String? {
        guard let iso = day.date, let dayAndMonth = WidgetText.date(iso, template: "dMMMM") else { return nil }
        if day.tomorrow { return String(localized: .widgetDayTomorrow(dayAndMonth)) }
        guard let weekday = WidgetText.date(iso, template: "EEEE") else { return nil }
        return "\(WidgetText.capitalized(weekday)), \(dayAndMonth)"
    }

    /// The day widget's subtitle of each summary kind (`widget_day_remaining`, `widget_day_planned`, `widget_next_over`,
    /// `widget_next_none_today`).
    static func summary(_ summary: DaySummary) -> String? {
        switch summary.kind {
        case .over: return String(localized: .widgetNextOver)
        case .noneToday: return String(localized: .widgetNextNoneToday)
        case .planned:
            guard let count = count(summary), let from = summary.from, let to = summary.to else { return nil }
            return String(localized: .widgetDayPlanned(count, from, to))
        case .remaining:
            guard let count = count(summary), let to = summary.to else { return nil }
            return String(localized: .widgetDayRemaining(count, to))
        }
    }

    /// The lessons and the sport the subtitle counts.
    private static func count(_ summary: DaySummary) -> String? {
        let lessons = summary.lessons > 0 ? String(localized: .widgetDayLessons(summary.lessons)) : nil
        switch (lessons, summary.sport) {
        case let (lessons?, true): return String(localized: .widgetDayWithSport(lessons))
        case let (lessons?, false): return lessons
        case (nil, true): return String(localized: .widgetDaySportOnly)
        case (nil, false): return nil
        }
    }
}
