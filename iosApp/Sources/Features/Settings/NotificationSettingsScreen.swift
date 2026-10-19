import Shared
import SwiftUI
import UIKit
import UserNotifications

/// the notifications page (`notifications_title`) (`AppRoutes.Settings` `NOTIFICATIONS`) over the shared `NotificationSettingsViewModel`, as Android's
/// `NotificationSettingsScreen`. The screen feeds the ViewModel what only iOS knows, on appear and on every return to
/// the app: the notification permission and Background App Refresh. Every type has the app's own switch, except the
/// pushes: without `com.apple.developer.usernotifications.filtering` the notification service cannot hide a push, so
/// the ViewModel lists none (`PlatformCapabilities.pushSwitches`).
struct NotificationSettingsScreen: View {
    @State private var model = ObservableViewModel(NotificationSettingsViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?
    @State private var showsBackgroundHint = false
    @Environment(AppRouter.self) private var router
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        let viewModel = model.viewModel
        NotificationSettingsContent(
            state: model.state,
            actions: NotificationSettingsActions(
                marks: { viewModel.setMarks(enabled: $0) },
                markSource: { viewModel.setMarkSource(source: $0, enabled: $1) },
                scheduleChanges: { viewModel.setScheduleChanges(enabled: $0) },
                push: { viewModel.setPush(kind: $0, enabled: $1) },
                connection: { viewModel.openConnection() },
                requestPermission: { viewModel.requestPermission() },
                systemSettings: { viewModel.openSystemSettings() },
                backgroundWork: { viewModel.openBackgroundWorkSettings() }
            )
        )
        .itmoDetailHeader(title: AppStrings.string("notifications_title"), subtitle: model.state.subtitle?.resolved)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case .requestPermission:
                Task { await requestPermission() }
            case .openSystemSettings:
                open(UIApplication.openNotificationSettingsURLString)
            case .openConnection:
                router.open(AppRoutes.Settings(page: SettingsPage.services.name))
            case .showBackgroundWorkHint:
                showsBackgroundHint = true
            case .openBackgroundWorkSettings:
                open(UIApplication.openSettingsURLString)
            case let .showError(failure):
                let text = AppErrorTextsKt.toUiText(failure.error).resolved
                message = SettingsMessage(text: text)
                AccessibilityNotification.Announcement(text).post()
            }
        }
        .task { await readSystemState() }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await readSystemState() }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.backgroundRefreshStatusDidChangeNotification)) { _ in
            Task { await readSystemState() }
        }
        .alert(Text(verbatim: AppStrings.string("ios_background_refresh_title")), isPresented: $showsBackgroundHint) {
            Button {
                open(UIApplication.openSettingsURLString)
            } label: {
                Text(verbatim: AppStrings.string("ios_background_refresh_open_settings"))
            }
            Button(role: .cancel) {} label: {
                Text(verbatim: AppStrings.string("background_work_later"))
            }
        } message: {
            Text(verbatim: AppStrings.string("ios_background_refresh_off"))
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
        .accessibilityIdentifier("notifications.screen")
    }

    /// Asks once while iOS has never asked; after that only Settings can change the answer.
    private func requestPermission() async {
        let center = UNUserNotificationCenter.current()
        if await center.notificationSettings().authorizationStatus == .notDetermined {
            _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
            await readSystemState()
        } else {
            open(UIApplication.openNotificationSettingsURLString)
        }
    }

    private func readSystemState() async {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        if status == .notDetermined {
            model.viewModel.onPermissionNotAsked()
        } else {
            model.viewModel.onPermissionChanged(granted: [.authorized, .provisional, .ephemeral].contains(status))
        }
        model.viewModel.onBackgroundWorkChanged()
    }

    private func open(_ address: String) {
        if let url = URL(string: address) { openURL(url) }
    }
}

/// What the page's rows do; snapshot tests pass no-ops.
struct NotificationSettingsActions {
    var marks: (Bool) -> Void = { _ in }
    var markSource: (MarkCheckSource, Bool) -> Void = { _, _ in }
    var scheduleChanges: (Bool) -> Void = { _ in }
    var push: (PushKind, Bool) -> Void = { _, _ in }
    var connection: () -> Void = {}
    var requestPermission: () -> Void = {}
    var systemSettings: () -> Void = {}
    var backgroundWork: () -> Void = {}
}

/// the notifications page (`notifications_title`) for one `NotificationSettingsUiState`, without the ViewModel.
struct NotificationSettingsContent: View {
    let state: NotificationSettingsUiState
    var actions = NotificationSettingsActions()

    private var denied: Bool { state.permission == .denied }

    var body: some View {
        ItmoScreenStack {
            if state.loaded {
                if denied {
                    ItmoGateCard(
                        title: AppStrings.string("notifications_gate_title"),
                        reason: AppStrings.string("notifications_gate_text"),
                        fix: (AppStrings.string("notifications_gate_fix"), actions.requestPermission),
                        symbol: .notificationsOff,
                        tone: .bad
                    )
                } else if state.systemChannelsOff {
                    ItmoGateCard(
                        title: AppStrings.string("notifications_channels_title"),
                        reason: AppStrings.string("notifications_channels_text"),
                        fix: (AppStrings.string("notifications_channels_fix"), actions.systemSettings),
                        symbol: .notificationsOff,
                        tone: .bad
                    )
                }
                VStack(alignment: .leading, spacing: ItmoKit.gap) {
                    ItmoSectionHeader(title: AppStrings.string("notifications_what"))
                    ItmoListGroup {
                        if let marks = state.marks { marksBlock(marks) }
                        typeSwitch(
                            title: "notifications_changes",
                            symbol: .editCalendar,
                            tone: .warn,
                            isOn: Binding(get: { state.scheduleChanges }, set: actions.scheduleChanges)
                        )
                        .accessibilityIdentifier("notifications.changes")
                        // The connection only gates the push switches, which iOS does not list yet.
                        if !state.connected && !state.pushes.isEmpty {
                            ItmoRow(
                                title: AppStrings.string("notifications_push_connection"),
                                symbol: .cloudOff,
                                tone: .neutral,
                                chevron: true,
                                action: actions.connection
                            )
                            .accessibilityIdentifier("notifications.connection")
                        }
                        ForEach(state.pushes, id: \.kind) { push in pushSwitch(push) }
                    }
                }
                .opacity(denied ? 0.8 : 1)
                if state.backgroundWorkRestricted {
                    ItmoListGroup {
                        ItmoRow(
                            title: AppStrings.string("ios_background_refresh_title"),
                            subtitle: AppStrings.string("ios_background_refresh_off"),
                            symbol: .openInNew,
                            action: actions.backgroundWork
                        )
                    }
                    .padding(.top, ItmoSpacing.compact)
                    .accessibilityIdentifier("notifications.background")
                }
                ItmoListGroup {
                    ItmoRow(
                        title: AppStrings.string("notifications_sound"),
                        symbol: .volumeUp,
                        chevron: true,
                        action: actions.systemSettings
                    )
                }
                .padding(.top, ItmoSpacing.compact)
            }
        }
    }

    private func marksBlock(_ marks: MarksNotifications) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            typeSwitch(
                title: "notifications_marks",
                symbol: .star,
                tone: .primary,
                isOn: Binding(get: { marks.enabled }, set: actions.marks)
            )
            if marks.enabled {
                ForEach(marks.sources, id: \.source) { source in
                    ItmoSwitchRow(
                        title: AppStrings.string(Self.title(source.source)),
                        enabled: !denied,
                        isOn: Binding(get: { source.enabled }, set: { actions.markSource(source.source, $0) })
                    )
                    .padding(.leading, Self.inset - ItmoKit.padding)
                    .accessibilityIdentifier("notifications.marks.\(source.source.name.lowercased())")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("notifications.marks")
    }

    /// A type with its switch: the icon in its tone while on, neutral while off or unavailable.
    private func typeSwitch(
        title: String, text: String? = nil, symbol: AppSymbol, tone: ItmoTone, enabled: Bool = true, isOn: Binding<Bool>
    ) -> some View {
        let active = enabled && !denied
        return Toggle(isOn: isOn) {
            HStack(spacing: ItmoSpacing.content) {
                ItmoIconTile(symbol: symbol, tone: isOn.wrappedValue && active ? tone : .neutral, size: 30)
                VStack(alignment: .leading, spacing: 1) {
                    Text(verbatim: AppStrings.string(title)).font(.itmo(.bodyLarge))
                    if let text {
                        Text(verbatim: AppStrings.string(text)).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                    }
                }
            }
        }
        .disabled(!active)
        .itmoTint()
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
    }

    /// A push type: the app's switch; it waits for the connection, without which Backend sends nothing.
    private func pushSwitch(_ push: PushNotifications) -> some View {
        let (title, symbol, tone): (String, AppSymbol, ItmoTone) = switch push.kind {
        case .sport: ("notifications_sport", .sprint, .ok)
        case .friends: ("notifications_friends", .personAdd, .tertiary)
        case .devices: ("notifications_devices", .devices, .primary)
        }
        return typeSwitch(
            title: title,
            text: push.kind == .devices ? "notifications_devices_text" : nil,
            symbol: symbol,
            tone: tone,
            enabled: state.connected,
            isOn: Binding(get: { push.enabled }, set: { actions.push(push.kind, $0) })
        )
        .accessibilityIdentifier("notifications.push.\(push.kind.name.lowercased())")
    }

    private static func title(_ source: MarkCheckSource) -> String {
        switch source {
        case .myItmo: "notifications_marks_myitmo"
        case .bars: "notifications_marks_bars"
        case .sheets: "notifications_marks_sheets"
        }
    }

    /// Under the row's text, past its icon tile.
    static let inset: CGFloat = 58
}
