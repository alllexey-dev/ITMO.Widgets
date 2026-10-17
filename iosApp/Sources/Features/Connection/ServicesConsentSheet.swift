import Shared
import SwiftUI

/// The consent before connecting to ITMO.Widgets (DECISIONS.md: enabling always goes through it), the one sheet of
/// the connection page (`connection_title`) and the first-run step, as Android's `ServicesConsentSheet`: what the server checks, what it
/// will keep (`ServicesStoredData`), who sees what first, `services_consent_connect` and `services_consent_later`. `connect` is the only way the
/// opt-in turns on; closing the sheet leaves it off.
struct ServicesConsentSheet: View {
    let connect: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ItmoSheet(title: AppStrings.string("services_consent_title")) {
            Text(verbatim: AppStrings.string("services_consent_text"))
                .font(.itmo(.bodyLarge))
                .foregroundStyle(.secondary)
            ItmoSectionHeader(title: AppStrings.string("services_consent_stored"))
            StoredDataGroup()
            ItmoCard(tone: .primary) {
                Label {
                    Text(verbatim: AppStrings.string("services_consent_privacy"))
                } icon: {
                    AppSymbol.lock.image
                }
                .font(.itmo(.bodyMedium))
            }
            Button(action: connect) {
                Text(verbatim: AppStrings.string("services_consent_connect"))
                    .font(.itmo(.titleMedium, emphasized: true))
                    .foregroundStyle(ItmoColor.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .itmoTint()
            .padding(.top, ItmoSpacing.compact)
            .accessibilityIdentifier("services.consent.connect")
            Button { dismiss() } label: {
                Text(verbatim: AppStrings.string("services_consent_later"))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.glass)
            .controlSize(.large)
            .accessibilityIdentifier("services.consent.later")
        }
    }
}

/// What the server keeps, one row each (`ServicesStoredData`), with optional rows after them.
struct StoredDataGroup<Extra: View>: View {
    @ViewBuilder var extra: Extra

    var body: some View {
        ItmoListGroup {
            ForEach(Array(ServicesStoredData.shared.items.enumerated()), id: \.offset) { _, datum in
                ItmoRow(title: datum.text.resolved, symbol: datum.icon.symbol)
            }
            extra
        }
    }
}

extension StoredDataGroup where Extra == EmptyView {
    init() {
        self.init { EmptyView() }
    }
}
