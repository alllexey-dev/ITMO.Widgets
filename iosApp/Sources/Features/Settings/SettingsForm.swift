import Shared
import SwiftUI
import UIKit

/// One settings page as the shared page model describes it (`SettingsUiState`, L14 LT-3b): one SwiftUI row per
/// `SettingItem` subtype in the sections the page provider built. Stateless, so snapshot tests render it from fixture
/// states; `SettingsScreen` owns the ViewModel and passes its state and actions in.
struct SettingsForm: View {
    let state: SettingsUiState
    /// What iOS says about itself where the shared copy names Android (`SettingsIosCopy`).
    var system = SettingsSystemState()
    /// A switch the user just flipped, shown as flipped until the ViewModel's next state.
    var pendingToggles: [SettingRowId: Bool] = [:]
    var actions = SettingsFormActions()

    var body: some View {
        if state.loaded {
            Form {
                ForEach(Array(state.sections.enumerated()), id: \.offset) { _, section in
                    ItmoFormSection(header: section.title?.resolved, footer: section.footer?.resolved) {
                        ForEach(section.items.map(SettingsRow.init)) { row in
                            self.row(row.item)
                                .accessibilityIdentifier("settings.row.\(row.id)")
                        }
                    }
                }
            }
            .itmoTint()
        } else {
            // No made-up defaults while the stored values load (`SettingsUiState.loaded`).
            ItmoLoadingView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder
    private func row(_ item: any SettingItem) -> some View {
        let copy = SettingsIosCopy(system: system)
        switch onEnum(of: item) {
        case let .toggle(toggle):
            ItmoToggleRow(
                title: copy.text(toggle.title),
                subtitle: toggle.description_.map(copy.text),
                isOn: Binding(
                    get: { pendingToggles[toggle.id] ?? toggle.checked },
                    set: { actions.toggle(toggle.id, $0) }
                )
            )
            .disabled(!toggle.enabled || !toggle.stateKnown)
        case let .choice(choice):
            // A menu over the options, with the row laid out as every other value row.
            Menu {
                Picker(
                    selection: Binding(
                        get: { choice.selectedOptionKey ?? "" },
                        set: { actions.choose(choice.id, $0) }
                    )
                ) {
                    ForEach(choice.options, id: \.key) { option in
                        Text(verbatim: copy.text(option.label)).tag(option.key)
                    }
                } label: {
                    Text(verbatim: copy.text(choice.title))
                }
                .pickerStyle(.inline)
            } label: {
                SettingsValueLabel(
                    title: copy.text(choice.title),
                    subtitle: choice.description_.map(copy.text),
                    value: copy.text(choice.value),
                    trailing: .expandMore
                )
            }
            .tint(.primary)
            .disabled(!choice.enabled || choice.selectedOptionKey == nil)
        case let .navigation(navigation):
            NavigationLink(value: ShellDestination(AppRoutes.Settings(page: navigation.page.name))) {
                SettingsValueLabel(
                    title: copy.text(navigation.title),
                    subtitle: navigation.description_.map(copy.text),
                    value: navigation.value.map(copy.text)
                )
            }
            .disabled(!navigation.enabled)
        case let .action(action):
            Button {
                actions.act(action.id)
            } label: {
                SettingsValueLabel(
                    title: copy.text(action.title),
                    subtitle: action.description_.map(copy.text),
                    value: action.value.map(copy.text),
                    trailing: action.trailingIcon?.symbol
                )
            }
            // A row that opens or runs something reads as a row, as on Android, not as a tinted command.
            .tint(.primary)
            .disabled(!action.enabled)
        case let .info(info):
            SettingsValueLabel(title: copy.text(info.title), value: copy.text(info.value))
                .textSelection(.enabled)
        }
    }
}

/// A row keyed by its stable `SettingRowId.key`, which never changes when an entry is renamed.
private struct SettingsRow: Identifiable {
    let item: any SettingItem

    var id: String { item.id.key }
}

/// What the rows of `SettingsForm` do; each closure reaches the page's ViewModel.
struct SettingsFormActions {
    var toggle: (SettingRowId, Bool) -> Void = { _, _ in }
    var choose: (SettingRowId, String) -> Void = { _, _ in }
    var act: (SettingRowId) -> Void = { _ in }
}

/// A row label with a value and an optional trailing symbol, in the colors of a plain row. The value sits at the
/// trailing edge while title and value fit side by side on one line each, else under the title, so neither is
/// squeezed into a narrow column at large text sizes.
struct SettingsValueLabel: View {
    let title: String
    var subtitle: String?
    var value: String?
    var trailing: AppSymbol?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
            if let value {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline, spacing: ItmoSpacing.content) {
                        ItmoRowLabel(title: title, subtitle: subtitle)
                        Spacer(minLength: 0)
                        valueText(value)
                            .multilineTextAlignment(.trailing)
                    }
                    VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                        ItmoRowLabel(title: title, subtitle: subtitle)
                        valueText(value)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                ItmoRowLabel(title: title, subtitle: subtitle)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if let trailing {
                trailing.image
                    .font(.itmo(.bodyMedium))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
        }
        // A menu centers its label's lines; a row reads from the leading edge.
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }

    private func valueText(_ value: String) -> some View {
        Text(verbatim: value)
            .font(.itmo(.bodyLarge))
            .foregroundStyle(.secondary)
    }
}

/// The system facts the iOS copy depends on, read by `SettingsScreen` on appear and on every return to the app.
struct SettingsSystemState: Equatable {
    var backgroundRefresh: UIBackgroundRefreshStatus = .available
}

/// The shared rows in iOS words: where a shared text names Android or its settings, iOS shows its own catalog text
/// (`iosApp/Strings/strings_ios*.xml`); every other text renders as the provider built it.
struct SettingsIosCopy {
    let system: SettingsSystemState

    func text(_ text: UiText) -> String {
        guard let resource = text as? UiTextRes, let key = replacement(for: resource.resource.key) else {
            return text.resolved
        }
        return AppStrings.string(key)
    }

    private func replacement(for key: String) -> String? {
        switch key {
        case "settings_notifications_allowed": "ios_settings_notifications_allowed"
        case "settings_notifications_blocked": "ios_settings_notifications_blocked"
        // The background work row is Background App Refresh on iOS: it opens the app's page in Settings.
        case "settings_background_work_title": "ios_background_refresh_title"
        case "background_work_hint":
            system.backgroundRefresh == .restricted ? "ios_background_refresh_restricted" : "ios_background_refresh_off"
        default: nil
        }
    }
}
