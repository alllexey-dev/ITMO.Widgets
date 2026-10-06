import SwiftUI

// Loading, empty and error states of SwiftUI-owned screens. Each fills the width, centres its content and keeps the
// same padding, so swapping one state for another does not move the screen around.

/// A spinner with an optional line under it.
struct ItmoLoadingView: View {
    var title: String?

    var body: some View {
        ItmoStateLayout {
            ProgressView()
                .controlSize(.large)
                .itmoTint()
        } texts: {
            if let title {
                Text(verbatim: title)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
            }
        } action: {
            EmptyView()
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("kit.loading")
    }
}

/// Nothing to show: a symbol, a title and an optional description.
struct ItmoEmptyView: View {
    let symbol: AppSymbol
    let title: String
    var description: String?

    var body: some View {
        ItmoStateLayout {
            ItmoStateSymbol(symbol: symbol, color: ItmoColor.primary)
        } texts: {
            ItmoStateTexts(title: title, description: description)
        } action: {
            EmptyView()
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("kit.empty")
    }
}

/// A failed load: the error symbol, a title, an optional description and a retry button.
struct ItmoErrorView: View {
    let title: String
    var description: String?
    var retryTitle: String = AppStrings.string("common_retry")
    let retry: () -> Void

    var body: some View {
        ItmoStateLayout {
            ItmoStateSymbol(symbol: .error, color: ItmoColor.error)
        } texts: {
            ItmoStateTexts(title: title, description: description)
        } action: {
            Button(action: retry) {
                Label {
                    Text(verbatim: retryTitle).font(.itmo(.labelLarge))
                } icon: {
                    AppSymbol.refresh.image.accessibilityHidden(true)
                }
                .frame(minHeight: ItmoMetrics.touchTarget)
            }
            .buttonStyle(.bordered)
            .itmoTint()
            .accessibilityIdentifier("kit.error.retry")
        }
        .accessibilityIdentifier("kit.error")
    }
}

private struct ItmoStateLayout<Symbol: View, Texts: View, Action: View>: View {
    @ViewBuilder var symbol: Symbol
    @ViewBuilder var texts: Texts
    @ViewBuilder var action: Action

    var body: some View {
        VStack(spacing: ItmoSpacing.group) {
            symbol
            VStack(spacing: ItmoSpacing.compact) {
                texts
            }
            .multilineTextAlignment(.center)
            action
        }
        .frame(maxWidth: .infinity)
        .padding(ItmoSpacing.statePadding)
    }
}

private struct ItmoStateSymbol: View {
    let symbol: AppSymbol
    let color: Color

    /// The symbol scales with the text size from the token's size at the default size.
    @ScaledMetric(relativeTo: .largeTitle) private var size = ItmoSpacing.stateIcon

    var body: some View {
        symbol.image
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .foregroundStyle(color)
            .accessibilityHidden(true)
    }
}

private struct ItmoStateTexts: View {
    let title: String
    let description: String?

    var body: some View {
        Text(verbatim: title)
            .font(.itmo(.titleLarge))
            .foregroundStyle(.primary)
        if let description {
            Text(verbatim: description)
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
        }
    }
}
