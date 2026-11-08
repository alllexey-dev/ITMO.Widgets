import Shared
import SwiftUI

/// `tuition_title` (`AppRoutes.Tuition`, docs/features/finances.md) over the shared `TuitionViewModel`: each tuition
/// agreement with its balance and payments, earlier years under `contract_past_years`. `contract_pay` opens my.itmo's
/// tuition page in the system browser (`TuitionWeb.PAY_URL`), since the in-app view does not follow the redirect to
/// the bank.
struct TuitionScreen: View {
    @State private var model = ObservableViewModel(TuitionViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?

    var body: some View {
        TuitionContentView(state: model.state, actions: actions)
            .refreshable {
                await model.pullRefresh({ $0.refresh(mode: .pull) }, while: { $0.uiState.value.refreshing })
            }
            .itmoDetailHeader(title: AppStrings.string("tuition_title"), subtitle: nil)
            .observing(model)
            .overlay(alignment: .bottom) { banner }
            .animation(.default, value: message?.id)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("tuition.screen")
    }

    private var actions: TuitionActions {
        TuitionActions(
            retry: { model.viewModel.refresh(mode: .force) },
            pay: {
                let platform = IosKoin.shared.get(protocol: PlatformActions.self) as? PlatformActions
                if platform?.openLink(url: TuitionWeb.shared.PAY_URL) != true {
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
struct TuitionActions {
    var retry: () -> Void = {}
    var pay: () -> Void = {}
    var togglePastYears: () -> Void = {}
}

/// The screen for one `TuitionUiState`, without the ViewModel.
struct TuitionContentView: View {
    let state: TuitionUiState
    var actions = TuitionActions()

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
                .accessibilityIdentifier("tuition.error")
            case .noContracts:
                ItmoEmptyView(
                    symbol: .payments,
                    title: AppStrings.string("tuition_none_title"),
                    description: AppStrings.string("tuition_none_text")
                )
            case let .shown(shown):
                if let stale = state.stale {
                    ItmoStale(since: stale.resolved) { contracts(shown.contracts) }
                } else {
                    contracts(shown.contracts)
                }
            }
        }
    }

    private func contracts(_ list: ContractList) -> some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            ItmoContractList(list: list, idPrefix: "tuition", pay: actions.pay, togglePastYears: actions.togglePastYears)
        }
    }
}
