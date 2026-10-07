import Shared
import SwiftUI

/// One step of the flow: a page that scrolls on its own, its title in `headlineSmall`, an optional subtitle, then the
/// step's groups.
struct OnboardingPage<Content: View>: View {
    let title: String
    var subtitle: String?
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: title)
                    .font(.itmo(.headlineSmall))
                    .accessibilityAddTraits(.isHeader)
                if let subtitle {
                    Text(verbatim: subtitle)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                        .padding(.top, ItmoSpacing.compact)
                }
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, ItmoSpacing.screenMargin)
            .padding(.top, ItmoSpacing.compact)
            .padding(.bottom, ItmoSpacing.group)
        }
    }
}

/// A filled group of rows (`Widget.ItmoWidgets.Card.Content`) with an optional title above and footer below.
struct OnboardingGroup<Content: View>: View {
    var title: String?
    var footer: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            if let title {
                Text(verbatim: title)
                    .font(.itmo(.titleSmall))
                    .foregroundStyle(ItmoColor.primary)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.horizontal, ItmoSpacing.cardPadding)
            }
            VStack(alignment: .leading, spacing: 0) {
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ItmoColor.surfaceContainerLow, in: RoundedRectangle(cornerRadius: ItmoShapes.cardContent))
            if let footer {
                Text(verbatim: footer)
                    .font(.itmo(.bodySmall))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, ItmoSpacing.cardPadding)
            }
        }
        .padding(.top, ItmoSpacing.group)
    }
}

/// A row of a group: the card's padding and a divider under every row but the last.
private struct OnboardingRow<Content: View>: View {
    var divided = false
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            content
                .padding(.horizontal, ItmoSpacing.cardPadding)
                .padding(.vertical, ItmoSpacing.content)
                .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget, alignment: .leading)
            if divided {
                Divider().padding(.leading, ItmoSpacing.cardPadding)
            }
        }
    }
}

// MARK: - Widget

/// One widget: how to add it on iOS, then the choices that shape it as the settings screen's own rows (toggles, then
/// `settings_widget_text_size_title` for a schedule widget). Rows wait for the stored appearance instead of showing
/// defaults. The QR widget keeps the standard spoiler image on iOS, so its image row is not shown.
struct OnboardingWidgetStep: View {
    let kind: WidgetKind
    let state: OnboardingUiState
    let actions: OnboardingActions

    var body: some View {
        OnboardingPage(title: AppStrings.string(kind.titleKey)) {
            OnboardingGroup {
                OnboardingRow {
                    HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
                        AppSymbol.widgets.image
                            .font(.itmo(.bodyLarge))
                            .foregroundStyle(ItmoColor.primary)
                            .accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                            Text(verbatim: AppStrings.string("ios_onboarding_widget_howto_title"))
                                .font(.itmo(.titleMedium))
                            Text(verbatim: AppStrings.string("ios_onboarding_widget_howto_text"))
                                .font(.itmo(.bodyMedium))
                                .foregroundStyle(.secondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("onboarding.widget.howto")
                }
            }
            OnboardingGroup {
                let options = kind.widgetOptions
                ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                    OnboardingRow(divided: index < options.count - 1 || kind != .qr) {
                        optionRow(option)
                    }
                }
                if kind != .qr {
                    OnboardingRow {
                        textSizeRow
                    }
                }
            }
        }
    }

    private func optionRow(_ option: WidgetOption) -> some View {
        let appearance = state.appearance
        return ItmoToggleRow(
            title: AppStrings.string(option.titleKey),
            subtitle: option.descriptionKey.map { AppStrings.string($0) },
            isOn: Binding(
                get: { appearance.map(option.isEnabled(in:)) ?? false },
                set: { actions.setOption(option, $0) }
            )
        )
        .disabled(appearance == nil)
        .accessibilityIdentifier("onboarding.option.\(option.name.lowercased())")
    }

    /// `settings_widget_text_size_title` with the size as a menu at its end.
    private var textSizeRow: some View {
        let size = state.appearance.flatMap(kind.textSize(in:))
        let title = AppStrings.string("settings_widget_text_size_title")
        return HStack(spacing: ItmoSpacing.content) {
            ItmoRowLabel(title: title)
            Spacer(minLength: 0)
            Picker(
                selection: Binding(
                    get: { size ?? .normal },
                    set: { actions.setTextSize(kind, $0) }
                )
            ) {
                ForEach(WidgetTextSize.allCases, id: \.self) { size in
                    Text(verbatim: AppStrings.string(size.labelKey)).tag(size)
                }
            } label: {
                Text(verbatim: title)
            }
            .pickerStyle(.menu)
            .labelsHidden()
            .itmoTint()
            .opacity(size == nil ? 0 : 1)
            .disabled(size == nil)
            .accessibilityIdentifier("onboarding.textSize")
        }
    }
}

extension WidgetKind {
    var titleKey: String {
        switch self {
        case .singleLesson: "onboarding_compact_widget_title"
        case .daySchedule: "onboarding_full_widget_title"
        case .qr: "onboarding_qr_widget_title"
        }
    }

    /// The shared `WidgetKind.options`, in row order.
    var widgetOptions: [WidgetOption] {
        switch self {
        case .singleLesson: [.compactNextLessonEarly, .compactHideTeacher]
        case .daySchedule: [.fullHideTeacher, .fullHidePastLessons, .fullShowTomorrow]
        case .qr: [.qrDynamicColors, .qrSpoiler]
        }
    }

    /// The shared `WidgetKind.textSize`: a schedule widget's text size, none for QR.
    func textSize(in appearance: WidgetAppearance) -> WidgetTextSize? {
        switch self {
        case .singleLesson: appearance.schedule.compact.textSize
        case .daySchedule: appearance.schedule.full.textSize
        case .qr: nil
        }
    }
}

extension WidgetOption {
    /// The shared `WidgetOption.isEnabled`.
    func isEnabled(in appearance: WidgetAppearance) -> Bool {
        switch self {
        case .compactNextLessonEarly: appearance.schedule.compact.showNextLessonEarly
        case .compactHideTeacher: appearance.schedule.compact.hideTeacher
        case .fullHideTeacher: appearance.schedule.full.hideTeacher
        case .fullHidePastLessons: appearance.schedule.full.hidePastLessons
        case .fullShowTomorrow: appearance.schedule.full.showTomorrowWhenTodayIsOver
        case .qrDynamicColors: appearance.qr.dynamicColors
        case .qrSpoiler: appearance.qr.spoilerEnabled
        }
    }

    var titleKey: String {
        switch self {
        case .compactNextLessonEarly: "settings_widget_next_early_title"
        case .compactHideTeacher, .fullHideTeacher: "settings_widget_hide_teacher_title"
        case .fullHidePastLessons: "settings_widget_hide_past_title"
        case .fullShowTomorrow: "settings_widget_tomorrow_title"
        case .qrDynamicColors: "settings_qr_dynamic_colors_title"
        case .qrSpoiler: "settings_qr_spoiler_title"
        }
    }

    var descriptionKey: String? {
        switch self {
        case .compactNextLessonEarly: "settings_widget_next_early_description"
        case .fullShowTomorrow: "settings_widget_tomorrow_description"
        case .qrSpoiler: "settings_qr_spoiler_description"
        default: nil
        }
    }
}

extension WidgetTextSize {
    var labelKey: String {
        switch self {
        case .normal: "settings_widget_text_size_normal"
        case .large: "settings_widget_text_size_large"
        case .extraLarge: "settings_widget_text_size_extra_large"
        }
    }
}

// MARK: - Services

/// The Backend opt-in as one switch row with the privacy default under it, what it gives and what the server stores,
/// named in full, with the `onboarding_services_source_link` link.
struct OnboardingServicesStep: View {
    let state: OnboardingUiState
    let actions: OnboardingActions

    /// The fields the Backend stores about a user, as Android's `StoredFields`.
    private static let storedKeys = [
        "onboarding_services_stored_identity",
        "onboarding_services_stored_group",
        "onboarding_services_stored_schedule",
        "onboarding_services_stored_sport",
        "onboarding_services_stored_device",
    ]

    var body: some View {
        OnboardingPage(
            title: AppStrings.string("onboarding_services_title"),
            subtitle: AppStrings.string("onboarding_services_subtitle")
        ) {
            OnboardingGroup(footer: AppStrings.string("onboarding_services_privacy_default")) {
                OnboardingRow {
                    servicesRow
                }
            }
            OnboardingGroup(title: AppStrings.string("onboarding_services_gives_title")) {
                OnboardingRow {
                    VStack(alignment: .leading, spacing: ItmoSpacing.content) {
                        AuthFeatureRow(symbol: .group, title: AppStrings.string("onboarding_services_feature_friends"))
                        AuthFeatureRow(symbol: .exercise, title: AppStrings.string("onboarding_services_feature_sport"))
                        AuthFeatureRow(
                            symbol: .notification,
                            title: AppStrings.string("onboarding_services_feature_notifications")
                        )
                    }
                }
            }
            OnboardingGroup(title: AppStrings.string("onboarding_services_stored_title")) {
                OnboardingRow {
                    storedData
                }
            }
        }
    }

    /// `onboarding_services_toggle_title` over the whole row; while the opt-in runs a spinner takes the switch's place
    /// and the row ignores taps, so it never changes height.
    private var servicesRow: some View {
        let title = AppStrings.string("onboarding_services_toggle_title")
        let isOn = Binding(get: { state.servicesEnabled }, set: { actions.setServicesEnabled($0) })
        return HStack(spacing: ItmoSpacing.content) {
            ItmoRowLabel(title: title, subtitle: AppStrings.string("onboarding_services_toggle_description"))
            Spacer(minLength: 0)
            ZStack {
                Toggle(isOn: isOn) {
                    Text(verbatim: title)
                }
                .labelsHidden()
                .itmoTint()
                .opacity(state.servicesBusy ? 0 : 1)
                .accessibilityIdentifier("onboarding.services.toggle")
                if state.servicesBusy {
                    ProgressView()
                        .itmoTint()
                        .accessibilityIdentifier("onboarding.services.progress")
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            if !state.servicesBusy { isOn.wrappedValue.toggle() }
        }
        .disabled(state.servicesBusy)
    }

    private var storedData: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.compact) {
            ForEach(Self.storedKeys, id: \.self) { key in
                Text(verbatim: AppStrings.string(key))
                    .font(.itmo(.bodyMedium))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text(verbatim: AppStrings.string("onboarding_services_stored_footer"))
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, ItmoSpacing.related)
            Button {
                actions.openLink(ProjectLinks.shared.SERVICES_SOURCE_URL)
            } label: {
                Label {
                    Text(verbatim: AppStrings.string("onboarding_services_source_link"))
                        .font(.itmo(.labelLarge))
                } icon: {
                    AppSymbol.brandGithub.image.accessibilityHidden(true)
                }
                .frame(minHeight: ItmoMetrics.touchTarget)
            }
            .buttonStyle(.bordered)
            .itmoTint()
            .accessibilityIdentifier("onboarding.services.source")
        }
    }
}

// MARK: - Notifications

/// The notification permission, asked right after the opt-in that sends the pushes: one status row and one button that
/// keeps its place. `onboarding_notifications_allow` while iOS can still show its dialog,
/// `onboarding_notifications_open_settings` after it was asked, `ios_onboarding_notifications_configure` once allowed;
/// the last two open the app's notification settings.
struct OnboardingNotificationsStep: View {
    let state: OnboardingUiState
    let actions: OnboardingActions

    var body: some View {
        OnboardingPage(
            title: AppStrings.string("onboarding_notifications_title"),
            subtitle: AppStrings.string("onboarding_notifications_subtitle")
        ) {
            OnboardingGroup {
                OnboardingRow {
                    VStack(alignment: .leading, spacing: ItmoSpacing.group) {
                        HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.group) {
                            AppSymbol.notification.image
                                .font(.itmo(.titleMedium))
                                .foregroundStyle(.secondary)
                                .accessibilityHidden(true)
                            Text(verbatim: AppStrings.string(statusKey))
                                .font(.itmo(.titleMedium))
                                .foregroundStyle(state.notificationsGranted ? ItmoColor.primary : .primary)
                                .accessibilityIdentifier("onboarding.notifications.status")
                        }
                        Button(action: actions.requestNotifications) {
                            Text(verbatim: AppStrings.string(buttonKey))
                                .font(.itmo(.labelLarge))
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity, minHeight: ItmoMetrics.touchTarget)
                        }
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.roundedRectangle(radius: ItmoCorner.large))
                        .itmoTint()
                        .accessibilityIdentifier("onboarding.notifications.button")
                    }
                }
            }
        }
    }

    private var statusKey: String {
        state.notificationsGranted ? "onboarding_notifications_on" : "onboarding_notifications_off"
    }

    private var buttonKey: String {
        if state.notificationsGranted { return "ios_onboarding_notifications_configure" }
        return state.notificationsAsked ? "onboarding_notifications_open_settings" : "onboarding_notifications_allow"
    }
}
