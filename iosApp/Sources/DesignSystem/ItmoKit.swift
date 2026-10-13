import SwiftUI

// The screen kit of the native screens (docs/design.md, SwiftUI screens): the scrolling column, cards, section
// headers and the tones of cards, pills and icon tiles. Native controls and system materials; colour, spacing and
// type come from the tokens. Texts reach the kit resolved, icons only as `AppSymbol`.

/// Layout values of the kit. Spacing comes from the tokens; the corner is the platform's.
enum ItmoKit {
    /// Side margin of a screen and its sheets; inset grouped lists use the same.
    static let margin = ItmoSpacing.screenMargin
    /// Between cards of a column.
    static let gap = ItmoSpacing.content
    /// Above a section header in a column (the column's `gap` included).
    static let section = ItmoSpacing.section
    /// Inside cards and rows.
    static let padding = ItmoSpacing.cardPadding
    /// Inside the one hero card of a screen.
    static let heroPadding = ItmoSpacing.summaryPadding
    /// The corner of cards and row groups: iOS 26's inset grouped container, a platform metric with no token.
    static let cardCorner: CGFloat = 26
    /// The opacity of a progress track over the hue of its container (`itmoTrack`), Android's `progressTrack` alpha.
    static let trackOpacity = 0.16
}

/// The meaning of a card, pill or icon tile. The word carries the meaning; the colour only helps scanning.
enum ItmoTone: Hashable, Sendable {
    case neutral, primary, tertiary, ok, warn, bad

    /// The colour of text and symbols in this tone, from the shared roles.
    var color: Color {
        switch self {
        case .neutral: .secondary
        case .primary: ItmoColor.primary
        case .tertiary: ItmoColor.tertiary
        case .ok: ItmoColor.success
        case .warn: ItmoColor.warning
        case .bad: ItmoColor.error
        }
    }

    /// The fill of a solid tile in this tone.
    var fill: Color {
        self == .neutral ? .gray : color
    }

    /// The wash under a pill or a tinted card.
    var wash: Color {
        fill.opacity(0.15)
    }
}

/// The standard scrolling body of a screen: side margins and `ItmoKit.gap` between children.
struct ItmoScreenStack<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ItmoKit.gap) {
                content
            }
            .padding(.horizontal, ItmoKit.margin)
            .padding(.bottom, ItmoSpacing.section)
        }
        .background(Color(.systemGroupedBackground))
    }
}

/// One topic per card. A tone tints it; `.primary` with `hero` is the one hero of a screen.
struct ItmoCard<Content: View>: View {
    var tone: ItmoTone?
    var hero = false
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            content
        }
        .padding(hero ? ItmoKit.heroPadding : ItmoKit.padding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background, in: .rect(cornerRadius: ItmoKit.cardCorner))
        .environment(\.itmoTrack, track)
    }

    /// A tinted card's tracks are grooves in its own hue, never the system's grey on a light wash.
    private var track: Color {
        guard let tone, tone != .neutral else { return Color.primary.opacity(ItmoKit.trackOpacity) }
        return tone.color.opacity(ItmoKit.trackOpacity)
    }

    private var background: Color {
        guard let tone, tone != .neutral else { return Color(.secondarySystemGroupedBackground) }
        return tone.color.opacity(0.14)
    }
}

/// The title over a group or a stack of cards, with an optional neutral `count` ("8 lessons": a fact about the
/// section, never glued to the title) and an optional action on the right ("All").
struct ItmoSectionHeader: View {
    let title: String
    var count: String?
    var action: (title: String, run: () -> Void)?

    @Environment(\.itmoOnSheet) private var onSheet

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
            Text(verbatim: title)
                .font(.itmo(.titleLarge, emphasized: true))
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            if let count {
                Text(verbatim: count)
                    .font(.itmo(.bodyMedium))
                    .monospacedDigit()
                    .foregroundStyle(.secondary)
            }
            if let action {
                Button(action: action.run) {
                    Text(verbatim: action.title).font(.itmo(.bodyMedium))
                }
                .itmoTint()
            }
        }
        // In a sheet the header sits closer to the content above.
        .padding(.top, onSheet ? ItmoSpacing.related : ItmoKit.section - ItmoKit.gap)
        .padding(.horizontal, ItmoSpacing.related)
    }
}

extension EnvironmentValues {
    /// The track of every progress bar, meter, scale and ring of the kit: the hue of the container it stands in at
    /// `ItmoKit.trackOpacity` (a tinted `ItmoCard` sets its tone), the label colour on the screen and neutral cards.
    @Entry var itmoTrack = Color.primary.opacity(ItmoKit.trackOpacity)

    /// True inside `ItmoSheet`: section headers sit closer to the content above.
    @Entry var itmoOnSheet = false
}
