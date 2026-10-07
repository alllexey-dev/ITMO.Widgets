import Shared
import SwiftUI
import UIKit
import UserNotifications

/// The first-run flow over the shared `OnboardingViewModel` (IO-07b, `docs/features/onboarding.md`): Android's steps
/// in SwiftUI. A widget step explains how to add the widget (iOS lets no app place one) beside its appearance rows;
/// the services step is the shared opt-in; the notifications step asks iOS (`requestAuthorization`). Every choice is
/// stored as it is made; `finished` runs once the flow ends, after the stored flag is written.
struct OnboardingScreen: View {
    let finished: () -> Void

    @State private var model = Self.makeModel()
    @State private var errorText: String?
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        OnboardingContent(state: model.state, actions: actions)
            .observing(model)
            .onEvents(of: model, \.events, perform: handle)
            .task {
                // iOS has no programmatic pin: every widget step shows the how-to instead of the pin button.
                model.viewModel.onPinSupportChanged(supported: false)
                await readNotificationPermission()
            }
            .onChange(of: scenePhase) { _, phase in
                // Back from the notification settings: the status row follows what the user chose there.
                if phase == .active { Task { await readNotificationPermission() } }
            }
            .onChange(of: model.state.finished, initial: true) { _, done in
                if done { finished() }
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
            skip: viewModel.skip,
            setOption: { option, enabled in viewModel.setOption(option: option, enabled: enabled) },
            setTextSize: { kind, size in viewModel.setTextSize(kind: kind, size: size) },
            setServicesEnabled: { viewModel.setServicesEnabled(enabled: $0) },
            requestNotifications: viewModel.requestNotifications,
            openLink: { url in if let url = URL(string: url) { openURL(url) } }
        )
    }

    private func handle(_ event: OnboardingEvent) {
        switch onEnum(of: event) {
        case .requestNotificationPermission:
            Task { await requestNotificationPermission() }
        case .openNotificationSettings:
            if let url = URL(string: UIApplication.openNotificationSettingsURLString) { openURL(url) }
        case let .showError(event):
            errorText = AppErrorTextsKt.toUiText(event.error).resolved
        case .requestPinWidget, .spoilerImageFailed:
            // Neither is sent on iOS: no programmatic pin, no custom spoiler image.
            break
        }
    }

    /// The system dialog; its answer is the status row's, and IO-13a's `alertsAllowed`.
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

/// The callbacks of the flow, one per `OnboardingViewModel` entry point iOS uses, plus opening a link.
struct OnboardingActions {
    var next: () -> Void = {}
    var back: () -> Void = {}
    var skip: () -> Void = {}
    var setOption: (WidgetOption, Bool) -> Void = { _, _ in }
    var setTextSize: (WidgetKind, WidgetTextSize) -> Void = { _, _ in }
    var setServicesEnabled: (Bool) -> Void = { _ in }
    var requestNotifications: () -> Void = {}
    var openLink: (String) -> Void = { _ in }
}

/// The flow for one `OnboardingUiState`, without the ViewModel (snapshot tests render it directly): the step dots with
/// the back button, the current step, and the footer with `onboarding_skip` and `onboarding_next` or `onboarding_done`.
struct OnboardingContent: View {
    let state: OnboardingUiState
    let actions: OnboardingActions

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 0) {
            header
            step
                .frame(maxHeight: .infinity)
            footer
        }
        .background(Color(uiColor: .systemBackground))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("onboarding.screen")
    }

    private var stepIndex: Int { Int(state.stepIndex) }

    private var header: some View {
        ZStack {
            OnboardingStepDots(count: state.steps.count, current: stepIndex)
            HStack {
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
                Spacer(minLength: 0)
            }
            .padding(.horizontal, ItmoSpacing.compact)
        }
        .padding(.top, ItmoSpacing.compact)
    }

    @ViewBuilder
    private var step: some View {
        Group {
            switch state.step {
            case .compactWidget:
                OnboardingWidgetStep(kind: .singleLesson, state: state, actions: actions)
            case .fullWidget:
                OnboardingWidgetStep(kind: .daySchedule, state: state, actions: actions)
            case .qrWidget:
                OnboardingWidgetStep(kind: .qr, state: state, actions: actions)
            case .services:
                OnboardingServicesStep(state: state, actions: actions)
            case .notifications:
                OnboardingNotificationsStep(state: state, actions: actions)
            }
        }
        .id(state.step)
        .transition(.opacity)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: state.step)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("onboarding.step.\(state.step.name.lowercased())")
    }

    /// `onboarding_skip` until the last step, then only `onboarding_done`.
    private var footer: some View {
        HStack(spacing: ItmoSpacing.group) {
            if !state.isLastStep {
                Button(action: actions.skip) {
                    Text(verbatim: AppStrings.string("onboarding_skip"))
                        .font(.itmo(.labelLarge))
                        .frame(minHeight: ItmoMetrics.touchTarget)
                }
                .itmoTint()
                .disabled(state.finished)
                .accessibilityIdentifier("onboarding.skip")
            }
            Spacer(minLength: 0)
            Button(action: actions.next) {
                Text(verbatim: AppStrings.string(state.isLastStep ? "onboarding_done" : "onboarding_next"))
                    .font(.itmo(.labelLarge))
                    .foregroundStyle(ItmoColor.onPrimary)
                    .padding(.vertical, ItmoSpacing.compact)
                    .frame(minWidth: 96, minHeight: ItmoMetrics.touchTarget)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: ItmoCorner.large))
            .itmoTint()
            .disabled(state.finished)
            .accessibilityIdentifier("onboarding.next")
        }
        .padding(.horizontal, ItmoSpacing.screenMargin)
        .padding(.top, ItmoSpacing.compact)
        .padding(.bottom, ItmoSpacing.group)
    }
}

/// The step progress: one dot per step, the current one in the primary role, read as `onboarding_step_progress`.
struct OnboardingStepDots: View {
    let count: Int
    let current: Int

    var body: some View {
        HStack(spacing: ItmoSpacing.compact) {
            ForEach(0..<count, id: \.self) { index in
                Capsule()
                    .fill(index == current ? ItmoColor.primary : ItmoColor.outlineVariant)
                    .frame(width: index == current ? 24 : 8, height: 8)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            Text(verbatim: AppStrings.string("onboarding_step_progress", [current + 1, count]))
        )
        .accessibilityIdentifier("onboarding.steps")
    }
}
