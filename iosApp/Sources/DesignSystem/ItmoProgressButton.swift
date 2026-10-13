import SwiftUI

/// The prominent button of a SwiftUI-owned screen. While `isInProgress` it shows a spinner in place of the title,
/// keeps its size and ignores taps.
struct ItmoProgressButton: View {
    let title: String
    var symbol: AppSymbol?
    var isInProgress = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            label
                .multilineTextAlignment(.center)
                .foregroundStyle(ItmoColor.onPrimary)
                .padding(.vertical, ItmoSpacing.compact)
                .opacity(isInProgress ? 0 : 1)
                .overlay {
                    if isInProgress {
                        ProgressView().tint(ItmoColor.onPrimary)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
        }
        .buttonStyle(.borderedProminent)
        // A capsule cuts a title that wraps at large text sizes; a rounded rectangle holds any number of lines.
        .buttonBorderShape(.roundedRectangle(radius: ItmoCorner.large))
        .itmoTint()
        .allowsHitTesting(!isInProgress)
        .accessibilityLabel(Text(verbatim: title))
    }

    @ViewBuilder private var label: some View {
        if let symbol {
            Label {
                Text(verbatim: title).font(.itmo(.labelLarge))
            } icon: {
                symbol.image.accessibilityHidden(true)
            }
        } else {
            Text(verbatim: title).font(.itmo(.labelLarge))
        }
    }
}

/// The kit's prominent action: the glass prominent button in the primary role. The system draws its label white,
/// which is unreadable on the dark scheme's light primary, so the label takes onPrimary, readable in both schemes.
struct ItmoProminentButton: View {
    let title: String
    var symbol: AppSymbol?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Group {
                if let symbol {
                    Label { Text(verbatim: title) } icon: { symbol.image }
                } else {
                    Text(verbatim: title)
                }
            }
            .foregroundStyle(ItmoColor.onPrimary)
        }
        .buttonStyle(.glassProminent)
        .itmoTint()
    }
}
