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

/// A public avatar URL, with initials while loading, when absent or on failure. No account headers, as on Android.
struct ItmoAvatar: View {
    let initials: String
    var pictureUrl: String?
    var size: CGFloat = 40
    var tone: ItmoTone = .tertiary

    var body: some View {
        AsyncImage(url: url) { phase in
            ItmoAvatarContent(initials: initials, phase: phase, size: size, tone: tone)
        }
        .id(url)
    }

    private var url: URL? {
        guard let value = pictureUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            return nil
        }
        return URL(string: value)
    }
}

/// The same fixed, clipped circle for every loading phase; kept separate for deterministic image snapshots.
struct ItmoAvatarContent: View {
    let initials: String
    let phase: AsyncImagePhase
    var size: CGFloat = 40
    var tone: ItmoTone = .tertiary

    var body: some View {
        ZStack {
            tone.color.opacity(0.16)
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else {
                Text(verbatim: initials)
                    .font(.system(size: size * 0.38, weight: .semibold, design: .rounded))
                    .foregroundStyle(tone.color)
            }
        }
        .frame(width: size, height: size)
        .clipShape(.circle)
        .accessibilityHidden(true)
    }
}
