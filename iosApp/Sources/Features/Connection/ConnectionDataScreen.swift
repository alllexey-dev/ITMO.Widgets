import Shared
import SwiftUI

// shortcut: feature-account's connected-client `ConnectionViewModel` and `ConnectionUiState` take the plain Swift
// names, so SKIE exports the settings ones with a `_` suffix by module order; drop these aliases once Kotlin gives one
// pair an `@ObjCName`.
typealias ServicesViewModel = ConnectionViewModel_
typealias ServicesUiState = ConnectionUiState_

/// the connection page (`connection_title`) (`AppRoutes.Settings` `SERVICES`, also `PRIVACY`) over the shared
/// settings `ConnectionViewModel` (`ServicesViewModel`), as Android's `ConnectionScreen`: the status with its switch,
/// then while connected who sees what, before it what the connection gives; what the server keeps opens in a sheet,
/// and the account deletion comes last. Turning it on always goes through `ServicesConsentSheet`. The devices live in
/// the devices screen (`devices_title`) of the profile.
struct ConnectionDataScreen: View {
    @State private var model = ObservableViewModel(ServicesViewModel.self, state: \.uiState)
    @State private var showsConsent = false
    @State private var showsStoredData = false
    @State private var message: SettingsMessage?
    @Environment(\.openURL) private var openURL

    var body: some View {
        ConnectionDataContent(
            state: model.state,
            actions: ConnectionDataActions(
                switchChanged: { on in
                    if on { showsConsent = true } else { model.viewModel.setConnected(enabled: false) }
                },
                audience: { topic, visibility in model.viewModel.setAudience(topic: topic, visibility: visibility) },
                retryPrivacy: { model.viewModel.retryPrivacy() },
                storedData: { showsStoredData = true },
                deleteAccount: { model.viewModel.deleteAccount() }
            )
        )
        .itmoDetailHeader(title: AppStrings.string("connection_title"), subtitle: nil)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case let .showError(failure):
                let text = AppErrorTextsKt.toUiText(failure.error).resolved
                message = SettingsMessage(text: text)
                AccessibilityNotification.Announcement(text).post()
            case let .openWebPage(page):
                if let url = SettingsLinks.webPage(page.path) { openURL(url) }
            }
        }
        .sheet(isPresented: $showsConsent) {
            ServicesConsentSheet {
                showsConsent = false
                model.viewModel.setConnected(enabled: true)
            }
        }
        .sheet(isPresented: $showsStoredData) {
            StoredDataSheet(connected: model.state.connected) {
                if let url = URL(string: ProjectLinks.shared.SERVICES_SOURCE_URL) { openURL(url) }
            }
        }
        .overlay(alignment: .bottom) {
            if let message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: SettingsMessageBanner.lifetime)
                        if self.message?.id == message.id { self.message = nil }
                    }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("connection.screen")
    }
}

/// What the page's rows do; snapshot tests pass no-ops.
struct ConnectionDataActions {
    var switchChanged: (Bool) -> Void = { _ in }
    var audience: (PrivacyTopic, SharingVisibility) -> Void = { _, _ in }
    var retryPrivacy: () -> Void = {}
    var storedData: () -> Void = {}
    var deleteAccount: () -> Void = {}
}

/// the connection page (`connection_title`) for one `ServicesUiState`, without the ViewModel.
struct ConnectionDataContent: View {
    let state: ServicesUiState
    var actions = ConnectionDataActions()

    var body: some View {
        ItmoScreenStack {
            if state.loaded {
                statusCard
                if state.connected { audiences } else { gives }
                ItmoListGroup {
                    ItmoRow(
                        title: AppStrings.string(state.connected ? "connection_stored_on" : "connection_stored_off"),
                        symbol: .folder,
                        chevron: true,
                        action: actions.storedData
                    )
                    .accessibilityIdentifier("connection.stored")
                }
                .padding(.top, ItmoSpacing.group)
                ItmoListGroup {
                    ItmoRow(
                        title: AppStrings.string("connection_delete"),
                        subtitle: AppStrings.string("connection_delete_text"),
                        symbol: .delete,
                        tone: .bad,
                        chevron: true,
                        danger: true,
                        action: actions.deleteAccount
                    )
                }
                .padding(.top, ItmoSpacing.group)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("connection.delete")
                ItmoGroupNote(text: AppStrings.string("connection_delete_note"))
            }
        }
    }

    private var statusCard: some View {
        ItmoCard(tone: state.connected ? .primary : .neutral, hero: true) {
            HStack(spacing: ItmoSpacing.content) {
                ItmoIconTile(symbol: state.connected ? .cloudDone : .cloudOff, tone: state.connected ? .ok : .neutral, size: 48)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: AppStrings.string(state.connected ? "connection_status_on" : "connection_status_off"))
                        .font(.itmo(.headlineSmall, emphasized: true))
                    Text(verbatim: AppStrings.string(state.connected ? "connection_status_on_detail" : "connection_status_off_detail"))
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                }
            }
            Text(verbatim: AppStrings.string("connection_status_text"))
                .font(.itmo(.bodyMedium))
                .padding(.top, ItmoSpacing.compact)
            Toggle(isOn: Binding(get: { state.connected }, set: actions.switchChanged)) {
                Text(verbatim: AppStrings.string("connection_switch")).font(.itmo(.bodyLarge))
            }
            .disabled(!state.changeable)
            .itmoTint()
            .padding(.top, ItmoSpacing.related)
            .accessibilityIdentifier("connection.switch")
        }
    }

    /// What the connection gives, before it is on.
    private var gives: some View {
        Group {
            ItmoSectionHeader(title: AppStrings.string("connection_gives"))
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("connection_friends"),
                    subtitle: AppStrings.string("connection_friends_text"),
                    symbol: .group
                )
                ItmoRow(
                    title: AppStrings.string("connection_queue"),
                    subtitle: AppStrings.string("connection_queue_text"),
                    symbol: .sprint
                )
                ItmoRow(
                    title: AppStrings.string("connection_links"),
                    subtitle: AppStrings.string("connection_links_off"),
                    symbol: .link
                )
            }
        }
    }

    @ViewBuilder private var audiences: some View {
        ItmoSectionHeader(title: AppStrings.string("connection_audience"))
        switch onEnum(of: state.privacy) {
        case .onlyYou:
            // Only while not connected, when the page has no audiences.
            EmptyView()
        case .loading:
            ItmoCard {
                ProgressView().itmoTint().frame(maxWidth: .infinity).padding(ItmoSpacing.related)
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("connection.audiences")
        case .failed:
            ItmoListGroup {
                ItmoRow(title: AppStrings.string("connection_audience_failed")).accessory {
                    Button(AppStrings.string("common_retry"), action: actions.retryPrivacy)
                        .buttonStyle(.glass)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("connection.audiences")
        case let .audiences(audiences):
            ItmoListGroup {
                audienceRow(.schedule, audiences.schedule, enabled: !audiences.updating)
                audienceRow(.sport, audiences.sport, enabled: !audiences.updating)
                audienceRow(.friends, audiences.friends, enabled: !audiences.updating)
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("connection.audiences")
            ItmoGroupNote(text: AppStrings.string("connection_audience_note"))
        }
    }

    /// A privacy choice: a pull-down menu with the current audience, as iOS settings do.
    private func audienceRow(_ topic: PrivacyTopic, _ value: SharingVisibility, enabled: Bool) -> some View {
        let (symbol, key): (AppSymbol, String) = switch topic {
        case .schedule: (.calendarMonth, "connection_audience_schedule")
        case .sport: (.sprint, "connection_audience_sport")
        case .friends: (.group, "connection_audience_friends")
        }
        return HStack(spacing: ItmoSpacing.content) {
            ItmoRowIcon(symbol: symbol)
            Text(verbatim: AppStrings.string(key)).font(.itmo(.bodyLarge))
            Spacer(minLength: ItmoSpacing.compact)
            Menu {
                Picker(selection: Binding(get: { value }, set: { actions.audience(topic, $0) })) {
                    ForEach(SharingVisibility.allCases, id: \.self) { option in
                        Text(verbatim: Self.label(option)).tag(option)
                    }
                } label: {
                    EmptyView()
                }
            } label: {
                HStack(spacing: 4) {
                    Text(verbatim: Self.label(value))
                    Image(systemName: "chevron.up.chevron.down").font(.caption)
                }
                .font(.itmo(.bodyLarge))
            }
            .disabled(!enabled)
            .itmoTint()
            .accessibilityIdentifier("connection.audience.\(topic.name.lowercased())")
        }
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
    }

    private static func label(_ visibility: SharingVisibility) -> String {
        switch visibility {
        case .all: AppStrings.string("settings_privacy_all")
        case .friends: AppStrings.string("settings_privacy_friends")
        case .nobody: AppStrings.string("settings_privacy_nobody")
        }
    }
}

/// What the server keeps (or will keep), the link to its code and the token note, as Android's `StoredDataSheet`.
struct StoredDataSheet: View {
    let connected: Bool
    let source: () -> Void

    var body: some View {
        ItmoSheet(title: AppStrings.string(connected ? "connection_stored_on" : "connection_stored_off")) {
            StoredDataGroup {
                ItmoRow(
                    title: AppStrings.string("connection_source"),
                    symbol: .codeBlocks,
                    chevron: true,
                    action: source
                )
            }
            ItmoGroupNote(text: AppStrings.string("services_stored_token"))
        }
        .accessibilityIdentifier("connection.stored.sheet")
    }
}
