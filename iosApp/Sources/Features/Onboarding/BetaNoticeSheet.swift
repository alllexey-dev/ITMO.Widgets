import Shared
import SwiftUI

/// The iPhone beta notice (`ios_beta_notice_title`): what the sideloaded iOS build lacks next to Android. The shell
/// shows it once on the tabs of the first real session (`BetaNotice.shownKey`); the about page opens it again.
struct BetaNoticeSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        ItmoSheet(title: AppStrings.string("ios_beta_notice_title")) {
            Text(verbatim: AppStrings.string("ios_beta_notice_text"))
                .font(.itmo(.bodyLarge))
                .foregroundStyle(.secondary)
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("ios_beta_notifications_title"),
                    subtitle: AppStrings.string("ios_beta_notifications_text"),
                    symbol: .notificationsOff,
                    tone: .neutral
                )
                external("ios_beta_agent_title", "ios_beta_agent_text", .exercise, ProjectLinks.shared.AGENT_URL)
                ItmoRow(
                    title: AppStrings.string("ios_beta_widgets_title"),
                    subtitle: AppStrings.string("ios_beta_widgets_text"),
                    symbol: .widgets,
                    tone: .neutral
                )
                external(
                    "ios_beta_feedback_title", "ios_beta_feedback_text", .brandTelegram, ProjectLinks.shared.TELEGRAM_URL
                )
            }
            Button { dismiss() } label: {
                Text(verbatim: AppStrings.string("ios_beta_notice_done"))
                    .font(.itmo(.titleMedium, emphasized: true))
                    .foregroundStyle(ItmoColor.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .itmoTint()
            .padding(.top, ItmoSpacing.compact)
            .accessibilityIdentifier("beta.notice.done")
        }
        .accessibilityIdentifier("beta.notice")
    }

    /// A row that leaves the app: an arrow out instead of the chevron, as on the profile.
    private func external(_ title: String, _ subtitle: String, _ symbol: AppSymbol, _ url: String) -> some View {
        ItmoRow(
            title: AppStrings.string(title),
            subtitle: AppStrings.string(subtitle),
            symbol: symbol,
            tone: .neutral,
            action: { URL(string: url).map { openURL($0) } }
        )
        .accessory {
            AppSymbol.openInNew.image
                .font(.itmo(.bodySmall, emphasized: true))
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
        }
    }
}

enum BetaNotice {
    /// The `UserDefaults` flag set once the notice was closed; it outlives sign-out, so the notice shows once per install.
    static let shownKey = "iosBetaNoticeShown"
}
