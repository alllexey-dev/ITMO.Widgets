import SwiftUI

/// The lessons of one day on a time rail, the app's only day list (a single lesson is an `ItmoLessonCard`): the time
/// column, a rail with a dot per lesson in its type colour and the lesson's details without a card; done lessons are
/// dimmed, the current one tinted.
///
/// `now` (minutes since midnight) draws the elapsed part of the day in the accent colour and puts the now marker in
/// the current lesson or gap; without it (another day) done lessons count as elapsed and there is no marker. Gaps of
/// `gapMinutes` or more get a row with `gapLabel(minutes)` ("a 2 h 10 min window").
struct ItmoLessonTimeline: View {
    let lessons: [ItmoLesson]
    var now: Int?
    var gapMinutes = 60
    let gapLabel: (Int) -> String

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(rows) { row in
                switch row.kind {
                case let .gap(minutes, progress):
                    TimelineGap(text: gapLabel(minutes), progress: progress, marker: now != nil)
                case let .lesson(lesson, first, last, reached, progress):
                    TimelineLessonRow(
                        lesson: lesson, first: first, last: last, reached: reached, progress: progress,
                        marker: now != nil
                    )
                }
            }
        }
    }

    private var rows: [TimelineRow] {
        var rows: [TimelineRow] = []
        for (index, lesson) in lessons.enumerated() {
            if index > 0 {
                let from = lessons[index - 1].end
                if lesson.start - from >= gapMinutes {
                    let progress = now.map { share($0, from, lesson.start) } ?? (lesson.elapsed ? 1 : 0)
                    rows.append(TimelineRow(id: "gap-\(lesson.id)", kind: .gap(lesson.start - from, progress)))
                }
            }
            let next = index + 1 < lessons.count ? lessons[index + 1].start : nil
            // The rail under a dot spans the lesson and the short break after it, up to the next dot.
            let until = next.map { $0 - lesson.end < gapMinutes ? $0 : lesson.end } ?? lesson.end
            let progress = now.map { share($0, lesson.start, until) } ?? (lesson.phase == .done ? 1 : 0)
            let reached = now.map { $0 >= lesson.start } ?? lesson.elapsed
            rows.append(TimelineRow(
                id: lesson.id,
                kind: .lesson(lesson, first: index == 0, last: next == nil, reached: reached, progress: progress)
            ))
        }
        return rows
    }
}

private struct TimelineRow: Identifiable {
    enum Kind {
        case gap(Int, CGFloat)
        case lesson(ItmoLesson, first: Bool, last: Bool, reached: Bool, progress: CGFloat)
    }

    let id: String
    let kind: Kind
}

private extension ItmoLesson {
    var elapsed: Bool { phase == .done || phase == .now }
}

/// Where `time` is inside [from, to), 0...1.
private func share(_ time: Int, _ from: Int, _ to: Int) -> CGFloat {
    guard to > from else { return time >= to ? 1 : 0 }
    return min(max(CGFloat(time - from) / CGFloat(to - from), 0), 1)
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
            ItmoLessonTimes(lesson: lesson)
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
                if let changeNote = lesson.changeNote {
                    HStack(spacing: 6) {
                        AppSymbol.history.image.foregroundStyle(.orange).accessibilityHidden(true)
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
