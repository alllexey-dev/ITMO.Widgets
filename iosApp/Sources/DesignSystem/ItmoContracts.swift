import Shared
import SwiftUI

/// The shared agreements (`ContractList`, core `ContractCards.kt`) as the dormitory and tuition screens draw them,
/// like the kit's `ContractSections` on Android: the current agreements, each with its balance, `contract_pay` while
/// something is left to pay and its payments, then `contract_past_years`, unfolded on a tap. Laid out in the caller's
/// stack; `idPrefix` names the accessibility identifiers (`dormitory.pay`, `tuition.past_years`).
struct ItmoContractList: View {
    let list: ContractList
    let idPrefix: String
    var pay: () -> Void = {}
    var togglePastYears: () -> Void = {}

    var body: some View {
        ForEach(list.current, id: \.id) { ItmoContractView(contract: $0, payId: "\(idPrefix).pay", pay: pay) }
        if let past = list.pastYears {
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("contract_past_years"),
                    symbol: .history,
                    tone: .neutral,
                    action: togglePastYears
                )
                .accessory {
                    AppSymbol.expandMore.image
                        .font(.itmo(.bodySmall, emphasized: true))
                        .foregroundStyle(.tertiary)
                        .rotationEffect(.degrees(past.expanded ? 180 : 0))
                        .accessibilityHidden(true)
                }
                .accessibilityIdentifier("\(idPrefix).past_years")
            }
            if past.expanded {
                ForEach(Array(past.years.enumerated()), id: \.offset) { _, year in
                    Text(verbatim: year.title.resolved)
                        .font(.itmo(.labelLarge, emphasized: true))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, ItmoKit.padding)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(year.contracts, id: \.id) { ItmoContractView(contract: $0, payId: "\(idPrefix).pay", pay: pay) }
                }
            }
        }
    }
}

/// One agreement: its kind, number and dates, the balance with `contract_pay`, then its payments.
private struct ItmoContractView: View {
    let contract: ContractCard
    let payId: String
    let pay: () -> Void

    var body: some View {
        ItmoSectionHeader(title: contract.title.resolved)
        ItmoCard(tone: contract.balance.tone == .debt ? .bad : nil) {
            Text(verbatim: contract.subtitle.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline) {
                Text(verbatim: contract.balance.label.resolved).font(.itmo(.titleMedium, emphasized: true))
                Spacer(minLength: ItmoSpacing.content)
                Text(verbatim: contract.balance.amount.resolved).font(.itmo(.titleLarge, emphasized: true))
            }
            .padding(.top, ItmoSpacing.related)
            if contract.pay {
                Button(action: pay) {
                    Label {
                        Text(verbatim: AppStrings.string("contract_pay"))
                    } icon: {
                        AppSymbol.openInNew.image
                    }
                    .foregroundStyle(ItmoColor.onPrimary)
                }
                .buttonStyle(.glassProminent)
                .itmoTint()
                .padding(.top, ItmoSpacing.related)
                .accessibilityIdentifier(payId)
            }
        }
        if !contract.payments.isEmpty {
            ItmoListGroup {
                ForEach(Array(contract.payments.enumerated()), id: \.offset) { _, payment in
                    ItmoRow(
                        title: payment.period,
                        subtitle: [payment.due, payment.paid].compactMap { $0?.resolved }.joined(separator: " \u{00B7} "),
                        trailing: payment.amount.resolved,
                        pill: payment.pill.map { ItmoPill(text: $0.text.resolved, tone: $0.tone.itmo) }
                    )
                }
            }
        }
    }
}

private extension PaymentTone {
    var itmo: ItmoTone {
        switch self {
        case .neutral: .neutral
        case .done: .ok
        case .attention: .warn
        case .debt: .bad
        }
    }
}
