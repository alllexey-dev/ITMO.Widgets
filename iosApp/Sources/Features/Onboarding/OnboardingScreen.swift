import Shared
import SwiftUI
import UIKit
import UserNotifications

/// The first-run flow over the shared `OnboardingViewModel` (DECISIONS.md, Первый запуск), as Android's
/// `OnboardingScreen`: what the app does with the user's own data, the permissions one by one (notifications through
/// `requestAuthorization`, the calendar through `CalendarAccess`), the connection through `ServicesConsentSheet` or
/// `Не сейчас`. Every choice is stored as it is made; `finished` runs once the flow ends, after the stored flag is
/// written.
struct OnboardingScreen: View {
    let finished: () -> Void
    var calendarAccess = CalendarAccess()

    @State private var model = Self.makeModel()
    @State private var errorText: String?
    @State private var showsConsent = false
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        OnboardingContent(state: model.state, actions: actions)
            .observing(model)
            .onEvents(of: model, \.events, perform: handle)
            .task { await readNotificationPermission() }
            .onChange(of: scenePhase) { _, phase in
                // Back from the notification settings: the row follows what the user chose there.
                if phase == .active { Task { await readNotificationPermission() } }
            }
            .onChange(of: model.state.finished, initial: true) { _, done in
                if done { finished() }
            }
            .sheet(isPresented: $showsConsent) {
                ServicesConsentSheet {
                    showsConsent = false
                    model.viewModel.onConsent()
                }
            }
            .alert(
                Text(verbatim: errorText ?? ""),
                isPresented: Binding(get: { errorText != nil }, set: { if !$0 { errorText = nil } })
            ) {
                Button(role: .cancel) {} label: {
                    Text(verbatim: AppStrings.string("common_got_it"))
                }
            }
    }

    /// The flow's ViewModel from the app's graph, with a fresh `SavedStateHandle` (`OnboardingIosParameters`).
    static func makeModel() -> ObservableViewModel<OnboardingViewModel, OnboardingUiState> {
        ObservableViewModel(state: \.uiState) { store in
            let parameters = OnboardingIosParameters.shared.viewModel()
            guard let viewModel = store.resolve(type: OnboardingViewModel.self, parameters: parameters)
                as? OnboardingViewModel else {
                preconditionFailure("Koin resolved no OnboardingViewModel")
            }
            return viewModel
        }
    }

    private var actions: OnboardingActions {
        let viewModel = model.viewModel
        return OnboardingActions(
            next: viewModel.next,
            back: viewModel.back,
            requestNotifications: viewModel.requestNotifications,
            addCalendar: viewModel.addLessonsToCalendar,
            connect: viewModel.connect,
            notNow: viewModel.notNow,
            details: { showsConsent = true }
        )
    }

    private func handle(_ event: OnboardingEvent) {
        switch onEnum(of: event) {
        case .requestNotificationPermission:
            Task { await requestNotificationPermission() }
        case .openNotificationSettings:
            if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
        case .requestCalendarAccess:
            Task {
                if await calendarAccess.turnOn() == .granted { model.viewModel.onCalendarAccessGranted() }
            }
        case .showConsent:
            showsConsent = true
        case let .showError(event):
            errorText = AppErrorTextsKt.toUiText(event.error).resolved
        }
    }

    /// The system dialog; its answer is the row's, and the push registration's `alertsAllowed`.
    private func requestNotificationPermission() async {
        let center = UNUserNotificationCenter.current()
        let granted = (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        model.viewModel.onNotificationPermission(granted: granted)
    }

    private func readNotificationPermission() async {
        let status = await UNUserNotificationCenter.current().notificationSettings().authorizationStatus
        model.viewModel.onNotificationPermission(granted: Self.allowsAlerts(status))
    }

    static func allowsAlerts(_ status: UNAuthorizationStatus) -> Bool {
        switch status {
        case .authorized, .provisional, .ephemeral: true
        default: false
        }
    }
}

/// The callbacks of the flow, one per `OnboardingViewModel` entry point, plus the consent sheet to read.
struct OnboardingActions {
    var next: () -> Void = {}
    var back: () -> Void = {}
    var requestNotifications: () -> Void = {}
    var addCalendar: () -> Void = {}
    var connect: () -> Void = {}
    var notNow: () -> Void = {}
    var details: () -> Void = {}
}

/// The flow for one `OnboardingUiState`, without the ViewModel (snapshot tests render it directly): the step dots
/// with `1 из 3` and the back button, the step's title and line over its body, and its actions pinned at the bottom.
struct OnboardingContent: View {
    let state: OnboardingUiState
    let actions: OnboardingActions

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: ItmoKit.gap) {
                    Text(verbatim: AppStrings.string(title))
                        .font(.largeTitle.bold())
                        .padding(.horizontal, 4)
                        .padding(.top, ItmoSpacing.compact)
                        .accessibilityAddTraits(.isHeader)
                    Text(verbatim: AppStrings.string(line))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 4)
                        .padding(.bottom, 12)
                    step
                }
                .padding(.horizontal, ItmoKit.margin)
                .padding(.bottom, ItmoSpacing.group)
            }
            .id(state.step)
            .transition(.opacity)
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: state.step)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("onboarding.step.\(state.step.name.lowercased())")
            footer
        }
        .background(Color(.systemGroupedBackground))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("onboarding.screen")
    }

    private var stepIndex: Int { Int(state.stepIndex) }

    private var title: String {
        switch state.step {
        case .value: "onboarding_value_title"
        case .permissions: "onboarding_permissions_title"
        case .connection: "onboarding_connection_title"
        }
    }

    private var line: String {
        switch state.step {
        case .value: "onboarding_value_text"
        case .permissions: "onboarding_permissions_text"
        case .connection: "onboarding_connection_text"
        }
    }

    private var header: some View {
        HStack(spacing: ItmoSpacing.compact) {
            Button(action: actions.back) {
                AppSymbol.arrowBack.image
                    .frame(minWidth: ItmoMetrics.touchTarget, minHeight: ItmoMetrics.touchTarget)
            }
            .itmoTint()
            .accessibilityLabel(Text(verbatim: AppStrings.string("common_back")))
            .accessibilityIdentifier("onboarding.back")
            // The first step has nowhere to go back to; the button keeps its place.
            .opacity(stepIndex > 0 ? 1 : 0)
            .disabled(stepIndex == 0 || state.finished)
            OnboardingStepDots(count: Int(state.stepCount), current: stepIndex)
            Spacer(minLength: 0)
            Text(verbatim: AppStrings.string("onboarding_step_progress", [stepIndex + 1, Int(state.stepCount)]))
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, ItmoSpacing.screenMargin - ItmoSpacing.compact)
        .padding(.top, ItmoSpacing.compact)
    }

    @ViewBuilder
    private var step: some View {
        switch state.step {
        case .value: OnboardingValueStep(state: state)
        case .permissions: OnboardingPermissionsStep(state: state, actions: actions)
        case .connection: OnboardingConnectionStep(state: state, actions: actions)
        }
    }

    /// `Далее` on the first two steps; `Подключить` and `Не сейчас` on the last, `Готово` once connected.
    private var footer: some View {
        let last = state.step == .connection
        let primary = !last ? "onboarding_next" : (state.servicesEnabled ? "onboarding_done" : "onboarding_connection_connect")
        return VStack(spacing: 10) {
            Button(action: last ? actions.connect : actions.next) {
                Text(verbatim: AppStrings.string(primary))
                    .font(.itmo(.titleMedium, emphasized: true))
                    .foregroundStyle(ItmoColor.onPrimary)
                    .frame(maxWidth: .infinity)
                    .opacity(state.servicesBusy ? 0 : 1)
                    .overlay { if state.servicesBusy { ProgressView().tint(ItmoColor.onPrimary) } }
            }
            .buttonStyle(.glassProminent)
            .controlSize(.extraLarge)
            .itmoTint()
            .disabled(state.finished || state.servicesBusy)
            .accessibilityIdentifier("onboarding.next")
            if last && !state.servicesEnabled {
                Button(action: actions.notNow) {
                    Text(verbatim: AppStrings.string("onboarding_connection_not_now")).frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                .controlSize(.extraLarge)
                .disabled(state.finished || state.servicesBusy)
                .accessibilityIdentifier("onboarding.notNow")
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 12)
    }
}

/// The step progress: one dot per step, the current one wide, read as `onboarding_step_progress`.
struct OnboardingStepDots: View {
    let count: Int
    let current: Int

    var body: some View {
        HStack(spacing: ItmoSpacing.compact) {
            ForEach(0..<count, id: \.self) { index in
                Capsule()
                    .fill(index <= current ? AnyShapeStyle(ItmoColor.primary) : AnyShapeStyle(Color(.systemFill)))
                    .frame(width: index == current ? 24 : 8, height: 8)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: AppStrings.string("onboarding_step_progress", [current + 1, count])))
        .accessibilityIdentifier("onboarding.steps")
    }
}

/// Step 1: the person, the lesson that matters now and what else is there, from their own data as it loads.
struct OnboardingValueStep: View {
    let state: OnboardingUiState

    var body: some View {
        if let me = state.me {
            HStack(spacing: ItmoSpacing.compact) {
                ItmoAvatar(initials: me.initials, pictureUrl: me.pictureUrl, size: 28)
                Text(verbatim: me.name).font(.itmo(.labelLarge, emphasized: true))
            }
            .padding(4)
            .padding(.trailing, 10)
            .background(Color(.secondarySystemGroupedBackground), in: .capsule)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("onboarding.me")
        }
        if let lesson = state.lesson {
            ItmoSectionHeader(title: lesson.title.resolved)
            ItmoLessonCard(lesson: lesson.lesson.kitLesson(id: "onboarding"))
                .accessibilityIdentifier("onboarding.lesson")
        }
        ItmoSectionHeader(title: AppStrings.string("onboarding_value_more"))
        ItmoListGroup {
            ItmoRow(title: AppStrings.string("onboarding_value_pass"), subtitle: AppStrings.string("onboarding_value_pass_text"), symbol: .qrCode, tone: .tertiary)
            ItmoRow(title: AppStrings.string("onboarding_value_scores"), subtitle: AppStrings.string("onboarding_value_scores_text"), symbol: .book2, tone: .primary)
            ItmoRow(
                title: AppStrings.string("onboarding_value_sport"),
                subtitle: state.sport?.resolved ?? AppStrings.string("onboarding_value_sport_text"),
                symbol: .sprint,
                tone: .ok
            )
            ItmoRow(title: AppStrings.string("onboarding_value_widgets"), subtitle: AppStrings.string("ios_onboarding_value_widgets_text"), symbol: .widgets, tone: .neutral)
        }
    }
}

/// Step 2: each permission with its own button, or `разрешено` once granted.
struct OnboardingPermissionsStep: View {
    let state: OnboardingUiState
    let actions: OnboardingActions

    var body: some View {
        ItmoListGroup {
            OnboardingPermissionRow(
                symbol: .notification,
                title: "onboarding_notifications_title",
                text: "onboarding_notifications_text",
                action: state.notificationsAsked ? "onboarding_notifications_open_settings" : "onboarding_notifications_allow",
                granted: state.notificationsGranted,
                run: actions.requestNotifications,
                id: "notifications"
            )
            if let synced = state.calendarSynced {
                OnboardingPermissionRow(
                    symbol: .calendarAdd,
                    title: "onboarding_calendar_title",
                    text: "onboarding_calendar_text",
                    action: "onboarding_calendar_add",
                    granted: synced.boolValue,
                    run: actions.addCalendar,
                    id: "calendar"
                )
            }
        }
        ItmoGroupNote(text: AppStrings.string("onboarding_permissions_widgets"))
    }
}

/// One permission, one explicit choice: its own button or the granted state.
struct OnboardingPermissionRow: View {
    let symbol: AppSymbol
    let title: String
    let text: String
    let action: String
    let granted: Bool
    let run: () -> Void
    let id: String

    var body: some View {
        HStack(alignment: .top, spacing: ItmoSpacing.content) {
            ItmoIconTile(symbol: symbol, tone: granted ? .ok : .primary)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: AppStrings.string(title)).font(.itmo(.titleMedium, emphasized: true))
                Text(verbatim: AppStrings.string(text)).font(.itmo(.bodyMedium)).foregroundStyle(.secondary)
                Group {
                    if granted {
                        ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("onboarding_permission_granted"), tone: .ok, symbol: .check))
                            .accessibilityIdentifier("onboarding.\(id).granted")
                    } else {
                        Button(AppStrings.string(action), action: run)
                            .buttonStyle(.glass)
                            .itmoTint()
                            .accessibilityIdentifier("onboarding.\(id).button")
                    }
                }
                .padding(.top, ItmoSpacing.compact)
            }
            Spacer(minLength: 0)
        }
        .padding(ItmoKit.padding)
    }
}

/// Step 3: what the connection gives, a way to read what the server keeps, and where to turn it on later.
struct OnboardingConnectionStep: View {
    let state: OnboardingUiState
    let actions: OnboardingActions

    var body: some View {
        ItmoListGroup {
            ItmoRow(title: AppStrings.string("onboarding_connection_friends"), subtitle: AppStrings.string("onboarding_connection_friends_text"), symbol: .group, tone: .primary)
            ItmoRow(title: AppStrings.string("onboarding_connection_queue"), subtitle: AppStrings.string("onboarding_connection_queue_text"), symbol: .sprint, tone: .primary)
            ItmoRow(title: AppStrings.string("onboarding_connection_links"), subtitle: AppStrings.string("onboarding_connection_links_text"), symbol: .link, tone: .primary)
        }
        ItmoListGroup {
            ItmoRow(title: AppStrings.string("onboarding_connection_details"), symbol: .info, chevron: true, action: actions.details)
        }
        .accessibilityIdentifier("onboarding.connection.details")
        if state.servicesEnabled {
            ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("onboarding_connection_connected"), tone: .ok, symbol: .check))
                .padding(.leading, ItmoSpacing.compact)
        } else {
            ItmoGroupNote(text: AppStrings.string("onboarding_connection_later_note"))
        }
    }
}
