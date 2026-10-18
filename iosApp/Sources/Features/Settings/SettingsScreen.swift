import Shared
import SwiftUI
import UIKit
import UserNotifications
import WidgetKit

/// One settings page: the page's `SettingsViewModel` rendered by `SettingsForm` under the stack's navigation
/// bar. Android's Compose pages are not used on iOS.
///
/// The screen feeds the ViewModel what only iOS knows, on appear and on every return to the app: the notification
/// permission and Background App Refresh. It answers the page's events with system pages, the router and a short
/// message at the bottom; the calendar switch asks for calendar access (`CalendarAccess`) and the `.ics` row opens
/// `IcsExportSheet`. The events of rows iOS does not list (the tile, the custom spoiler) never come. The widgets page
/// is the gallery (`WidgetGalleryForm`): the screen loads its live previews whenever their settings change and tells
/// the ViewModel how many widgets of each format WidgetKit has placed.
struct SettingsScreen: View {
    @State private var model: ObservableViewModel<SettingsViewModel, SettingsUiState>
    @State private var system = SettingsSystemState()
    @State private var pendingToggles: [SettingRowId: Bool] = [:]
    @State private var message: SettingsMessage?
    @State private var showsCalendarRationale = false
    @State private var showsIcsExport = false
    @State private var widgetPreviews = WidgetGalleryPreviews()
    private let calendarAccess: CalendarAccess
    @Environment(AppRouter.self) private var router
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    /// [page] is a `SettingsPage` name, `AppRoutes.Settings.page`; an unknown one opens the root.
    init(page: String, calendarAccess: CalendarAccess = CalendarAccess()) {
        self.calendarAccess = calendarAccess
        _model = State(initialValue: ObservableViewModel(state: \.uiState) { store in
            let parameters: [Any?] = settingsPageParameters(page: page)
            guard let model = store.resolve(type: SettingsViewModel.self, parameters: parameters) as? SettingsViewModel
            else {
                preconditionFailure("Koin resolved no SettingsViewModel")
            }
            return model
        })
    }

    var body: some View {
        page
        .navigationTitle(Text(verbatim: model.state.page.title.resolved))
        .navigationSubtitle(Text(verbatim: model.state.gallery?.subtitle?.resolved ?? ""))
        // The root under it is inline (`itmoRootHeader`); a settings page keeps its large title.
        .toolbarTitleDisplayMode(.large)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            handle(event)
        }
        .task {
            await readSystemState()
        }
        // Kotlin's data classes compare by value (`isEqual:`), so a changed setting or theme loads the previews again.
        .task(id: model.state.gallery?.cards.compactMap { $0.preview as? NSObject }) {
            guard let cards = model.state.gallery?.cards else { return }
            widgetPreviews = await WidgetGalleryPreviews.load(cards)
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task { await readSystemState() }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.backgroundRefreshStatusDidChangeNotification)) { _ in
            Task { await readSystemState() }
        }
        .onChange(of: model.state) {
            pendingToggles = [:]
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
        .animation(.default, value: message?.id)
        .calendarAccessRationale(isPresented: $showsCalendarRationale) {
            open(UIApplication.openSettingsURLString)
        }
        .sheet(isPresented: $showsIcsExport) {
            IcsExportSheet()
        }
        .accessibilityIdentifier("settings.page.\(model.state.page.name.lowercased())")
    }

    @ViewBuilder
    private var page: some View {
        if model.state.loaded, let gallery = model.state.gallery {
            WidgetGalleryForm(
                gallery: gallery,
                sections: model.state.sections,
                previews: widgetPreviews,
                pendingToggles: pendingToggles,
                actions: actions
            )
        } else {
            SettingsForm(state: model.state, system: system, pendingToggles: pendingToggles, actions: actions)
        }
    }

    private var actions: SettingsFormActions {
        let viewModel = model.viewModel
        return SettingsFormActions(
            toggle: { id, checked in
                pendingToggles[id] = checked
                viewModel.onToggleChanged(id: id, checked: checked)
            },
            choose: { id, key in viewModel.onChoiceChanged(id: id, optionKey: key) },
            act: { id in viewModel.onAction(id: id) }
        )
    }

    private func handle(_ event: any SettingsEvent) {
        switch onEnum(of: event) {
        case .widgetsRefreshStarted:
            show(AppStrings.string("settings_refresh_widgets_started"))
        case .openDiagnostics:
            router.open(AppRoutes.Diagnostics.shared)
        case .closeOverlays:
            // The onboarding flag is reset; the session gate shows the first-run flow, settings only leave.
            router.setPath([], of: router.selectedTab)
        case let .openWebPage(page):
            if let url = SettingsLinks.webPage(page.path) { openURL(url) }
        case let .showMessage(shown):
            pendingToggles = [:]
            show(shown.text.resolved)
        case let .showError(failure):
            pendingToggles = [:]
            show(AppErrorTextsKt.toUiText(failure.error).resolved)
        case .requestCalendarAccess:
            Task { await turnCalendarSyncOn() }
        case .openIcsExport:
            showsIcsExport = true
        case .chooseCustomSpoiler, .resetCustomSpoiler, .requestQrTile:
            // Rows `PlatformCapabilities` hides on iOS.
            break
        }
    }

    /// VoiceOver reads the message as it appears, as TalkBack reads a snackbar.
    private func show(_ text: String) {
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }

    /// The sync turns on with full access; a refusal just now says so, a refusal for good shows the rationale with
    /// the app's page in Settings. The switch goes back off unless the sync turns on.
    private func turnCalendarSyncOn() async {
        switch await calendarAccess.turnOn() {
        case .granted:
            model.viewModel.onCalendarAccessGranted()
        case .refused:
            pendingToggles = [:]
            show(AppStrings.string("calendar_access_denied"))
        case .rationale:
            pendingToggles = [:]
            showsCalendarRationale = true
        }
    }

    private func readSystemState() async {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        let granted = [.authorized, .provisional, .ephemeral].contains(status)
        system.backgroundRefresh = UIApplication.shared.backgroundRefreshStatus
        model.viewModel.onNotificationPermissionChanged(granted: granted)
        await countPlacedWidgets()
    }

    /// The home screen widgets of each format WidgetKit has placed; Lock Screen accessories are not counted.
    private func countPlacedWidgets() async {
        guard let configurations = try? await WidgetCenter.shared.currentConfigurations() else { return }
        let home: Set<WidgetFamily> = [.systemSmall, .systemMedium, .systemLarge, .systemExtraLarge]
        let kinds: [WidgetFormat: String] = [
            .next: NextLessonWidget.kind, .day: DayWidget.kind, .list: ClassicListWidget.kind, .sport: SportWidget.kind,
            .pass: QrWidget.kind,
        ]
        for (format, kind) in kinds {
            let count = configurations.filter { $0.kind == kind && home.contains($0.family) }.count
            model.viewModel.onPlacedWidgets(format: format, count: Int32(count))
        }
    }

    private func open(_ address: String) {
        if let url = URL(string: address) { openURL(url) }
    }
}

/// The site pages a settings row opens: `path` under the build's `BackendBaseURL`, which is the site's origin too.
enum SettingsLinks {
    static func webPage(_ path: String, bundle: Bundle = .main) -> URL? {
        guard let origin = bundle.object(forInfoDictionaryKey: "BackendBaseURL") as? String else { return nil }
        return URL(string: origin + path)
    }
}

/// A short message of the page; a new one replaces the shown one.
struct SettingsMessage: Equatable {
    let id = UUID()
    let text: String
}

/// The message at the bottom of a settings page, what a snackbar is on Android; it leaves after a few seconds.
struct SettingsMessageBanner: View {
    let text: String

    var body: some View {
        Text(verbatim: text)
            .font(.itmo(.bodyMedium))
            .foregroundStyle(Color(uiColor: .systemBackground))
            .padding(.horizontal, ItmoSpacing.screenMargin)
            .padding(.vertical, ItmoSpacing.content)
            .background(Capsule().fill(Color(uiColor: .label)))
            .padding(.horizontal, ItmoSpacing.screenMargin)
            .padding(.bottom, ItmoSpacing.section)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .accessibilityIdentifier("settings.message")
    }
}
