import SwiftUI

/// The kind of a lesson, which colours its bar or dot (the `lessonType*` tokens).
enum ItmoLessonType: Hashable, Sendable {
    case lecture, practice, lab, assessment, consultation, sport, free, booking, other

    var color: Color {
        switch self {
        case .lecture: ItmoColor.lessonTypeLecture
        case .practice: ItmoColor.lessonTypePractice
        case .lab: ItmoColor.lessonTypeLab
        case .assessment: ItmoColor.lessonTypeAssessment
        case .consultation: ItmoColor.lessonTypeConsultation
        case .sport: ItmoColor.lessonTypeSport
        case .free: ItmoColor.lessonTypeFree
        case .booking: ItmoColor.tertiary
        case .other: ItmoColor.lessonTypeDefault
        }
    }
}

/// One lesson as every lesson view shows it, texts resolved by the caller: subject; "kind, teacher";
/// "room, building" (no line when online, an "online" pill says it); the lesson's note; pills.
struct ItmoLesson: Hashable, Identifiable {
    /// Where the lesson stands at the shown moment: done lessons are dimmed, the current one is tinted.
    enum Phase: Hashable, Sendable { case done, now, next, upcoming }

    let id: String
    /// Minutes since the day's midnight.
    let start: Int
    let end: Int
    let subject: String
    let type: ItmoLessonType
    /// The lesson kind: "Lecture".
    let kind: String
    /// The teacher's short name: "Surname I. O.".
    var teacher: String?
    var room: String?
    /// The building's short name; the full address belongs to a hero or a sheet.
    var building: String?
    /// The old room or time of a changed lesson, struck through before the new place.
    var was: String?
    var note: String?
    /// A quiet line under the lesson in a day's timeline about its change (when it was changed).
    var changeNote: String?
    /// A quiet line under the later of two lessons at the same time ("at the same time as ...").
    var overlapNote: String?
    var cancelled = false
    var phase: Phase = .upcoming
    /// Status pills in display order: now or next, changed or cancelled, the queue, booked, online.
    var pills: [ItmoPill] = []
    /// Initials of friends at this lesson, at most three shown.
    var friends: [String] = []

    var place: String {
        [room, building].compactMap { $0 }.joined(separator: ", ")
    }
}

/// "08:20" for 500 minutes.
func itmoClockTime(_ minutes: Int) -> String {
    String(format: "%02ld:%02ld", minutes / 60, minutes % 60)
}

/// A single lesson outside a day's list (a hero's neighbour, a sport booking): the time column, the type bar and the
/// details on a card. A day's lessons are always an `ItmoLessonTimeline`.
struct ItmoLessonCard: View {
    let lesson: ItmoLesson

    /// The time column grows with the text, so "11:40" never wraps.
    @ScaledMetric(relativeTo: .subheadline) private var timeColumn = ItmoLessonTimes.column

    var body: some View {
        HStack(alignment: .top, spacing: ItmoSpacing.content) {
            ItmoLessonTimes(lesson: lesson)
                .frame(width: timeColumn, alignment: .leading)
            RoundedRectangle(cornerRadius: 2)
                .fill(lesson.type.color)
                .frame(width: 4)
                .accessibilityHidden(true)
            ItmoLessonDetails(lesson: lesson)
            Spacer(minLength: 0)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(ItmoKit.padding)
        .background(
            lesson.phase == .now ? ItmoColor.primary.opacity(0.14) : Color(.secondarySystemGroupedBackground),
            in: .rect(cornerRadius: ItmoKit.cardCorner)
        )
        .opacity(lesson.phase == .done && !lesson.cancelled ? 0.6 : 1)
        .accessibilityElement(children: .combine)
    }
}

/// The start over the end of a lesson.
struct ItmoLessonTimes: View {
    /// The width of the time column at the default text size.
    static let column: CGFloat = 48

    let lesson: ItmoLesson

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(verbatim: itmoClockTime(lesson.start))
                .font(.itmo(.bodyMedium, emphasized: true))
                .foregroundStyle(lesson.phase == .now ? ItmoColor.primary : Color.primary)
            Text(verbatim: itmoClockTime(lesson.end))
                .font(.itmo(.labelMedium))
                .foregroundStyle(.secondary)
        }
        .monospacedDigit()
    }
}

/// What a lesson says, in the order of every lesson view. Shared by `ItmoLessonCard` and `ItmoLessonTimeline`.
struct ItmoLessonDetails: View {
    let lesson: ItmoLesson

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: lesson.subject)
                .font(.itmo(.bodyLarge, emphasized: true))
                .strikethrough(lesson.cancelled)
                .lineLimit(2)
            Text(verbatim: [lesson.kind, lesson.teacher].compactMap { $0 }.joined(separator: ", "))
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
            // Online lessons have no place: the line is skipped and the online pill says it.
            if !lesson.place.isEmpty {
                HStack(spacing: 6) {
                    if let was = lesson.was {
                        Text(verbatim: was).strikethrough().foregroundStyle(.secondary)
                    }
                    Text(verbatim: lesson.place)
                        .foregroundStyle(lesson.was == nil ? Color.secondary : ItmoTone.warn.color)
                        .fontWeight(lesson.was == nil ? nil : .semibold)
                }
                .font(.itmo(.bodyMedium))
                .lineLimit(1)
            }
            if let note = lesson.note {
                Text(verbatim: note)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            if !lesson.pills.isEmpty || !lesson.friends.isEmpty {
                ItmoFlow(spacing: 6) {
                    ForEach(lesson.pills, id: \.self) { ItmoStatusPill(pill: $0) }
                    if !lesson.friends.isEmpty {
                        HStack(spacing: -6) {
                            ForEach(lesson.friends.prefix(3), id: \.self) { ItmoAvatar(initials: $0, size: 22) }
                        }
                    }
                }
                .padding(.top, ItmoSpacing.related)
            }
        }
    }
}

/// Pills or tags wrapped onto lines.
struct ItmoFlow: Layout {
    var spacing: CGFloat = ItmoSpacing.compact

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let lines = arrange(subviews, width: width)
        let height = lines.last.map { $0.y + $0.height } ?? 0
        let used = lines.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? used, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for line in arrange(subviews, width: bounds.width) {
            var x = bounds.minX
            for index in line.items {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + line.y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Line {
        var items: [Int] = []
        var y: CGFloat = 0
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> [Line] {
        var lines: [Line] = []
        var line = Line()
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if !line.items.isEmpty, line.width + spacing + size.width > width {
                lines.append(line)
                line = Line(y: line.y + line.height + spacing)
            }
            line.width += (line.items.isEmpty ? 0 : spacing) + size.width
            line.height = max(line.height, size.height)
            line.items.append(index)
        }
        if !line.items.isEmpty { lines.append(line) }
        return lines
    }
}
