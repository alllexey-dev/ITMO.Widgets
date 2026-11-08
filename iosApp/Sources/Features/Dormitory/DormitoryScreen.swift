import Shared
import SwiftUI

/// `dormitory_title` (`AppRoutes.Dormitory`, docs/features/dormitory.md) over the shared `DormitoryViewModel`: the
/// stage of the dormitory application, the settlement documents while they matter, the assigned dormitory with
/// `dormitory_on_map` in Apple Maps and the settlement instructions as the shared view model's plain blocks, and the
/// rental agreements with their payments. Picking a date and uploading documents open my.itmo's dormitory section in
/// the app (`DormitoryWeb.route`); `contract_pay` opens the same section in the system browser (`DormitoryWeb.PAY_URL`),
/// since the in-app view does not follow the redirect to the bank.
struct DormitoryScreen: View {
    @Environment(AppRouter.self) private var router
    @State private var model = ObservableViewModel(DormitoryViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?

    var body: some View {
        DormitoryContentView(state: model.state, actions: actions)
            .refreshable {
                await model.pullRefresh({ $0.refresh(mode: .pull) }, while: { $0.uiState.value.refreshing })
            }
            .itmoDetailHeader(title: AppStrings.string("dormitory_title"), subtitle: nil)
            .observing(model)
            .overlay(alignment: .bottom) { banner }
            .animation(.default, value: message?.id)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("dormitory.screen")
    }

    private var actions: DormitoryActions {
        DormitoryActions(
            retry: { model.viewModel.refresh(mode: .force) },
            openMyItmo: { router.open(DormitoryWeb.shared.route) },
            openMap: { destination in
                let platform = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions
                if platform?.openMap(destination: destination) != true {
                    message = SettingsMessage(text: AppStrings.string("dormitory_map_unavailable"))
                }
            },
            pay: {
                let platform = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions
                if platform?.openLink(url: DormitoryWeb.shared.PAY_URL) != true {
                    message = SettingsMessage(text: AppStrings.string("contract_pay_unavailable"))
                }
            },
            togglePastYears: { model.viewModel.togglePastYears() }
        )
    }

    @ViewBuilder private var banner: some View {
        if let message {
            SettingsMessageBanner(text: message.text)
                .task(id: message.id) {
                    try? await Task.sleep(for: SettingsMessageBanner.lifetime)
                    if self.message?.id == message.id { self.message = nil }
                }
        }
    }
}

/// What the screen's controls do; snapshot tests pass no-ops.
struct DormitoryActions {
    var retry: () -> Void = {}
    var openMyItmo: () -> Void = {}
    var openMap: (MapDestination) -> Void = { _ in }
    var pay: () -> Void = {}
    var togglePastYears: () -> Void = {}
}

/// The screen for one `DormitoryUiState`, without the ViewModel.
struct DormitoryContentView: View {
    let state: DormitoryUiState
    var actions = DormitoryActions()

    var body: some View {
        ItmoScreenStack {
            switch onEnum(of: state.content) {
            case .loading:
                ItmoLoadingView()
            case let .failed(failed):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: failed.text.resolved,
                    retry: actions.retry
                )
                .accessibilityIdentifier("dormitory.error")
            case .noApplication:
                ItmoEmptyView(
                    symbol: .apartment,
                    title: AppStrings.string("dormitory_none_title"),
                    description: AppStrings.string("dormitory_none_text")
                )
            case let .shown(shown):
                if let stale = state.stale {
                    ItmoStale(since: stale.resolved) { DormitoryShownView(shown: shown, actions: actions) }
                } else {
                    DormitoryShownView(shown: shown, actions: actions)
                }
            }
        }
    }
}

/// The application: the stage as the hero, the documents, the dormitory and how to settle in.
private struct DormitoryShownView: View {
    let shown: DormitoryContentShown
    let actions: DormitoryActions

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            if let stage = shown.stage { stageCard(stage) }
            if let documents = shown.documents {
                DormitoryDocumentsView(documents: documents, upload: actions.openMyItmo)
            }
            if let contracts = shown.contracts { DormitoryContractsView(contracts: contracts, actions: actions) }
            if let place = shown.dormitory { DormitoryPlaceView(place: place, openMap: actions.openMap) }
        }
    }

    private func stageCard(_ card: DormitoryStageCard) -> some View {
        ItmoCard(tone: card.tone.itmo, hero: true) {
            Text(verbatim: card.title.resolved)
                .font(.itmo(.titleLarge, emphasized: true))
                .accessibilityAddTraits(.isHeader)
            if let place = card.queuePlace {
                Text(verbatim: place.resolved).font(.itmo(.titleMedium, emphasized: true))
            }
            Text(verbatim: card.text.resolved).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
            if card.chooseDate {
                Button(action: actions.openMyItmo) {
                    Label {
                        Text(verbatim: AppStrings.string("dormitory_choose_date"))
                    } icon: {
                        AppSymbol.eventAvailable.image
                    }
                    .foregroundStyle(ItmoColor.onPrimary)
                }
                .buttonStyle(.glassProminent)
                .itmoTint()
                .padding(.top, ItmoSpacing.related)
                .accessibilityIdentifier("dormitory.choose_date")
            }
        }
    }
}

/// Each document with ITMO's status, then `dormitory_upload` while one is not confirmed.
private struct DormitoryDocumentsView: View {
    let documents: DormitoryDocuments
    let upload: () -> Void

    var body: some View {
        ItmoSectionHeader(title: AppStrings.string("dormitory_documents_section"))
        if documents.rows.isEmpty {
            ItmoGroupNote(text: AppStrings.string("dormitory_documents_none"))
        } else {
            ItmoListGroup {
                ForEach(Array(documents.rows.enumerated()), id: \.offset) { _, row in
                    ItmoRow(
                        title: row.name,
                        pill: ItmoPill(
                            text: row.status,
                            tone: row.confirmed ? .ok : .warn,
                            symbol: row.confirmed ? .checkCircle : .error
                        )
                    )
                }
                if documents.upload {
                    ItmoRow(
                        title: AppStrings.string("dormitory_upload"),
                        subtitle: AppStrings.string("dormitory_upload_subtitle"),
                        symbol: .upload,
                        tone: .primary,
                        chevron: true,
                        action: upload
                    )
                    .accessibilityIdentifier("dormitory.upload")
                }
            }
        }
    }
}

/// The dormitory, `dormitory_on_map` in Apple Maps, and the settlement instructions.
private struct DormitoryPlaceView: View {
    let place: DormitoryPlace
    let openMap: (MapDestination) -> Void

    var body: some View {
        ItmoSectionHeader(title: AppStrings.string("dormitory_place_section"))
        ItmoListGroup {
            ItmoRow(title: place.name, subtitle: place.address, symbol: .apartment, tone: .primary)
            ItmoRow(
                title: AppStrings.string("dormitory_on_map"),
                symbol: .map,
                tone: .neutral,
                action: { openMap(place.map) }
            )
                .accessory {
                    AppSymbol.openInNew.image
                        .font(.itmo(.bodySmall, emphasized: true))
                        .foregroundStyle(.tertiary)
                        .accessibilityHidden(true)
                }
                .accessibilityIdentifier("dormitory.map")
        }
        if !place.instructions.blocks.isEmpty {
            ItmoSectionHeader(title: AppStrings.string("dormitory_instructions_section"))
            ItmoCard {
                ItmoRichText(text: place.instructions)
            }
        }
    }
}

/// The current agreements, then `contract_past_years`, unfolded on a tap.
private struct DormitoryContractsView: View {
    let contracts: DormitoryContracts
    let actions: DormitoryActions

    var body: some View {
        ForEach(contracts.current, id: \.id) { ContractView(contract: $0, pay: actions.pay) }
        if let past = contracts.pastYears {
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("contract_past_years"),
                    symbol: .history,
                    tone: .neutral,
                    action: actions.togglePastYears
                )
                .accessory {
                    AppSymbol.expandMore.image
                        .font(.itmo(.bodySmall, emphasized: true))
                        .foregroundStyle(.tertiary)
                        .rotationEffect(.degrees(past.expanded ? 180 : 0))
                        .accessibilityHidden(true)
                }
                .accessibilityIdentifier("dormitory.past_years")
            }
            if past.expanded {
                ForEach(Array(past.years.enumerated()), id: \.offset) { _, year in
                    Text(verbatim: year.title.resolved)
                        .font(.itmo(.labelLarge, emphasized: true))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, ItmoKit.padding)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(year.contracts, id: \.id) { ContractView(contract: $0, pay: actions.pay) }
                }
            }
        }
    }
}

/// One agreement: its kind, number and dates, the balance with `contract_pay`, then its payments.
private struct ContractView: View {
    let contract: ContractCard
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
                .accessibilityIdentifier("dormitory.pay")
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

private extension DormitoryTone {
    var itmo: ItmoTone? {
        switch self {
        case .neutral: nil
        case .attention: .warn
        case .done: .ok
        }
    }
}
