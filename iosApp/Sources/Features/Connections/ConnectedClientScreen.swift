import Shared
import SwiftUI
import UIKit

/// A connected client (UX spec section 4) over the shared `ConnectedClientViewModel`, pushed from the devices screen and
/// from its reminder (`AppRoutes.Connection`): its ITMO sign-in with renewal, access as switches (widening asks for the
/// screen lock), the facts and the danger row, which asks first and leaves the screen once revoked. Renewal shows the
/// command for the client, then scans the client's QR and continues in the pairing sheet (`AppRoutes.Pairing`).
struct ConnectedClientScreen: View {
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @State private var model: ObservableViewModel<ConnectedClientViewModel, ConnectedClientUiState>
    /// The renewal's scan waits for its sheet to close; the scanned link opens once the scanner has closed.
    @State private var scanAfterRenew = false
    @State private var scanning = false
    @State private var scanned: String?

    init(id: String) {
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters = ConnectionsIosParameters.shared.connection(id: id)
            guard let model = store.resolve(type: ConnectedClientViewModel.self, parameters: parameters)
                as? ConnectedClientViewModel else {
                preconditionFailure("Koin resolved no ConnectedClientViewModel")
            }
            return model
        })
    }

    var body: some View {
        ConnectedClientContent(
            state: model.state,
            actions: ConnectedClientActions(
                scope: { id, checked in
                    model.viewModel.onScopeChanged(scopeId: id, checked: checked, unlock: appDeviceUnlock())
                },
                renew: { model.viewModel.askRenew() },
                revoke: { model.viewModel.askRevoke() }
            )
        )
        .itmoDetailHeader(title: model.state.title.resolved, subtitle: model.state.subtitle?.resolved)
        .sheet(item: renew, onDismiss: startScanAfterRenew) { item in
            ClientRenewSheet(renew: item.renew, name: model.state.title.resolved) {
                scanAfterRenew = true
                model.viewModel.closeRenew()
            }
        }
        .fullScreenCover(isPresented: $scanning, onDismiss: openScanned) {
            PairingScannerScreen(
                scanned: { link in
                    scanned = link
                    scanning = false
                },
                unavailable: {
                    // Pasting the link still works in the pairing sheet.
                    scanned = ""
                    scanning = false
                },
                close: { scanning = false }
            )
        }
        .connectionConfirm(
            model.state.confirm,
            confirm: { model.viewModel.revoke() },
            dismiss: { model.viewModel.dismissConfirm() }
        )
        .connectionMessage(model.state.message?.resolved) { model.viewModel.onMessageShown() }
        .onChange(of: model.state.closed) { _, closed in
            if closed { dismiss() }
        }
        .observing(model)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("client.screen")
    }

    private var renew: Binding<KeyedRenewSheet?> {
        Binding(
            get: { model.state.renew.map(KeyedRenewSheet.init) },
            set: { if $0 == nil { model.viewModel.closeRenew() } }
        )
    }

    private func startScanAfterRenew() {
        guard scanAfterRenew else { return }
        scanAfterRenew = false
        PairingScanner.requestAccess { granted in
            if granted { scanning = true } else { router.open(AppRoutes.Pairing(link: nil)) }
        }
    }

    private func openScanned() {
        guard let link = scanned else { return }
        scanned = nil
        router.open(AppRoutes.Pairing(link: link.isEmpty ? nil : link))
    }
}

/// The renewal sheet by its command; one at a time.
private struct KeyedRenewSheet: Identifiable {
    let renew: RenewSheet
    var id: String { renew.command }
}

/// What the screen's rows do; snapshot tests pass no-ops.
struct ConnectedClientActions {
    var scope: (String, Bool) -> Void = { _, _ in }
    var renew: () -> Void = {}
    var revoke: () -> Void = {}
}

/// A client for one `ConnectedClientUiState`, without the ViewModel: `connection_gone` once it is gone, a spinner
/// before the first answer, else its sections.
struct ConnectedClientContent: View {
    let state: ConnectedClientUiState
    var actions = ConnectedClientActions()

    var body: some View {
        if state.gone {
            ItmoEmptyView(symbol: .error, title: AppStrings.string("connection_gone"))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else if state.loading {
            ItmoLoadingView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else {
            ItmoScreenStack {
                if let signIn = state.signIn {
                    ItmoSectionHeader(title: AppStrings.string("connection_section_sign_in"))
                    signInGroup(signIn)
                }
                if !state.scopes.isEmpty {
                    ItmoSectionHeader(title: AppStrings.string("connection_section_access"))
                    ItmoListGroup(inset: ItmoKit.padding) {
                        ForEach(Array(state.scopes.enumerated()), id: \.offset) { _, scope in
                            ItmoSwitchRow(
                                title: scope.title.resolved,
                                subtitle: scope.subtitle.resolved,
                                enabled: scope.id != nil && !state.busy,
                                isOn: Binding(
                                    get: { scope.checked },
                                    set: { checked in scope.id.map { actions.scope($0, checked) } }
                                )
                            )
                            .accessibilityIdentifier("client.scope.\(scope.id ?? "")")
                        }
                    }
                }
                ItmoSectionHeader(title: AppStrings.string("connection_section_about"))
                ConnectionFacts(facts: state.facts)
                ItmoListGroup {
                    ItmoRow(
                        title: state.revoke.resolved,
                        symbol: .logout,
                        tone: .neutral,
                        danger: true,
                        action: actions.revoke
                    )
                    .accessibilityIdentifier("client.revoke")
                }
                .padding(.top, ItmoSpacing.compact)
            }
        }
    }

    /// The status row in its tone, then the action row while renewal is due.
    private func signInGroup(_ signIn: SignInStatus) -> some View {
        ItmoListGroup {
            ItmoRow(
                title: signIn.title.resolved,
                subtitle: signIn.text?.resolved,
                symbol: signIn.tone == .error ? .error : .key,
                tone: signIn.tone.kit
            )
            if let action = signIn.action?.resolved {
                ItmoRow(title: action, symbol: .refresh, tone: .primary, chevron: true, action: actions.renew)
                    .accessibilityIdentifier("client.renew")
            }
        }
    }
}

extension SignInTone {
    var kit: ItmoTone {
        switch self {
        case .ok: .neutral
        case .warning: .warn
        case .error: .bad
        }
    }
}

/// `connection_renew_title` (UX spec section 6): the command to run on the client with copy, the note and the scan.
struct ClientRenewSheet: View {
    let renew: RenewSheet
    let name: String
    var scan: () -> Void = {}

    var body: some View {
        ItmoSheet(title: renew.title.resolved) {
            Text(verbatim: AppStrings.string("connection_renew_lead", [name]))
                .font(.itmo(.bodyLarge))
            ItmoCard {
                HStack(spacing: ItmoSpacing.content) {
                    Text(verbatim: renew.command)
                        .font(.itmo(.bodyLarge).monospaced())
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Button {
                        UIPasteboard.general.string = renew.command
                    } label: {
                        AppSymbol.contentCopy.image
                    }
                    .itmoTint()
                    .frame(minWidth: ItmoMetrics.touchTarget, minHeight: ItmoMetrics.touchTarget)
                    .accessibilityLabel(Text(verbatim: AppStrings.string("connection_copy")))
                    .accessibilityIdentifier("client.renew.copy")
                }
            }
            ItmoGroupNote(text: renew.note.resolved)
            ItmoProminentButton(title: AppStrings.string("connection_renew_scan"), symbol: .qrCodeScanner, action: scan)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
                .padding(.top, ItmoSpacing.compact)
                .accessibilityIdentifier("client.renew.scan")
        }
        .accessibilityIdentifier("client.renew.sheet")
    }
}
