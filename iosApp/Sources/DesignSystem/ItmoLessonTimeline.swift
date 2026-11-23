import SwiftUI

/// One row of an `ItmoLessonTimeline`, computed by the shared presentation (`core/lesson/LessonTimelines`): a lesson
/// with the elapsed share of the rail under its dot (the lesson and the short break after it) and whether the rail
/// above it is reached, a gap of an hour or more with its label ("a 2 h 10 min window") and its elapsed share, or the
/// last row, where the day's lessons end: the time in minutes, whether the day got there, a label on today.
enum ItmoTimelineRow: Identifiable {
    case lesson(ItmoLesson, progress: CGFloat, reached: Bool)
    case gap(id: String, label: String, progress: CGFloat)
    case end(time: Int, reached: Bool, label: String?)

    var id: String {
        switch self {
        case let .lesson(lesson, _, _): lesson.id
        case let .gap(id, _, _): id
        case .end: "end"
        }
    }
}

/// The lessons of one day on a time rail, the app's only day list (a single lesson is an `ItmoLessonCard`): the time
/// column, a rail with a dot per lesson in its type colour and the lesson's details without a card; done lessons are
/// dimmed, the current one tinted, with the time left beside a bar; an end row closes the rail with the time the day ends, which the lesson above it
/// then leaves out. The rows arrive computed (`ItmoTimelineRow`); `showsNow` puts the now marker where
/// the elapsed part ends inside the current lesson or gap (today), another day shows none. With `onLesson` every
/// lesson is a button that reports its index in `rows` (the lesson sheet).
struct ItmoLessonTimeline: View {
    let rows: [ItmoTimelineRow]
    var showsNow = false
    var onLesson: ((Int) -> Void)?

    var body: some View {
        let lessons = rows.indices.filter { if case .lesson = rows[$0] { true } else { false } }
        let end: Int? = if case let .end(time, _, _) = rows.last { time } else { nil }
        VStack(alignment: .leading, spacing: 0) {
            ForEach(rows.indices, id: \.self) { index in
                switch rows[index] {
                case let .gap(_, label, progress):
                    TimelineGap(text: label, progress: progress, marker: showsNow)
                case let .end(time, reached, label):
                    TimelineEnd(time: time, reached: reached, label: label)
                case let .lesson(lesson, progress, reached):
                    let row = TimelineLessonRow(
                        lesson: lesson, first: index == lessons.first, last: index == lessons.last && end == nil,
                        // The mark right under the last lesson says this time.
                        showsEnd: !(index == lessons.last && lesson.end == end), reached: reached,
                        progress: progress, marker: showsNow
                    )
                    if let onLesson {
                        Button { onLesson(index) } label: { row.contentShape(.rect) }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("timeline.lesson.\(lesson.id)")
                    } else {
                        row
                    }
                }
            }
        }
    }
}

private enum Rail {
    static let width: CGFloat = 24
    static let passed = ItmoColor.primary
    static let ahead = Color(.separator)
    /// The screen and the kit sheet, which the dots cut the rail with.
    static let under = Color(.systemGroupedBackground)

    /// A stretch from `top` to `bottom`: elapsed up to `progress`, the now marker at the split when `marker`.
    static func draw(
        _ context: GraphicsContext, _ size: CGSize, _ top: CGFloat, _ bottom: CGFloat, _ progress: CGFloat,
        marker: Bool, dashed: Bool = false
    ) {
        let x = size.width / 2
        let split = top + (bottom - top) * progress
        let style = StrokeStyle(lineWidth: 2, dash: dashed ? [3, 3] : [])
        func line(_ from: CGFloat, _ to: CGFloat, _ color: Color) {
            guard to > from else { return }
            var path = Path()
            path.move(to: CGPoint(x: x, y: from))
            path.addLine(to: CGPoint(x: x, y: to))
            context.stroke(path, with: .color(color), style: style)
        }
        line(top, split, passed)
        line(split, bottom, ahead)
        if marker, progress > 0, progress < 1 {
            dot(context, CGPoint(x: x, y: split), 5, passed)
            dot(context, CGPoint(x: x, y: split), 2, under)
        }
    }

    static func dot(_ context: GraphicsContext, _ center: CGPoint, _ radius: CGFloat, _ color: Color) {
        let rect = CGRect(x: center.x - radius, y: center.y - radius, width: 2 * radius, height: 2 * radius)
        context.fill(Path(ellipseIn: rect), with: .color(color))
    }
}

private struct TimelineLessonRow: View {
    let lesson: ItmoLesson
    let first: Bool
    let last: Bool
    let showsEnd: Bool
    let reached: Bool
    let progress: CGFloat
    let marker: Bool

    @ScaledMetric(relativeTo: .subheadline) private var timeColumn = ItmoLessonTimes.column
    /// The dot sits on the first line of the start time.
    @ScaledMetric(relativeTo: .subheadline) private var dotY: CGFloat = 19

    var body: some View {
        let now = lesson.phase == .now
        let dotY = dotY
        let done = lesson.phase == .done
        HStack(alignment: .top, spacing: 0) {
            ItmoLessonTimes(lesson: lesson, showsEnd: showsEnd)
                .frame(width: timeColumn, alignment: .leading)
                .padding(.top, 10)
                .opacity(done ? 0.6 : 1)
            Canvas { context, size in
                if !first { Rail.draw(context, size, 0, dotY, reached ? 1 : 0, marker: false) }
                Rail.draw(context, size, dotY, last ? size.height - 6 : size.height, progress, marker: marker)
                let center = CGPoint(x: size.width / 2, y: dotY)
                if lesson.cancelled {
                    let ring = CGRect(x: center.x - 5, y: center.y - 5, width: 10, height: 10)
                    context.stroke(Path(ellipseIn: ring), with: .color(lesson.type.color), lineWidth: 2)
                } else {
                    Rail.dot(context, center, now ? 9 : 7, Rail.under)
                    Rail.dot(context, center, now ? 7 : 5, lesson.type.color.opacity(done ? 0.5 : 1))
                }
            }
            .frame(width: Rail.width)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                ItmoLessonDetails(lesson: lesson)
                if let remaining = lesson.remaining {
                    HStack(spacing: ItmoSpacing.content) {
                        ProgressView(value: Double(lesson.elapsed ?? 0))
                            .tint(ItmoColor.primary)
                            .accessibilityHidden(true)
                        Text(verbatim: remaining)
                            .font(.itmo(.labelMedium, emphasized: true))
                            .foregroundStyle(ItmoColor.primary)
                            .monospacedDigit()
                    }
                }
                if let overlapNote = lesson.overlapNote {
                    Text(verbatim: overlapNote).font(.itmo(.labelMedium)).foregroundStyle(.secondary)
                }
                if let changeNote = lesson.changeNote {
                    HStack(spacing: 6) {
                        AppSymbol.history.image.foregroundStyle(ItmoTone.warn.color).accessibilityHidden(true)
                        Text(verbatim: changeNote).foregroundStyle(.secondary)
                    }
                    .font(.itmo(.labelMedium))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, ItmoSpacing.content)
            .padding(.vertical, ItmoSpacing.compact)
            .background(now ? ItmoColor.primary.opacity(0.14) : .clear, in: .rect(cornerRadius: 20))
            .opacity(done ? 0.6 : 1)
            .padding(.leading, ItmoSpacing.related)
            .padding(.bottom, 6)
        }
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityElement(children: .combine)
    }
}

private struct TimelineGap: View {
    let text: String
    let progress: CGFloat
    let marker: Bool

    @ScaledMetric(relativeTo: .subheadline) private var timeColumn = ItmoLessonTimes.column

    var body: some View {
        HStack(spacing: 0) {
            Spacer().frame(width: timeColumn)
            Canvas { context, size in
                Rail.draw(context, size, 0, size.height, progress, marker: marker, dashed: true)
            }
            .frame(width: Rail.width)
            .accessibilityHidden(true)
            Text(verbatim: text)
                .font(.itmo(.labelMedium))
                .foregroundStyle(.secondary)
                .padding(.leading, ItmoSpacing.group)
            Spacer()
        }
        .frame(minHeight: 30)
    }
}

private struct TimelineEnd: View {
    let time: Int
    let reached: Bool
    let label: String?

    @ScaledMetric(relativeTo: .subheadline) private var timeColumn = ItmoLessonTimes.column

    var body: some View {
        HStack(spacing: 0) {
            Text(verbatim: itmoClockTime(time))
                .font(.itmo(.labelMedium))
                .foregroundStyle(.secondary)
                .monospacedDigit()
                .frame(width: timeColumn, alignment: .leading)
            Canvas { context, size in
                let color = reached ? Rail.passed : Rail.ahead
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                var path = Path()
                path.move(to: CGPoint(x: center.x, y: 0))
                path.addLine(to: CGPoint(x: center.x, y: center.y - 4))
                path.addEllipse(in: CGRect(x: center.x - 4, y: center.y - 4, width: 8, height: 8))
                context.stroke(path, with: .color(color), lineWidth: 2)
            }
            .frame(width: Rail.width)
            .accessibilityHidden(true)
            if let label {
                Text(verbatim: label)
                    .font(.itmo(.labelMedium))
                    .foregroundStyle(.secondary)
                    .padding(.leading, ItmoSpacing.group)
            }
            Spacer(minLength: 0)
        }
        .frame(minHeight: 28)
        .accessibilityElement(children: .combine)
    }
}
