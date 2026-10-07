import Shared
import SwiftUI

/// Android's `AppUpdateFragment` over the shared `AppUpdateViewModel` (IO-08b): what the check found,
/// `app_update_action` (the App Store page), `app_update_later` and, for a build Backend still serves,
/// `app_update_skip`. It renders the offer it is opened with and never repeats the request.
struct AppUpdateScreen: View {
    @State private var model: ObservableViewModel<AppUpdateViewModel, AppUpdateUiState>
    @State private var openFailed = false
    @Environment(\.openURL) private var openURL
    let storeURL: URL
    let close: () -> Void

    init(update: AppUpdate, storeURL: URL, close: @escaping () -> Void) {
        self.storeURL = storeURL
        self.close = close
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = AppUpdateIosParameters.shared.viewModel(update: update)
            guard let viewModel = store.resolve(type: AppUpdateViewModel.self, parameters: parameters)
                as? AppUpdateViewModel else {
                preconditionFailure("Koin resolved no AppUpdateViewModel")
            }
            return viewModel
        })
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                AppUpdateContent(
                    state: model.state,
                    update: {
                        openURL(storeURL) { accepted in
                            if !accepted { openFailed = true }
                        }
                    },
                    later: close,
                    skip: model.viewModel.skipVersion
                )
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: close) {
                        AppSymbol.close.image
                    }
                    .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                    .accessibilityIdentifier("appUpdate.close")
                }
            }
        }
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case .skipped: close()
            }
        }
        .alert(Text(verbatim: AppStrings.string("app_update_open_failed")), isPresented: $openFailed) {
            Button(role: .cancel) {} label: {
                Text(verbatim: AppStrings.string("common_close"))
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("appUpdate.screen")
    }
}

/// The offer for one `AppUpdateUiState`, without the ViewModel (snapshot tests render it directly). Release notes
/// follow the reason instead of replacing it: they explain the release, not why the screen is open.
struct AppUpdateContent: View {
    let state: AppUpdateUiState
    var update: () -> Void = {}
    var later: () -> Void = {}
    var skip: () -> Void = {}

    @ScaledMetric(relativeTo: .largeTitle) private var symbolSize = ItmoSpacing.stateIcon

    var body: some View {
        VStack(spacing: ItmoSpacing.group) {
            AppSymbol.download.image
                .resizable()
                .scaledToFit()
                .frame(width: symbolSize, height: symbolSize)
                .foregroundStyle(ItmoColor.primary)
                .accessibilityHidden(true)
            VStack(spacing: ItmoSpacing.compact) {
                Text(verbatim: title)
                    .font(.itmo(.titleLarge))
                    .accessibilityAddTraits(.isHeader)
                    .accessibilityIdentifier("appUpdate.title")
                Text(verbatim: AppStrings.string("app_update_versions", [state.installed, state.latest]))
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(ItmoColor.primary)
                    .accessibilityIdentifier("appUpdate.versions")
                Text(verbatim: description)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
            }
            .multilineTextAlignment(.center)
            VStack(spacing: ItmoSpacing.compact) {
                ItmoProgressButton(title: AppStrings.string("app_update_action"), symbol: .download, action: update)
                    .accessibilityIdentifier("appUpdate.download")
                Button(action: later) {
                    Text(verbatim: AppStrings.string("app_update_later"))
                        .font(.itmo(.labelLarge))
                        .multilineTextAlignment(.center)
                        .padding(.vertical, ItmoSpacing.compact)
                        .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.roundedRectangle(radius: ItmoCorner.large))
                .itmoTint()
                .accessibilityIdentifier("appUpdate.later")
                // An unsupported build has nowhere to skip to: only the reminder is left.
                if !state.unsupported {
                    Button(action: skip) {
                        Text(verbatim: AppStrings.string("app_update_skip"))
                            .font(.itmo(.labelLarge))
                            .frame(minHeight: ItmoMetrics.touchTarget)
                    }
                    .itmoTint()
                    .accessibilityIdentifier("appUpdate.skip")
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, ItmoSpacing.section)
        .padding(.vertical, ItmoSpacing.statePadding)
    }

    private var title: String {
        AppStrings.string(state.unsupported ? "app_update_unsupported_title" : "app_update_title")
    }

    private var description: String {
        let reason = AppStrings.string(
            state.unsupported ? "app_update_unsupported_description" : "app_update_description"
        )
        return state.note.isEmpty ? reason : "\(reason)\n\n\(state.note)"
    }
}
