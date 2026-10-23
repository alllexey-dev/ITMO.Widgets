import Shared
import SwiftUI

/// The devices screen (`devices_title`, UX spec section 2) over the shared `DevicesViewModel`, pushed from the
/// profile: connect, this phone, the user's other installs, the connected clients, the other ITMO.ID sessions and the
/// label switch (`devices_browser_like`). Installs and sessions open a facts sheet whose button asks first
/// (`confirmationDialog`); a client opens its screen (`AppRoutes.Connection`); connecting opens the pairing sheet
/// (`AppRoutes.Pairing`).
struct DevicesScreen: View {
    @Environment(AppRouter.self) private var router
    @State private var model = ObservableViewModel(DevicesViewModel.self, state: \.uiState)

    var body: some View {
        DevicesContent(
            state: model.state,
            actions: DevicesActions(
                connect: { router.open(AppRoutes.Pairing(link: nil)) },
                enableServer: { router.open(AppRoutes.Settings(page: SettingsPage.services.name)) },
                openClient: { router.open(AppRoutes.Connection(id: $0)) },
                open: { model.viewModel.open(key: $0) },
                retry: { model.viewModel.refresh() },
                endAllOthers: { model.viewModel.askEndAllOthers() },
                browserLike: { model.viewModel.setBrowserLike(browserLike: $0) }
            )
        )
        .refreshable {
            await model.pullRefresh({ $0.refresh() }, while: { $0.uiState.value.refreshing })
        }
        .itmoDetailHeader(title: AppStrings.string("devices_title"), subtitle: nil)
        .sheet(item: sheet) { item in
            DevicesFactsSheet(sheet: item.sheet, busy: model.state.busy, action: { model.viewModel.askSheetAction() })
                // Over the sheet the dialog has to come from the sheet.
                .connectionConfirm(model.state.confirm, confirm: confirm, dismiss: dismissConfirm)
        }
        .connectionConfirm(
            model.state.sheet == nil ? model.state.confirm : nil,
            confirm: confirm,
            dismiss: dismissConfirm
        )
        .connectionMessage(model.state.message?.resolved) { model.viewModel.onMessageShown() }
        .observing(model)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("devices.screen")
    }

    private var sheet: Binding<KeyedFactsSheet?> {
        Binding(
            get: { model.state.sheet.map(KeyedFactsSheet.init) },
            set: { if $0 == nil { model.viewModel.closeSheet() } }
        )
    }

    private func confirm() {
        model.viewModel.confirm()
    }

    private func dismissConfirm() {
        model.viewModel.dismissConfirm()
    }
}

/// A facts sheet by its row key, so the sheet keeps its content while it closes.
private struct KeyedFactsSheet: Identifiable {
    let sheet: FactsSheet
    var id: String { sheet.key }
}

/// What the screen's rows do; snapshot tests pass no-ops.
struct DevicesActions {
    var connect: () -> Void = {}
    var enableServer: () -> Void = {}
    var openClient: (String) -> Void = { _ in }
    var open: (String) -> Void = { _ in }
    var retry: () -> Void = {}
    var endAllOthers: () -> Void = {}
    var browserLike: (Bool) -> Void = { _ in }
}

/// the devices screen (`devices_title`) for one `DevicesUiState`, without the ViewModel.
struct DevicesContent: View {
    let state: DevicesUiState
    var actions = DevicesActions()

    var body: some View {
        ItmoScreenStack {
            connect.padding(.top, ItmoSpacing.compact)
            if let phone = state.thisPhone {
                ItmoSectionHeader(title: AppStrings.string("devices_this_phone"))
                ItmoListGroup { row(phone) }
            }
            if let error = state.error {
                ItmoErrorView(title: error.resolved, retry: actions.retry)
                    .accessibilityIdentifier("devices.error")
            } else if state.loading {
                ItmoLoadingView()
            } else {
                if !state.installs.isEmpty {
                    ItmoSectionHeader(title: AppStrings.string("devices_my_devices"))
                    ItmoListGroup { ForEach(state.installs, id: \.key) { row($0) } }
                }
                if !state.clients.isEmpty {
                    ItmoSectionHeader(title: AppStrings.string("devices_connected"), count: "\(state.clients.count)")
                    ItmoListGroup { ForEach(state.clients, id: \.key) { row($0) } }
                        .accessibilityIdentifier("devices.clients")
                }
            }
            others
            ItmoListGroup {
                ItmoSwitchRow(
                    title: AppStrings.string("devices_browser_like"),
                    subtitle: AppStrings.string("devices_browser_like_subtitle"),
                    symbol: .visibilityOff,
                    tone: .neutral,
                    isOn: Binding(get: { state.browserLike }, set: actions.browserLike)
                )
                .accessibilityIdentifier("devices.browserLike")
            }
            .padding(.top, ItmoSpacing.compact)
        }
    }

    /// Connecting needs the server; without it the gate says so and opens its page.
    @ViewBuilder private var connect: some View {
        if state.backend {
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("devices_connect"),
                    subtitle: AppStrings.string("devices_connect_subtitle"),
                    symbol: .qrCodeScanner,
                    tone: .primary,
                    chevron: true,
                    action: actions.connect
                )
                .accessibilityIdentifier("devices.connect")
            }
        } else {
            ItmoGateCard(
                title: AppStrings.string("devices_gate_title"),
                reason: AppStrings.string("devices_gate_reason"),
                fix: (AppStrings.string("devices_gate_fix"), actions.enableServer),
                symbol: .cloudOff
            )
        }
    }

    /// `devices_others`: a spinner, the no-cookie note, the error with retry, or the rows and ending them
    /// all; nothing when there are none.
    @ViewBuilder private var others: some View {
        switch onEnum(of: state.others) {
        case .loading:
            ItmoSectionHeader(title: AppStrings.string("devices_others"))
            ItmoLoadingView()
        case .noCookie:
            ItmoSectionHeader(title: AppStrings.string("devices_others"))
            ItmoGroupNote(text: AppStrings.string("devices_others_no_cookie"))
        case let .error(error):
            ItmoSectionHeader(title: AppStrings.string("devices_others"))
            ItmoErrorView(title: error.text.resolved, retry: actions.retry)
        case let .content(content) where !content.rows.isEmpty:
            ItmoSectionHeader(title: AppStrings.string("devices_others"), count: "\(content.rows.count)")
            ItmoListGroup {
                ForEach(content.rows, id: \.key) { row($0) }
                ItmoRow(
                    title: AppStrings.string("devices_end_all"),
                    symbol: .logout,
                    tone: .neutral,
                    danger: true,
                    action: actions.endAllOthers
                )
                .accessibilityIdentifier("devices.endAll")
            }
        case .content:
            EmptyView()
        }
    }

    /// An install or a session opens its facts, a client its screen.
    private func row(_ row: DeviceRow) -> some View {
        ItmoRow(
            title: row.title.resolved,
            subtitle: row.subtitle?.resolved,
            symbol: row.icon.symbol,
            tone: row.icon.tone,
            chevron: row.connectionId != nil,
            pill: row.pill?.kit,
            action: {
                if let client = row.connectionId { actions.openClient(client) } else { actions.open(row.key) }
            }
        )
        .accessibilityIdentifier("devices.row.\(row.key)")
    }
}

/// The facts of an install or a session (UX spec section 3): its title and first line, the value rows, then the
/// outlined destructive action, or the note that stands in its place on this phone.
struct DevicesFactsSheet: View {
    let sheet: FactsSheet
    var busy = false
    var action: () -> Void = {}

    var body: some View {
        ItmoSheet(title: sheet.title.resolved) {
            if let subtitle = sheet.subtitle?.resolved {
                Text(verbatim: subtitle)
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
            }
            ConnectionFacts(facts: sheet.facts)
            if let note = sheet.note?.resolved {
                ItmoGroupNote(text: note)
            }
            if let title = sheet.action?.resolved {
                Button(role: .destructive, action: action) {
                    Label { Text(verbatim: title) } icon: { AppSymbol.logout.image }
                        .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
                }
                .buttonStyle(.glass)
                .disabled(busy)
                .padding(.top, ItmoSpacing.compact)
                .accessibilityIdentifier("devices.sheet.action")
            }
        }
        .accessibilityIdentifier("devices.sheet")
    }
}
