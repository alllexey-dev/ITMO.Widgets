import Shared
import SwiftUI

/// The pairing sheet (`pair_title`, UX spec section 5) over the shared `PairingViewModel`, a route sheet
/// (`AppRoutes.Pairing`): paste a link or scan it (`PairingScanner`, VisionKit), compare the code and choose the
/// access, the screen lock (`DeviceUnlock` from Koin), then for an agent or MCP server the ITMO.ID page full screen
/// (`ClientSignInPage`), and the result. A `link` (an App Link, a renewal's scan) is read once as a scan; a renewal
/// skips the card and asks for the screen lock at once.
struct PairingSheet: View {
    let link: String?
    @State private var model = ObservableViewModel<PairingViewModel, any PairingUiState>(state: \.uiState) { store in
        let parameters = ConnectionsIosParameters.shared.pairing()
        guard let model = store.resolve(type: PairingViewModel.self, parameters: parameters) as? PairingViewModel else {
            preconditionFailure("Koin resolved no PairingViewModel")
        }
        return model
    }
    @State private var text = ""
    @State private var scanning = false
    @State private var linkRead = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ItmoSheet(title: AppStrings.string("pair_title"), height: height) {
            PairingContent(
                state: model.state,
                text: $text,
                actions: PairingActions(
                    scan: scan,
                    submit: { model.viewModel.submit() },
                    proceed: { model.viewModel.proceed(unlock: appDeviceUnlock()) },
                    scope: { id, checked in model.viewModel.onScopeChanged(scopeId: id, checked: checked) },
                    cancel: { dismiss() },
                    result: result
                )
            )
        }
        .fullScreenCover(isPresented: signingIn) {
            if let page = model.state as? PairingUiStateSigningIn {
                ClientSignInPage(
                    state: page,
                    navigation: { model.viewModel.onSignInNavigation(url: $0) },
                    close: { model.viewModel.onSignInCancelled() }
                )
            }
        }
        .fullScreenCover(isPresented: $scanning) {
            PairingScannerScreen(
                scanned: { raw in
                    scanning = false
                    model.viewModel.onScanned(raw: raw)
                },
                unavailable: {
                    scanning = false
                    model.viewModel.onScannerUnavailable()
                },
                close: { scanning = false }
            )
        }
        .observing(model)
        .task {
            guard let link, !linkRead else { return }
            linkRead = true
            model.viewModel.onScanned(raw: link)
        }
        .onChange(of: text) { _, typed in
            if (model.state as? PairingUiStateInput)?.text != typed { model.viewModel.onTextChanged(text: typed) }
        }
        // The field is the user's while they type; it takes the state's text only when the step changes (a scan
        // again starts empty).
        .onChange(of: model.state.step, initial: true) { _, _ in
            if let input = model.state as? PairingUiStateInput, input.text != text { text = input.text }
        }
        // A renewal has no card: the screen lock comes at once, once.
        .onChange(of: renewalToProceed, initial: true) { _, proceed in
            if proceed { model.viewModel.proceed(unlock: appDeviceUnlock()) }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.sheet.pairing")
    }

    /// The card with its switches is a long read: the full height; the other steps fit their content.
    private var height: ItmoSheetHeight {
        model.state is PairingUiStateConfirm ? .full : .content
    }

    private var signingIn: Binding<Bool> {
        Binding(
            get: { model.state is PairingUiStateSigningIn },
            set: { if !$0 { model.viewModel.onSignInCancelled() } }
        )
    }

    private var renewalToProceed: Bool {
        guard let confirm = model.state as? PairingUiStateConfirm else { return false }
        return confirm.renewal && !confirm.busy
    }

    private func scan() {
        PairingScanner.requestAccess { granted in
            if granted { scanning = true } else { model.viewModel.onScannerUnavailable() }
        }
    }

    private func result() {
        guard let result = model.state as? PairingUiStateResult else { return }
        if result.next == .close { dismiss() } else { model.viewModel.onResultAction() }
    }
}

extension PairingUiState {
    /// Which step the sheet shows, without its data.
    var step: String {
        switch onEnum(of: self) {
        case .input: "input"
        case .opening: "opening"
        case .confirm: "confirm"
        case .signingIn: "signingIn"
        case .sending: "sending"
        case .result: "result"
        }
    }
}

/// What the sheet's buttons do; snapshot tests pass no-ops.
struct PairingActions {
    var scan: () -> Void = {}
    var submit: () -> Void = {}
    var proceed: () -> Void = {}
    var scope: (String, Bool) -> Void = { _, _ in }
    var cancel: () -> Void = {}
    var result: () -> Void = {}
}

/// The sheet's content for one `PairingUiState`, without the ViewModel (snapshot tests render it in an `ItmoSheet`).
/// The field is the caller's, so typing never waits for the state; the sign-in page covers the screen above it.
struct PairingContent: View {
    let state: PairingUiState
    @Binding var text: String
    var actions = PairingActions()

    var body: some View {
        switch onEnum(of: state) {
        case let .input(input):
            PairingInput(input: input, text: $text, actions: actions)
        case let .confirm(confirm) where !confirm.renewal:
            PairingConfirm(confirm: confirm, actions: actions)
        case .opening, .confirm, .signingIn, .sending:
            ItmoLoadingView()
                .padding(.vertical, ItmoSpacing.section)
                .accessibilityIdentifier("pairing.progress")
        case let .result(result):
            ItmoEmptyView(
                symbol: result.success ? .checkCircle : .error,
                title: result.title.resolved,
                description: result.text.resolved,
                action: (result.action.resolved, actions.result)
            )
            .frame(maxWidth: .infinity)
            .padding(.vertical, ItmoSpacing.group)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("pairing.result")
        }
    }
}

/// The link field with its error, `pair_scan` and `pair_open`.
private struct PairingInput: View {
    let input: PairingUiStateInput
    @Binding var text: String
    let actions: PairingActions

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
            TextField(text: $text, prompt: Text(verbatim: AppStrings.string("pair_paste_hint"))) {
                Text(verbatim: AppStrings.string("pair_paste_hint"))
            }
            .font(.itmo(.bodyLarge))
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .keyboardType(.URL)
            .submitLabel(.go)
            .onSubmit(actions.submit)
            .padding(.horizontal, ItmoKit.padding)
            .frame(minHeight: ItmoMetrics.touchTarget)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: ItmoKit.cardCorner))
            .accessibilityIdentifier("pairing.field")
            if let error = input.error?.resolved {
                Text(verbatim: error)
                    .font(.itmo(.bodySmall))
                    .foregroundStyle(ItmoColor.error)
                    .padding(.horizontal, ItmoKit.padding)
                    .accessibilityIdentifier("pairing.field.error")
            }
        }
        VStack(spacing: ItmoSpacing.related) {
            ItmoProminentButton(title: AppStrings.string("pair_scan"), symbol: .qrCodeScanner, action: actions.scan)
                .controlSize(.large)
                .accessibilityIdentifier("pairing.scan")
            Button(action: actions.submit) {
                Text(verbatim: AppStrings.string("pair_open"))
                    .font(.itmo(.labelLarge))
                    .frame(minHeight: ItmoMetrics.touchTarget)
            }
            .itmoTint()
            .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .accessibilityIdentifier("pairing.open")
        }
        .frame(maxWidth: .infinity)
        .padding(.top, ItmoSpacing.compact)
    }
}

/// The client's card, the code to compare, the access it asks for, the note, then the primary action and cancel.
private struct PairingConfirm: View {
    let confirm: PairingUiStateConfirm
    let actions: PairingActions

    var body: some View {
        ItmoCard {
            HStack(spacing: ItmoSpacing.content) {
                ItmoIconTile(symbol: confirm.icon.symbol, tone: confirm.icon.tone)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: confirm.name.resolved)
                        .font(.itmo(.titleMedium, emphasized: true))
                    Text(verbatim: confirm.subtitle.resolved)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("pairing.client")
        PairingCodeCard(code: confirm.code)
        if !confirm.scopes.isEmpty {
            ItmoSectionHeader(title: AppStrings.string("pair_access"))
            ItmoListGroup(inset: ItmoKit.padding) {
                ForEach(Array(confirm.scopes.enumerated()), id: \.offset) { _, scope in
                    ItmoSwitchRow(
                        title: scope.title.resolved,
                        subtitle: scope.subtitle.resolved,
                        enabled: scope.id != nil && !confirm.busy,
                        isOn: Binding(
                            get: { scope.checked },
                            set: { checked in scope.id.map { actions.scope($0, checked) } }
                        )
                    )
                    .accessibilityIdentifier("pairing.scope.\(scope.id ?? "")")
                }
            }
        }
        ItmoGroupNote(text: confirm.note.resolved)
        VStack(spacing: ItmoSpacing.related) {
            ItmoProgressButton(title: confirm.primary.resolved, isInProgress: confirm.busy, action: actions.proceed)
                .disabled(!confirm.canProceed && !confirm.busy)
                .accessibilityIdentifier("pairing.proceed")
            Button(action: actions.cancel) {
                Text(verbatim: AppStrings.string("common_cancel"))
                    .font(.itmo(.labelLarge))
                    .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
            }
            .itmoTint()
            .disabled(confirm.busy)
            .accessibilityIdentifier("pairing.cancel")
        }
        .padding(.top, ItmoSpacing.compact)
    }
}

/// `pair_code_title`: the digits large and monospaced on a neutral card (a tinted one was too loud).
private struct PairingCodeCard: View {
    let code: String

    var body: some View {
        ItmoCard {
            Text(verbatim: AppStrings.string("pair_code_title"))
                .font(.itmo(.labelLarge))
            Text(verbatim: code)
                .font(.itmo(.displaySmall).monospaced())
                .frame(maxWidth: .infinity)
                .padding(.vertical, ItmoSpacing.related)
                .accessibilityIdentifier("pairing.code")
            Text(verbatim: AppStrings.string("pair_code_hint"))
                .font(.itmo(.bodySmall))
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}
