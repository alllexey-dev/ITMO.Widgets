import SwiftUI

/// A short status: now, changed, the place in a queue. The text carries the meaning, the tone helps scanning.
struct ItmoPill: Hashable {
    let text: String
    var tone: ItmoTone = .neutral
    var symbol: AppSymbol?
}

/// A status pill: a capsule over its tone's wash.
struct ItmoStatusPill: View {
    let pill: ItmoPill

    var body: some View {
        HStack(spacing: ItmoSpacing.related) {
            if let symbol = pill.symbol {
                symbol.image
                    .font(.itmo(.labelSmall))
                    .accessibilityHidden(true)
            }
            Text(verbatim: pill.text)
                .font(.itmo(.labelMedium, emphasized: true))
        }
        .foregroundStyle(pill.tone.color)
        .padding(.horizontal, ItmoSpacing.compact)
        .padding(.vertical, 3)
        .background(pill.tone.wash, in: .capsule)
    }
}

/// Initials in a circle.
struct ItmoAvatar: View {
    let initials: String
    var size: CGFloat = 40
    var tone: ItmoTone = .tertiary

    var body: some View {
        Text(verbatim: initials)
            .font(.system(size: size * 0.38, weight: .semibold, design: .rounded))
            .foregroundStyle(tone.color)
            .frame(width: size, height: size)
            .background(tone.color.opacity(0.16), in: .circle)
            .accessibilityHidden(true)
    }
}
