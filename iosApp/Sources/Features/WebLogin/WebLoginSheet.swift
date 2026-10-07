import Shared
import SwiftUI

/// Android's `WebLoginBottomSheet` over the shared `WebLoginViewModel` (IO-08b): approves a browser's sign-in to the
/// web version with a scanned QR (`WebLoginScanner`, VisionKit) or the code typed from the screen, shows which browser
/// asked, then signs it in with `web_login_approve`. The router opens it as a shell sheet (`ShellSheet.webLogin`);
/// a Universal Link's `code` is checked at once, as a scan.
struct WebLoginSheet: View {
    @State private var model: ObservableViewModel<WebLoginViewModel, WebLoginUiState>
    @State private var code = ""
    @State private var scanning = false
    @Environment(\.dismiss) private var dismiss
    private let linkCode: String?

    init(code: String? = nil) {
        linkCode = code
        _model = State(initialValue: Self.makeModel())
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                WebLoginContent(
                    state: model.state,
                    code: $code,
                    actions: WebLoginActions(
                        scan: scan,
                        submit: model.viewModel.submit,
                        approve: model.viewModel.approve,
                        editCode: model.viewModel.editCode,
                        retry: model.viewModel.retry,
                        close: { dismiss() }
                    )
                )
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(Text(verbatim: AppStrings.string("web_login_title")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        AppSymbol.close.image
                    }
                    .accessibilityLabel(Text(verbatim: AppStrings.string("common_close")))
                    .accessibilityIdentifier("webLogin.close")
                }
            }
        }
        .observing(model)
        .onChange(of: code) { _, typed in
            if typed != model.state.fieldCode { model.viewModel.onCodeChanged(code: typed) }
        }
        // The field is the user's while they type: the state's echo of a keystroke can arrive after the next one,
        // so the field takes the state's code only when the step changes (a check, `common_cancel`, a retry).
        .onChange(of: model.state.step, initial: true) { _, _ in
            if let shown = model.state.fieldCode, shown != code { code = shown }
        }
        .task {
            if let linkCode { model.viewModel.onScanned(raw: linkCode) }
        }
        .fullScreenCover(isPresented: $scanning) {
            WebLoginScannerScreen(
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
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("shell.sheet.webLogin")
    }

    /// The fixture build hands over the fixture link: the simulator has no camera.
    private func scan() {
        if WebLoginFixture.isOn {
            model.viewModel.onScanned(raw: WebLoginIosFixture.shared.LINK)
            return
        }
        WebLoginScanner.requestAccess { granted in
            if granted { scanning = true } else { model.viewModel.onScannerUnavailable() }
        }
    }

    private static func makeModel() -> ObservableViewModel<WebLoginViewModel, WebLoginUiState> {
        ObservableViewModel(state: \.uiState) { store in
            if let fixture = WebLoginFixture.viewModel(store: store) { return fixture }
            let parameters = WebLoginIosParameters.shared.viewModel()
            guard let viewModel = store.resolve(type: WebLoginViewModel.self, parameters: parameters)
                as? WebLoginViewModel else {
                preconditionFailure("Koin resolved no WebLoginViewModel")
            }
            return viewModel
        }
    }
}

/// What the sheet's buttons do; snapshot tests pass no-ops.
struct WebLoginActions {
    var scan: () -> Void = {}
    var submit: () -> Void = {}
    var approve: () -> Void = {}
    var editCode: () -> Void = {}
    var retry: () -> Void = {}
    var close: () -> Void = {}
}

extension WebLoginUiState {
    /// Which step the sheet shows, without its data.
    var step: String {
        switch onEnum(of: self) {
        case .input: "input"
        case .checking: "checking"
        case .confirm: "confirm"
        case .done: "done"
        case .error: "error"
        }
    }

    /// The code the field shows: typed, or being checked; nil once the field is gone.
    var fieldCode: String? {
        switch onEnum(of: self) {
        case let .input(input): input.code
        case let .checking(checking): checking.code
        case .confirm, .done, .error: nil
        }
    }
}

/// The sheet for one `WebLoginUiState`, without the ViewModel (snapshot tests render it directly). The field is the
/// caller's, so typing never waits for the state.
struct WebLoginContent: View {
    let state: WebLoginUiState
    @Binding var code: String
    var actions = WebLoginActions()

    var body: some View {
        Group {
            switch onEnum(of: state) {
            case let .input(input):
                codeInput(error: input.error?.resolved, checking: false, canSubmit: input.canSubmit)
            case .checking:
                codeInput(error: nil, checking: true, canSubmit: false)
            case let .confirm(confirm):
                WebLoginConfirm(confirm: confirm, approve: actions.approve, cancel: actions.editCode)
            case .done:
                WebLoginResult(
                    symbol: .checkCircle,
                    color: ItmoColor.primary,
                    text: AppStrings.string("web_login_done"),
                    action: AppStrings.string("common_close"),
                    perform: actions.close
                )
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("webLogin.done")
            case let .error(error):
                WebLoginResult(
                    symbol: .error,
                    color: ItmoColor.error,
                    text: error.text.resolved,
                    action: AppStrings.string("common_retry"),
                    perform: actions.retry
                )
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("webLogin.error")
            }
        }
        .padding(.horizontal, ItmoSpacing.screenMargin)
        .padding(.top, ItmoSpacing.group)
        .padding(.bottom, ItmoSpacing.group)
        .frame(maxWidth: .infinity)
    }

    private func codeInput(error: String?, checking: Bool, canSubmit: Bool) -> some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.group) {
            ItmoProgressButton(title: AppStrings.string("web_login_scan"), symbol: .qrCodeScanner, action: actions.scan)
                .disabled(checking)
                .accessibilityIdentifier("webLogin.scan")
            VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                TextField(text: $code, prompt: Text(verbatim: AppStrings.string("web_login_code_hint"))) {
                    Text(verbatim: AppStrings.string("web_login_code_hint"))
                }
                .font(.itmo(.bodyLarge).monospaced())
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .keyboardType(.asciiCapable)
                .submitLabel(.go)
                .onSubmit(actions.submit)
                .disabled(checking)
                .padding(.horizontal, ItmoSpacing.content)
                .frame(minHeight: ItmoMetrics.touchTarget)
                .background(
                    RoundedRectangle(cornerRadius: ItmoCorner.extraSmall)
                        .strokeBorder(error == nil ? ItmoColor.outline : ItmoColor.error)
                )
                .accessibilityIdentifier("webLogin.code")
                if let error {
                    Text(verbatim: error)
                        .font(.itmo(.bodySmall))
                        .foregroundStyle(ItmoColor.error)
                        .padding(.horizontal, ItmoSpacing.content)
                        .accessibilityIdentifier("webLogin.code.error")
                }
            }
            Button(action: actions.submit) {
                Text(verbatim: AppStrings.string("web_login_continue"))
                    .font(.itmo(.labelLarge))
                    .multilineTextAlignment(.center)
                    .opacity(checking ? 0 : 1)
                    .overlay {
                        if checking { ProgressView() }
                    }
                    .padding(.vertical, ItmoSpacing.compact)
                    .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.roundedRectangle(radius: ItmoCorner.large))
            .itmoTint()
            .disabled(!canSubmit && !checking)
            .allowsHitTesting(!checking)
            .accessibilityLabel(Text(verbatim: AppStrings.string("web_login_continue")))
            .accessibilityIdentifier("webLogin.continue")
        }
    }
}

/// The browser that asked, `web_login_own_only`, then `web_login_approve` or `common_cancel`.
private struct WebLoginConfirm: View {
    let confirm: WebLoginUiStateConfirm
    let approve: () -> Void
    let cancel: () -> Void

    @ScaledMetric(relativeTo: .title3) private var symbolSize: CGFloat = 24

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.group) {
            VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
                HStack(spacing: ItmoSpacing.group) {
                    AppSymbol.computer.image
                        .font(.system(size: symbolSize))
                        .foregroundStyle(ItmoColor.onSurfaceVariant)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                        Text(verbatim: confirm.browser.resolved)
                            .font(.itmo(.titleMedium))
                            .foregroundStyle(ItmoColor.onSurface)
                            .accessibilityIdentifier("webLogin.browser")
                        Text(verbatim: confirm.requestedAt.resolved)
                            .font(.itmo(.bodyMedium))
                            .foregroundStyle(ItmoColor.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(ItmoSpacing.cardPadding)
                .background(
                    RoundedRectangle(cornerRadius: ItmoShapes.cardContent)
                        .fill(ItmoColor.surfaceContainerHighest)
                )
                .accessibilityElement(children: .combine)
                Text(verbatim: AppStrings.string("web_login_own_only"))
                    .font(.itmo(.bodySmall))
                    .foregroundStyle(ItmoColor.onSurfaceVariant)
            }
            VStack(spacing: ItmoSpacing.related) {
                ItmoProgressButton(
                    title: AppStrings.string("web_login_approve"),
                    isInProgress: confirm.approving,
                    action: approve
                )
                .accessibilityIdentifier("webLogin.approve")
                Button(action: cancel) {
                    Text(verbatim: AppStrings.string("common_cancel"))
                        .font(.itmo(.labelLarge))
                        .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
                }
                .itmoTint()
                .disabled(confirm.approving)
                .accessibilityIdentifier("webLogin.cancel")
            }
        }
    }
}

/// Done and error: a symbol, the text and one action, as Android's compact content state.
private struct WebLoginResult: View {
    let symbol: AppSymbol
    let color: Color
    let text: String
    let action: String
    let perform: () -> Void

    @ScaledMetric(relativeTo: .largeTitle) private var symbolSize = ItmoSpacing.stateInlineIcon

    var body: some View {
        VStack(spacing: ItmoSpacing.group) {
            symbol.image
                .resizable()
                .scaledToFit()
                .frame(width: symbolSize, height: symbolSize)
                .foregroundStyle(color)
                .accessibilityHidden(true)
            Text(verbatim: text)
                .font(.itmo(.titleLarge))
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.updatesFrequently)
                .accessibilityIdentifier("webLogin.result.text")
            Button(action: perform) {
                Text(verbatim: action)
                    .font(.itmo(.labelLarge))
                    .padding(.horizontal, ItmoSpacing.compact)
                    .frame(minHeight: ItmoMetrics.touchTarget)
            }
            .buttonStyle(.bordered)
            .itmoTint()
            .accessibilityIdentifier("webLogin.result.action")
        }
        .frame(maxWidth: .infinity)
        .padding(.top, ItmoSpacing.section)
        .padding(.bottom, ItmoSpacing.compact)
    }
}
