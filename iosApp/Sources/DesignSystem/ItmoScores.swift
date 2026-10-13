import SwiftUI

// Numbers of the kit (docs/design.md, SwiftUI screens): a number over its quiet source, the 0-100 meter of a row,
// the small sport ring, the grade scale of a hero and the votes of a link. Texts reach the kit resolved.

/// A number column of a row: the value (a meter, a pill, a ring) over its quiet source ("BARS", "My ITMO"). `stale`
/// colours the source as a warning (an old answer with its date); with `onSource` the source opens where the number
/// comes from.
struct ItmoValueWithSource<Value: View>: View {
    let source: String
    var stale = false
    var onSource: (() -> Void)?
    var sourceLabel: String?
    @ViewBuilder var value: Value

    var body: some View {
        VStack(alignment: .trailing, spacing: ItmoSpacing.related) {
            value
            if let onSource {
                Button(action: onSource) { caption }
                    .buttonStyle(.borderless)
                    .accessibilityLabel(Text(verbatim: sourceLabel ?? source))
            } else {
                caption
            }
        }
    }

    private var caption: some View {
        Text(verbatim: source)
            .font(.itmo(.labelSmall))
            .foregroundStyle(stale ? ItmoColor.warning : .secondary)
            .lineLimit(1)
    }
}

/// Points out of 100 in a row: the number over a thin bar filled to `share` in `tone`.
struct ItmoPointsMeter: View {
    let text: String
    let share: Double
    var tone: ItmoTone = .primary

    var body: some View {
        VStack(alignment: .trailing, spacing: ItmoSpacing.related) {
            Text(verbatim: text)
                .font(.itmo(.titleLarge, emphasized: true))
                .monospacedDigit()
            Capsule()
                .fill(Color(.tertiarySystemFill))
                .frame(width: 40, height: 4)
                .overlay(alignment: .leading) {
                    Capsule().fill(tone.color).frame(width: 40 * min(max(share, 0), 1))
                }
                .accessibilityHidden(true)
        }
    }
}

/// The sport points in a small ring: visits and bonus as shares of the turn in the sport colours, the total in the
/// middle.
struct ItmoSportRing: View {
    let text: String
    let visits: Double
    let bonus: Double
    var size: CGFloat = 44

    var body: some View {
        ZStack {
            Circle().stroke(Color(.tertiarySystemFill), lineWidth: 4)
            arc(from: 0, to: visits, color: ItmoColor.sportScoreAttendance)
            arc(from: visits, to: visits + bonus, color: ItmoColor.sportScoreBonus)
            Text(verbatim: text)
                .font(.itmo(.labelLarge, emphasized: true))
                .monospacedDigit()
                .minimumScaleFactor(0.7)
        }
        .frame(width: size, height: size)
    }

    private func arc(from start: Double, to end: Double, color: Color) -> some View {
        Circle()
            .trim(from: min(start, 1), to: min(end, 1))
            .stroke(color, style: StrokeStyle(lineWidth: 4, lineCap: .round))
            .rotationEffect(.degrees(-90))
    }
}

/// The 100-point bar of a hero with a tick and its label at each threshold (E 60, D 68 and so on).
struct ItmoGradeScale: View {
    let share: Double
    let ticks: [(label: String, score: Double)]

    var body: some View {
        VStack(spacing: ItmoSpacing.related) {
            GeometryReader { geometry in
                let width = geometry.size.width
                ZStack(alignment: .leading) {
                    Capsule().fill(ItmoColor.primary.opacity(0.18))
                    Capsule().fill(ItmoColor.primary).frame(width: width * min(max(share, 0), 1))
                    ForEach(ticks.indices, id: \.self) { index in
                        Rectangle()
                            .fill(Color(.secondarySystemGroupedBackground))
                            .frame(width: 2)
                            .offset(x: width * ticks[index].score / 100 - 1)
                    }
                }
            }
            .frame(height: 10)
            GeometryReader { geometry in
                ForEach(ticks.indices, id: \.self) { index in
                    Text(verbatim: ticks[index].label)
                        .font(.itmo(.labelSmall))
                        .foregroundStyle(.secondary)
                        .fixedSize()
                        .frame(width: 40)
                        .offset(x: geometry.size.width * ticks[index].score / 100 - 20)
                }
            }
            .frame(height: 16)
        }
        .accessibilityHidden(true)
    }
}

/// The votes of a link: ▲ score ▼ in a capsule, the viewer's own vote in the tint. Without `enabled` only the score.
struct ItmoVotePill: View {
    let score: Int
    let myVote: Int
    let upLabel: String
    let downLabel: String
    var enabled = true
    let onUp: () -> Void
    let onDown: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            if enabled { arrow(.arrowDropUp, label: upLabel, chosen: myVote > 0, action: onUp) }
            Text(verbatim: "\(score)")
                .font(.itmo(.labelLarge, emphasized: true))
                .monospacedDigit()
                .foregroundStyle(myVote != 0 ? ItmoColor.primary : Color.primary)
                .padding(.horizontal, enabled ? 0 : ItmoSpacing.compact)
            if enabled { arrow(.arrowDropDown, label: downLabel, chosen: myVote < 0, action: onDown) }
        }
        .padding(.vertical, 2)
        .background(Color(.tertiarySystemFill), in: .capsule)
    }

    private func arrow(_ symbol: AppSymbol, label: String, chosen: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            symbol.image
                .font(.itmo(.labelSmall))
                .foregroundStyle(chosen ? ItmoColor.primary : .secondary)
                .frame(width: 32, height: 32)
                .contentShape(.rect)
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(Text(verbatim: label))
    }
}
