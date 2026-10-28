import Shared
import SwiftUI
import UniformTypeIdentifiers

/// The Wi-Fi sheet over the shared `WifiViewModel` (docs/features/wifi.md): ITMO-Corp, the login and the password from
/// ISU with copy buttons and the manual settings; without an ITMO.ID session on the phone `auth_login_itmo_id` opens
/// `BarsLoginSheet` and reads ISU again after it. No quick connect on iOS: `NEHotspotConfiguration` needs the Hotspot
/// Configuration capability, which re-signed builds do not get, so the user pastes the password in the Wi-Fi settings.
struct WifiSheet: View {
    @State private var model = ObservableViewModel(WifiViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?
    @State private var showsSignIn = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        ItmoSheet(title: AppStrings.string("wifi_title"), height: .content) {
            content
        }
        .overlay(alignment: .bottom) {
            if let message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: .seconds(4))
                        if self.message?.id == message.id { self.message = nil }
                    }
            }
        }
        .animation(.default, value: message?.id)
        .sheet(isPresented: $showsSignIn) {
            BarsLoginSheet(title: AppStrings.string("auth_web_title")) {
                showsSignIn = false
                model.viewModel.start()
            }
        }
        .observing(model)
        .accessibilityIdentifier("wifi.sheet")
        .onAppear { model.viewModel.start() }
    }

    @ViewBuilder
    private var content: some View {
        let state = model.state
        if let credentials = state.credentials {
            WifiCredentialsView(
                state: state,
                credentials: credentials,
                toggle: { model.viewModel.togglePassword() },
                copy: { text, secret in copy(text, secret: secret, confirm: true) }
            )
        } else {
            switch onEnum(of: state.status) {
            case .noPassword:
                unavailable("wifi_no_password_title", "wifi_no_password_description", action: "wifi_open_isu") {
                    if let url = URL(string: ItmoWifi.shared.ISU_PAGE_URL) { openURL(url) }
                }
            case .signInRequired:
                unavailable("wifi_sign_in_title", "wifi_sign_in_description", action: "auth_login_itmo_id") {
                    showsSignIn = true
                }
            case let .failed(failed):
                ItmoErrorView(
                    title: AppStrings.string("common_load_error_title"),
                    description: AppErrorTextsKt.toUiText(failed.error).resolved,
                    retry: { model.viewModel.start() }
                )
            case .loading, .ready:
                ItmoLoadingView()
            }
        }
    }

    private func unavailable(
        _ title: String, _ description: String, action: String, run: @escaping () -> Void
    ) -> some View {
        ItmoEmptyView(
            symbol: .wifi,
            title: AppStrings.string(title),
            description: AppStrings.string(description),
            action: (AppStrings.string(action), run)
        )
    }

    /// A secret stays on this device and leaves the pasteboard after two minutes.
    private func copy(_ text: String, secret: Bool, confirm: Bool) {
        let options: [UIPasteboard.OptionsKey: Any] = secret
            ? [.localOnly: true, .expirationDate: Date().addingTimeInterval(120)]
            : [:]
        UIPasteboard.general.setItems([[UTType.plainText.identifier: text]], options: options)
        if confirm { show("wifi_copied") }
    }

    private func show(_ key: String) {
        let text = AppStrings.string(key)
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}

/// The network, the login and the password (dots until shown) with their buttons, and the manual settings.
struct WifiCredentialsView: View {
    let state: WifiUiState
    let credentials: WifiCredentials
    var toggle: () -> Void = {}
    var copy: (String, Bool) -> Void = { _, _ in }

    var body: some View {
        ItmoListGroup(inset: ItmoSpacing.screenMargin) {
            field("wifi_network", state.network)
            field("wifi_login", credentials.login) {
                button(.contentCopy, "wifi_copy_login") { copy(credentials.login, false) }
            }
            .accessibilityIdentifier("wifi_login")
            let shown = state.passwordShown
            field("wifi_password", shown ? credentials.password : "••••••••", hidden: !shown) {
                button(shown ? .visibilityOff : .visibility, shown ? "wifi_password_hide" : "wifi_password_show", action: toggle)
                    .accessibilityIdentifier("wifi_password_toggle")
                button(.contentCopy, "wifi_copy_password") { copy(credentials.password, true) }
            }
            .accessibilityIdentifier("wifi_password")
        }
        Text(verbatim: AppStrings.string("wifi_manual", [state.serverDomain]))
            .font(.itmo(.bodySmall))
            .foregroundStyle(.secondary)
            .padding(.horizontal, ItmoSpacing.screenMargin)
    }

    private func field(
        _ label: String, _ value: String, hidden: Bool = false, @ViewBuilder buttons: () -> some View = { EmptyView() }
    ) -> some View {
        HStack(spacing: ItmoSpacing.compact) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: AppStrings.string(label)).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                Text(verbatim: value)
                    .font(.itmo(.bodyLarge).monospacedDigit())
                    .textSelection(.enabled)
                    .accessibilityHidden(hidden)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            buttons()
        }
        .padding(.horizontal, ItmoSpacing.screenMargin)
        .padding(.vertical, 10)
    }

    private func button(_ symbol: AppSymbol, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            symbol.image.foregroundStyle(ItmoColor.primary).frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: AppStrings.string(label)))
    }
}
