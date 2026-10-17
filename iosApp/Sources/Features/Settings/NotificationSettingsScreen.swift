import Shared
import SwiftUI
import UIKit
import UserNotifications

/// the notifications page (`notifications_title`) (`AppRoutes.Settings` `NOTIFICATIONS`) over the shared `NotificationSettingsViewModel`, as Android's
/// `NotificationSettingsScreen`. The screen feeds the ViewModel what only iOS knows, on appear and on every return to
/// the app: the notification permission and Background App Refresh. iOS has no switch per push type, so those rows
/// open the app's page in Settings, or the connection page (`connection_title`) while not connected.
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
                push: { viewModel.openPush(kind: $0) },
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
            case .openChannelSettings:
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
                        try? await Task.sleep(for: .seconds(3))
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
    var push: (PushKind) -> Void = { _ in }
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
                }
                VStack(alignment: .leading, spacing: ItmoKit.gap) {
                    ItmoSectionHeader(title: AppStrings.string("notifications_what"))
                    ItmoListGroup {
                        if let marks = state.marks { marksBlock(marks) }
                        typeBlock(
                            title: "notifications_changes",
                            text: "notifications_changes_text",
                            symbol: .editCalendar,
                            tone: .warn,
                            isOn: Binding(get: { state.scheduleChanges }, set: actions.scheduleChanges),
                            preview: NotificationPreviews.shared.scheduleChanges
                        )
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("notifications.changes")
                        ForEach(state.pushes, id: \.kind) { push in pushBlock(push) }
                    }
                    ItmoGroupNote(text: AppStrings.string("notifications_push_note"))
                    ItmoGroupNote(text: AppStrings.string("notifications_night"))
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
                        subtitle: AppStrings.string("notifications_sound_text"),
                        symbol: .volumeUp,
                        chevron: true,
                        action: actions.systemSettings
                    )
                }
                .padding(.top, ItmoSpacing.compact)
                ItmoGroupNote(text: AppStrings.string("notifications_bell_note"))
            }
        }
    }

    private func marksBlock(_ marks: MarksNotifications) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            typeSwitch(
                title: "notifications_marks",
                text: "notifications_marks_text",
                symbol: .star,
                tone: .primary,
                isOn: Binding(get: { marks.enabled }, set: actions.marks)
            )
            if marks.enabled {
                ForEach(marks.sources, id: \.source) { source in
                    ItmoSwitchRow(
                        title: AppStrings.string(Self.title(source.source)),
                        subtitle: source.source == .bars ? AppStrings.string("notifications_marks_bars_text") : nil,
                        enabled: !denied,
                        isOn: Binding(get: { source.enabled }, set: { actions.markSource(source.source, $0) })
                    )
                    .padding(.leading, Self.inset - ItmoKit.padding)
                    .accessibilityIdentifier("notifications.marks.\(source.source.name.lowercased())")
                }
            }
            NotificationPreviewView(preview: NotificationPreviews.shared.marks)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("notifications.marks")
    }

    private func typeBlock(
        title: String, text: String, symbol: AppSymbol, tone: ItmoTone, isOn: Binding<Bool>, preview: NotificationPreview
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            typeSwitch(title: title, text: text, symbol: symbol, tone: tone, isOn: isOn)
            NotificationPreviewView(preview: preview)
        }
    }

    /// A type with its own switch: the icon in its tone while on, neutral while off.
    private func typeSwitch(title: String, text: String, symbol: AppSymbol, tone: ItmoTone, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: ItmoSpacing.content) {
                ItmoIconTile(symbol: symbol, tone: isOn.wrappedValue ? tone : .neutral, size: 30)
                VStack(alignment: .leading, spacing: 1) {
                    Text(verbatim: AppStrings.string(title)).font(.itmo(.bodyLarge))
                    Text(verbatim: AppStrings.string(text)).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                }
            }
        }
        .disabled(denied)
        .itmoTint()
        .padding(.horizontal, ItmoKit.padding)
        .padding(.vertical, 11)
        .frame(minHeight: 52)
    }

    /// A push: the connection it needs, the way to change it in Settings, and how it arrives.
    private func pushBlock(_ push: PushNotifications) -> some View {
        let (title, text, symbol, tone): (String, String, AppSymbol, ItmoTone) = switch push.kind {
        case .sport: ("notifications_sport", "notifications_sport_text", .sprint, .ok)
        case .friends: ("notifications_friends", "notifications_friends_text", .personAdd, .tertiary)
        case .devices: ("notifications_devices", "notifications_devices_text", .devices, .primary)
        }
        let on = push.connected && push.systemEnabled?.boolValue != false
        return VStack(alignment: .leading, spacing: 0) {
            ItmoRow(
                title: AppStrings.string(title),
                subtitle: AppStrings.string(push.connected ? text : "notifications_push_connection"),
                symbol: symbol,
                tone: on ? tone : .neutral,
                trailing: push.connected ? push.systemEnabled.map { AppStrings.string($0.boolValue ? "notifications_push_on" : "notifications_push_off") } : nil,
                chevron: true,
                action: { actions.push(push.kind) }
            )
            if push.connected { NotificationPreviewView(preview: push.preview) }
        }
        .accessibilityElement(children: .contain)
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

/// How a type arrives: a miniature of the system notification with the app's mark.
struct NotificationPreviewView: View {
    let preview: NotificationPreview

    var body: some View {
        HStack(spacing: 10) {
            AppSymbol.school.image
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 22, height: 22)
                .background(ItmoColor.primary, in: .rect(cornerRadius: 6))
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: preview.title.resolved).font(.itmo(.labelLarge, emphasized: true)).lineLimit(1)
                if let text = preview.text {
                    Text(verbatim: text.resolved).font(.itmo(.bodySmall)).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(Color(.tertiarySystemGroupedBackground), in: .rect(cornerRadius: 14))
        .padding(.leading, NotificationSettingsContent.inset)
        .padding(.trailing, ItmoKit.padding)
        .padding(.bottom, 12)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("notifications.preview")
    }
}
