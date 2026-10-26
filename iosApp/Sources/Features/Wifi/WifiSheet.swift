import NetworkExtension
import Shared
import SwiftUI
import UniformTypeIdentifiers

/// The Wi-Fi sheet over the shared `WifiViewModel` (docs/features/wifi.md): ITMO-Corp, the login and the password from
/// ISU with copy buttons, and the connect button (`wifi_connect`), which asks iOS to join the network with
/// `NEHotspotConfiguration` (EAP-PEAP, the server checked by the name `wifi.it.itmo.ru`). A build without the Hotspot
/// Configuration entitlement, or any other refusal, copies the password instead and says where to paste it; iOS has
/// no link to its Wi-Fi settings.
struct WifiSheet: View {
    @State private var model = ObservableViewModel(WifiViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?
    @State private var isConnecting = false
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
            ItmoProgressButton(title: AppStrings.string("wifi_connect"), symbol: .wifi, isInProgress: isConnecting) {
                Task { await connect(credentials) }
            }
            .accessibilityIdentifier("wifi.connect")
        } else {
            switch onEnum(of: state.status) {
            case .noPassword:
                unavailable("wifi_no_password_title", "wifi_no_password_description")
            case .signInRequired:
                unavailable("wifi_sign_in_title", "wifi_sign_in_description")
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

    private func unavailable(_ title: String, _ description: String) -> some View {
        ItmoEmptyView(
            symbol: .wifi,
            title: AppStrings.string(title),
            description: AppStrings.string(description),
            action: (AppStrings.string("wifi_open_isu"), {
                if let url = URL(string: ItmoWifi.shared.ISU_PAGE_URL) { openURL(url) }
            })
        )
    }

    /// Saves and joins ITMO-Corp; a refusal other than the user's own copies the password for the manual way.
    private func connect(_ credentials: WifiCredentials) async {
        let eap = NEHotspotEAPSettings()
        eap.supportedEAPTypes = [NSNumber(value: NEHotspotEAPSettings.EAPType.EAPPEAP.rawValue)]
        eap.username = credentials.login
        eap.password = credentials.password
        eap.trustedServerNames = [ItmoWifi.shared.SERVER_DOMAIN]
        let configuration = NEHotspotConfiguration(ssid: ItmoWifi.shared.SSID, eapSettings: eap)
        isConnecting = true
        defer { isConnecting = false }
        do {
            try await NEHotspotConfigurationManager.shared.apply(configuration)
            show("wifi_saved")
        } catch let error as NSError where error.domain == NEHotspotConfigurationErrorDomain
            && error.code == NEHotspotConfigurationError.alreadyAssociated.rawValue {
            show("wifi_saved")
        } catch let error as NSError where error.domain == NEHotspotConfigurationErrorDomain
            && error.code == NEHotspotConfigurationError.userDenied.rawValue {
            // The user said no in the system's alert.
        } catch {
            copy(credentials.password, secret: true, confirm: false)
            show("wifi_save_failed")
        }
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
