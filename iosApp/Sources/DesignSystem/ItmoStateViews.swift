import SwiftUI

// The states of a screen with data (docs/design.md, SwiftUI screens): content, loading, empty, error, gated and stale.
// Empty and error are the system's unavailable view; a gate stands in place of the content it locks; stale content
// stays visible, dimmed, under the line that says when it is from.

/// A spinner with an optional line under it.
struct ItmoLoadingView: View {
    var title: String?

    var body: some View {
        VStack(spacing: ItmoSpacing.group) {
            ProgressView()
                .controlSize(.large)
                .itmoTint()
            if let title {
                Text(verbatim: title)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(ItmoSpacing.statePadding)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("kit.loading")
    }
}

/// Nothing to show: a symbol, a title, an optional description and an optional action.
struct ItmoEmptyView: View {
    let symbol: AppSymbol
    let title: String
    var description: String?
    var action: (title: String, run: () -> Void)?

    var body: some View {
        ItmoUnavailable(symbol: symbol, title: title, description: description, action: action)
            .accessibilityIdentifier("kit.empty")
    }
}

/// A failed load: the load error title or the error's own, an optional description and the retry button.
struct ItmoErrorView: View {
    let title: String
    var description: String?
    var retryTitle: String = AppStrings.string("common_retry")
    let retry: () -> Void

    var body: some View {
        ItmoUnavailable(symbol: .error, title: title, description: description, action: (retryTitle, retry))
            .accessibilityIdentifier("kit.error")
    }
}

private struct ItmoUnavailable: View {
    let symbol: AppSymbol
    let title: String
    let description: String?
    let action: (title: String, run: () -> Void)?

    var body: some View {
        ContentUnavailableView {
            Label {
                Text(verbatim: title)
            } icon: {
                symbol.image.foregroundStyle(ItmoColor.primary)
            }
        } description: {
            if let description { Text(verbatim: description) }
        } actions: {
            if let action {
                ItmoProminentButton(title: action.title, action: action.run)
                    .controlSize(.large)
                    .accessibilityIdentifier("kit.action")
            }
        }
    }
}

/// Something is unavailable to the viewer and they can fix it: what, why and one fix, in place of the missing
/// content. Never a silent hide.
struct ItmoGateCard: View {
    let title: String
    let reason: String
    let fix: (title: String, run: () -> Void)
    var symbol: AppSymbol = .lock
    var tone: ItmoTone = .warn

    var body: some View {
        ItmoNoticeCard(title: title, reason: reason, symbol: symbol, tone: tone, action: fix, prominent: true)
            .accessibilityIdentifier("kit.gate")
    }
}

/// Something worth doing, not a lock (add a course link): the gate's layout with a tonal button.
struct ItmoActionCard: View {
    let title: String
    let reason: String
    let action: (title: String, run: () -> Void)
    let symbol: AppSymbol
    var tone: ItmoTone = .primary

    var body: some View {
        ItmoNoticeCard(title: title, reason: reason, symbol: symbol, tone: tone, action: action, prominent: false)
    }
}

/// Locked with no fix for the viewer: what is closed and why, no button. Someone else's privacy is not a lock: their
/// hidden data is simply absent.
struct ItmoLockNote: View {
    let title: String
    let reason: String
    var symbol: AppSymbol = .lock

    var body: some View {
        ItmoNoticeCard(title: title, reason: reason, symbol: symbol, tone: .neutral, action: nil, prominent: false)
    }
}

private struct ItmoNoticeCard: View {
    let title: String
    let reason: String
    let symbol: AppSymbol
    let tone: ItmoTone
    let action: (title: String, run: () -> Void)?
    let prominent: Bool

    var body: some View {
        ItmoCard {
            HStack(alignment: .top, spacing: 14) {
                ItmoIconTile(symbol: symbol, tone: tone)
                VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                    Text(verbatim: title)
                        .font(.itmo(.bodyLarge, emphasized: true))
                    Text(verbatim: reason)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                    if let action {
                        button(action)
                            .controlSize(.small)
                            .itmoTint()
                            .padding(.top, 6)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    @ViewBuilder
    private func button(_ action: (title: String, run: () -> Void)) -> some View {
        if prominent {
            ItmoProminentButton(title: action.title, action: action.run)
        } else {
            Button(action: action.run) { Text(verbatim: action.title) }.buttonStyle(.bordered)
        }
    }
}

/// Old data kept visible while it cannot refresh: dimmed, under the line that says when it is from ("data as of
/// 11:44").
struct ItmoStale<Content: View>: View {
    let since: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            Label {
                Text(verbatim: since)
            } icon: {
                AppSymbol.history.image
            }
            .font(.itmo(.bodySmall))
            .foregroundStyle(.secondary)
            .padding(.horizontal, ItmoSpacing.related)
            content.opacity(0.5)
        }
    }
}

extension View {
    /// A shown value that a slower source is about to replace (My ITMO's points while BARS answers) pulses like a
    /// skeleton in place while `active`, and VoiceOver hears `label` as its value; still under Reduce Motion. The
    /// geometry never changes, so the answer moves nothing.
    func itmoLoadingPulse(_ active: Bool, label: String) -> some View {
        modifier(ItmoLoadingPulse(active: active, label: label))
    }
}

private struct ItmoLoadingPulse: ViewModifier {
    let active: Bool
    let label: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if !active {
            content
        } else if reduceMotion {
            content.accessibilityValue(Text(verbatim: label))
        } else {
            content
                .phaseAnimator([1, ItmoMotion.pulseMinAlpha]) { view, opacity in view.opacity(opacity) } animation: { _ in
                    .easeInOut(duration: ItmoMotion.pulse)
                }
                .accessibilityValue(Text(verbatim: label))
        }
    }
}
