import Shared
import SwiftUI

/// the connection page (`connection_title`) (`AppRoutes.Settings` `SERVICES`, also `PRIVACY`) over the shared `ConnectionViewModel`, as
/// Android's `ConnectionScreen`: the status with its switch, what the connection gives, what works without it, what
/// the server keeps, who sees what, BARS, the devices and the account deletion. Turning it on always goes through
/// `ServicesConsentSheet`.
struct ConnectionDataScreen: View {
    @State private var model = ObservableViewModel(ConnectionViewModel.self, state: \.uiState)
    @State private var showsConsent = false
    @State private var showsBarsLogin = false
    @State private var message: SettingsMessage?
    @Environment(AppRouter.self) private var router
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
                friends: { router.open(AppRoutes.Friends.shared) },
                queue: { _ = router.open(entry: EntryRoute(tab: .sport)) },
                links: { _ = router.open(entry: EntryRoute(tab: .recordbook)) },
                source: { if let url = URL(string: ProjectLinks.shared.SERVICES_SOURCE_URL) { openURL(url) } },
                bars: { showsBarsLogin = true },
                pairDevice: { router.open(AppRoutes.WebLogin(code: nil)) },
                devices: { router.open(AppRoutes.Connections.shared) },
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
        .sheet(isPresented: $showsBarsLogin) {
            BarsLoginSheet { showsBarsLogin = false }
        }
        .overlay(alignment: .bottom) {
            if let message {
                SettingsMessageBanner(text: message.text)
                    .task(id: message.id) {
                        try? await Task.sleep(for: .seconds(3))
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
    var friends: () -> Void = {}
    var queue: () -> Void = {}
    var links: () -> Void = {}
    var source: () -> Void = {}
    var bars: () -> Void = {}
    var pairDevice: () -> Void = {}
    var devices: () -> Void = {}
    var deleteAccount: () -> Void = {}
}

/// the connection page (`connection_title`) for one `ConnectionUiState`, without the ViewModel.
struct ConnectionDataContent: View {
    let state: ConnectionUiState
    var actions = ConnectionDataActions()

    var body: some View {
        ItmoScreenStack {
            if state.loaded {
                statusCard
                gives
                offline
                ItmoSectionHeader(title: AppStrings.string(state.connected ? "connection_stored_on" : "connection_stored_off"))
                StoredDataGroup {
                    ItmoRow(
                        title: AppStrings.string("connection_source"),
                        symbol: .codeBlocks,
                        chevron: true,
                        action: actions.source
                    )
                }
                ItmoGroupNote(text: AppStrings.string("services_stored_token"))
                audiences
                bars
                devices
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

    private var gives: some View {
        let on = state.connected
        let tone: ItmoTone = on ? .ok : .neutral
        return Group {
            ItmoSectionHeader(title: AppStrings.string("connection_gives"))
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("connection_friends"),
                    subtitle: state.friends?.resolved ?? AppStrings.string("connection_friends_text"),
                    symbol: .group,
                    tone: tone,
                    badge: state.friendRequests > 0 ? Int(state.friendRequests) : nil,
                    chevron: on,
                    action: on ? actions.friends : nil
                )
                ItmoRow(
                    title: AppStrings.string("connection_queue"),
                    subtitle: AppStrings.string("connection_queue_text"),
                    symbol: .sprint,
                    tone: tone,
                    chevron: on,
                    action: on ? actions.queue : nil
                )
                ItmoRow(
                    title: AppStrings.string("connection_links"),
                    subtitle: AppStrings.string(on ? "connection_links_on" : "connection_links_off"),
                    symbol: .link,
                    tone: tone,
                    chevron: on,
                    action: on ? actions.links : nil
                )
            }
        }
    }

    private var offline: some View {
        Group {
            ItmoSectionHeader(title: AppStrings.string("connection_offline"))
            ItmoCard {
                ItmoFlow(spacing: 6) {
                    ForEach(Self.offlinePills, id: \.1) { symbol, key in
                        ItmoStatusPill(pill: ItmoPill(text: AppStrings.string(key), symbol: symbol))
                    }
                }
                Text(verbatim: AppStrings.string("connection_offline_text"))
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)
            }
        }
    }

    private static let offlinePills: [(AppSymbol, String)] = [
        (.calendarMonth, "connection_offline_schedule"),
        (.book2, "connection_offline_recordbook"),
        (.grading, "connection_offline_bars"),
        (.qrCode, "connection_offline_pass"),
        (.sprint, "connection_offline_sport"),
    ]

    @ViewBuilder private var audiences: some View {
        ItmoSectionHeader(title: AppStrings.string("connection_audience"))
        switch onEnum(of: state.privacy) {
        case .onlyYou:
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("connection_only_you"),
                    subtitle: AppStrings.string("connection_only_you_text"),
                    symbol: .visibilityOff
                )
            }
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

    private var bars: some View {
        let (subtitle, tone, action): (String, ItmoTone, String?) = switch state.bars {
        case .connected: ("connection_bars_connected", .ok, nil)
        case .expired: ("connection_bars_expired", .warn, "connection_bars_sign_in")
        case .notConnected: ("connection_bars_off", .neutral, "connection_bars_connect")
        }
        return Group {
            ItmoSectionHeader(title: AppStrings.string("connection_bars"))
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("connection_bars"),
                    subtitle: AppStrings.string(subtitle),
                    symbol: .grading,
                    tone: tone
                ).accessory {
                    if let action {
                        Button(AppStrings.string(action), action: actions.bars)
                            .buttonStyle(.glass)
                            .itmoTint()
                            .accessibilityIdentifier("connection.bars.action")
                    }
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("connection.bars")
        }
    }

    private var devices: some View {
        let on = state.connected
        return Group {
            ItmoSectionHeader(title: AppStrings.string("connection_devices"))
            ItmoListGroup {
                ItmoRow(
                    title: AppStrings.string("connection_pair"),
                    subtitle: AppStrings.string(on ? "connection_pair_text" : "connection_devices_off"),
                    symbol: .qrCodeScanner,
                    chevron: on,
                    action: on ? actions.pairDevice : nil
                )
                ItmoRow(
                    title: AppStrings.string("connection_devices_list"),
                    subtitle: AppStrings.string(on ? "connection_devices_list_text" : "connection_devices_off"),
                    symbol: .devices,
                    chevron: on,
                    action: on ? actions.devices : nil
                )
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("connection.devices")
        }
    }
}
