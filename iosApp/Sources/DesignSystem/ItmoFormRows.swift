import SwiftUI

// Rows and sections of SwiftUI-owned `Form` screens (settings, account, sign-in). Texts arrive resolved from the
// catalog (`AppStrings`, `UiText.resolved`) and render verbatim; icons come only from `AppSymbol`.

/// A section with an optional header and footer.
struct ItmoFormSection<Content: View>: View {
    var header: String?
    var footer: String?
    @ViewBuilder var content: Content

    var body: some View {
        Section {
            content
        } header: {
            if let header { Text(verbatim: header) }
        } footer: {
            if let footer { Text(verbatim: footer) }
        }
    }
}

/// The label of a row: an optional symbol in the primary role, a title that wraps and an optional secondary line.
/// The symbol stays beside the first line at every text size and the row separator starts at the title.
struct ItmoRowLabel: View {
    let title: String
    var subtitle: String?
    var symbol: AppSymbol?
    var symbolColor: Color = ItmoColor.primary

    /// The symbol column scales with the text, so titles of rows with symbols line up.
    @ScaledMetric(relativeTo: .body) private var symbolWidth = ItmoMetrics.rowSymbolWidth

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
            if let symbol {
                symbol.image
                    .font(.itmo(.bodyLarge))
                    .foregroundStyle(symbolColor)
                    .frame(width: symbolWidth)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                Text(verbatim: title)
                    .font(.itmo(.bodyLarge))
                    .foregroundStyle(.primary)
                if let subtitle {
                    Text(verbatim: subtitle)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                }
            }
            .alignmentGuide(.listRowSeparatorLeading) { $0[.leading] }
        }
    }
}

/// A switch row; the whole row toggles.
struct ItmoToggleRow: View {
    let title: String
    var subtitle: String?
    var symbol: AppSymbol?
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            ItmoRowLabel(title: title, subtitle: subtitle, symbol: symbol)
        }
        .itmoTint()
    }
}

/// A row with a trailing value, read-only or the label of a picker's destination.
struct ItmoValueRow: View {
    let title: String
    let value: String
    var subtitle: String?
    var symbol: AppSymbol?

    var body: some View {
        LabeledContent {
            Text(verbatim: value)
                .font(.itmo(.bodyLarge))
        } label: {
            ItmoRowLabel(title: title, subtitle: subtitle, symbol: symbol)
        }
    }
}

/// A row that runs an action: tinted with the primary role, red for a destructive one.
struct ItmoActionRow: View {
    let title: String
    var symbol: AppSymbol?
    var role: ButtonRole?
    let action: () -> Void

    var body: some View {
        Button(role: role, action: action) {
            HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
                if let symbol {
                    symbol.image
                        .frame(width: symbolWidth)
                        .accessibilityHidden(true)
                }
                Text(verbatim: title)
                    .alignmentGuide(.listRowSeparatorLeading) { $0[.leading] }
            }
            .font(.itmo(.bodyLarge))
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget, alignment: .leading)
            .contentShape(Rectangle())
        }
    }

    @ScaledMetric(relativeTo: .body) private var symbolWidth = ItmoMetrics.rowSymbolWidth

    private var color: Color {
        role == .destructive ? ItmoColor.error : ItmoColor.primary
    }
}
