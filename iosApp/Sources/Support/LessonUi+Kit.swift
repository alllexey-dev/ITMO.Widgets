import Shared
import SwiftUI

// The shared lesson presentation (`core/lesson`, `LessonTimelines`) drawn by the iOS kit: texts resolved, pills
// toned, types coloured. Android's twin is the designsystem's `kit/LessonMapping.kt`.

extension LessonUi {
    /// The kit's lesson; `id` keeps it apart from the other lessons of its day.
    func kitLesson(id: String) -> ItmoLesson {
        ItmoLesson(
            id: id,
            start: Self.minutes(start),
            end: Self.minutes(end),
            subject: subject,
            type: ItmoLessonType(typeId: Int(typeId)),
            kind: byline.resolved,
            room: place?.resolved,
            was: previousPlace?.resolved,
            note: note,
            cancelled: cancelled,
            phase: ItmoLesson.Phase(state),
            pills: pills.map(\.kitPill),
            friends: withYou ? [AppStrings.string("lesson_with_you")] : []
        )
    }

    /// "08:20" as minutes since midnight.
    private static func minutes(_ text: String) -> Int {
        let parts = text.split(separator: ":").compactMap { Int($0) }
        return parts.count == 2 ? parts[0] * 60 + parts[1] : 0
    }
}

extension LessonPill {
    /// now primary, next neutral, changed and queue warn, cancelled bad, booked ok, online
    /// neutral with the camera.
    var kitPill: ItmoPill {
        switch kind {
        case .now: ItmoPill(text: text.resolved, tone: .primary)
        case .next: ItmoPill(text: text.resolved)
        case .changed, .queue: ItmoPill(text: text.resolved, tone: .warn)
        case .cancelled: ItmoPill(text: text.resolved, tone: .bad)
        case .booked: ItmoPill(text: text.resolved, tone: .ok)
        case .online: ItmoPill(text: text.resolved, symbol: .videocam)
        }
    }
}

extension Array where Element == TimelineItem {
    /// The kit's timeline rows of a day.
    var kitRows: [ItmoTimelineRow] {
        enumerated().map { index, item in
            switch onEnum(of: item) {
            case let .lesson(row):
                var lesson = row.lesson.kitLesson(id: "\(index)")
                lesson.changeNote = row.changeNote?.resolved
                lesson.overlapNote = row.overlapNote?.resolved
                return .lesson(lesson, progress: CGFloat(row.progress), reached: row.reached)
            case let .gap(gap):
                return .gap(id: "gap-\(index)", label: gap.label.resolved, progress: CGFloat(gap.progress))
            }
        }
    }
}

extension ItmoLesson.Phase {
    init(_ state: LessonState) {
        switch state {
        case .upcoming: self = .upcoming
        case .next: self = .next
        case .now: self = .now
        case .done: self = .done
        }
    }
}

extension ItmoLessonType {
    /// MyITMO's lesson type ids as every feature colours them.
    init(typeId: Int) {
        switch typeId {
        case -1: self = .free
        case Int(LessonTypeNamesKt.ROOM_BOOKING_TYPE_ID): self = .booking
        case 1: self = .lecture
        case 2: self = .lab
        case 3: self = .practice
        case 4...9: self = .assessment
        case 10: self = .consultation
        case 11: self = .sport
        default: self = .other
        }
    }
}
