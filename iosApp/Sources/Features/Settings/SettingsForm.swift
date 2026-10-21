import Shared
import SwiftUI
import UIKit

/// One settings page as the shared page model describes it (`SettingsUiState`), on the kit as the notifications and
/// connection pages: a `SettingSectionView` per section, an untitled group after another a little apart. Stateless, so
/// snapshot tests render it from fixture states; `SettingsScreen` owns the ViewModel and passes its state and actions
/// in.
struct SettingsForm: View {
    let state: SettingsUiState
    /// What iOS says about itself where the shared copy names Android (`SettingsIosCopy`).
    var system = SettingsSystemState()
    /// A switch the user just flipped, shown as flipped until the ViewModel's next state.
    var pendingToggles: [SettingRowId: Bool] = [:]
    var actions = SettingsFormActions()

    var body: some View {
        if state.loaded {
            ItmoScreenStack {
                ForEach(Array(state.sections.enumerated()), id: \.offset) { index, section in
                    SettingSectionView(
                        section: section, copy: SettingsIosCopy(system: system), pendingToggles: pendingToggles,
                        actions: actions
                    )
                    .padding(.top, index > 0 && section.title == nil ? ItmoSpacing.compact : 0)
                }
            }
        } else {
            // No made-up defaults while the stored values load (`SettingsUiState.loaded`).
            ItmoLoadingView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

/// One `SettingSection` on the kit, as every settings page and the widgets gallery draw it: the section header, a lone
/// choice of two to four options as a segmented control, any other rows in one `ItmoListGroup` with their icons in
/// neutral tiles (as on the profile), the footer as an `ItmoGroupNote`.
struct SettingSectionView: View {
    let section: SettingSection
    var copy = SettingsIosCopy(system: SettingsSystemState())
    var pendingToggles: [SettingRowId: Bool] = [:]
    var actions = SettingsFormActions()

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoKit.gap) {
            if let title = section.title {
                ItmoSectionHeader(title: copy.text(title))
            }
            if let choice = segmented {
                ItmoSegmentedChoice(
                    label: copy.text(choice.title),
                    options: choice.options.map(\.key),
                    selection: Binding(get: { choice.selectedOptionKey ?? "" }, set: { actions.choose(choice.id, $0) }),
                    enabled: choice.enabled
                ) { key in choice.options.first { $0.key == key }.map { copy.text($0.label) } ?? key }
                .accessibilityIdentifier("settings.row.\(choice.id.key)")
            } else {
                ItmoListGroup {
                    ForEach(section.items.map(SettingsRow.init)) { row in
                        self.row(row.item)
                            .accessibilityIdentifier("settings.row.\(row.id)")
                    }
                }
            }
            if let footer = section.footer {
                ItmoGroupNote(text: copy.text(footer))
            }
        }
    }

    /// The section's only row when it is a choice of two to four options.
    private var segmented: SettingItemChoice? {
        guard section.items.count == 1, let choice = section.items.first as? SettingItemChoice,
              (2...4).contains(choice.options.count) else { return nil }
        return choice
    }

    @ViewBuilder
    private func row(_ item: any SettingItem) -> some View {
        let symbol = item.icon?.symbol
        let tone: ItmoTone? = symbol == nil ? nil : .neutral
        switch onEnum(of: item) {
        case let .toggle(toggle):
            ItmoSwitchRow(
                title: copy.text(toggle.title),
                subtitle: toggle.description_.map(copy.text),
                symbol: symbol,
                tone: tone,
                enabled: toggle.enabled,
                isOn: Binding(get: { pendingToggles[toggle.id] ?? toggle.checked }, set: { actions.toggle(toggle.id, $0) })
            )
        case let .choice(choice):
            // More options than a segmented control holds: a menu over them, the row laid out as every other.
            Menu {
                Picker(selection: Binding(get: { choice.selectedOptionKey ?? "" }, set: { actions.choose(choice.id, $0) })) {
                    ForEach(choice.options, id: \.key) { option in
                        Text(verbatim: copy.text(option.label)).tag(option.key)
                    }
                } label: {
                    Text(verbatim: copy.text(choice.title))
                }
                .pickerStyle(.inline)
            } label: {
                ItmoRow(
                    title: copy.text(choice.title),
                    subtitle: choice.description_.map(copy.text),
                    symbol: symbol,
                    tone: tone,
                    trailing: copy.text(choice.value)
                )
            }
            .tint(.primary)
            .disabled(!choice.enabled || choice.selectedOptionKey == nil)
        case let .navigation(navigation):
            ItmoRow(
                title: copy.text(navigation.title),
                subtitle: (navigation.description_ ?? navigation.value).map(copy.text),
                symbol: symbol,
                tone: tone,
                trailing: navigation.description_ == nil ? nil : navigation.value.map(copy.text),
                chevron: true,
                action: { actions.open(navigation.page) }
            )
            .disabled(!navigation.enabled)
        case let .action(action):
            ItmoRow(
                title: copy.text(action.title),
                subtitle: (action.description_ ?? action.value).map(copy.text),
                symbol: symbol,
                tone: tone,
                trailing: action.description_ == nil ? nil : action.value.map(copy.text),
                chevron: action.trailingIcon == .chevronRight,
                action: { actions.act(action.id) }
            )
            .accessory {
                if let trailing = action.trailingIcon, trailing != .chevronRight {
                    trailing.symbol.image
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                }
            }
            .disabled(!action.enabled)
        case let .info(info):
            ItmoRow(title: copy.text(info.title), subtitle: copy.text(info.value), symbol: symbol, tone: tone)
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
    var open: (SettingsPage) -> Void = { _ in }
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
        guard let resource = text as? UiTextRes, let key = replacement(for: resource.key) else {
            return text.resolved
        }
        return AppStrings.string(key)
    }

    private func replacement(for key: String) -> String? {
        switch key {
        // The background work row is Background App Refresh on iOS: it opens the app's page in Settings.
        case "settings_background_work_title": "ios_background_refresh_title"
        case "background_work_hint":
            system.backgroundRefresh == .restricted ? "ios_background_refresh_restricted" : "ios_background_refresh_off"
        default: nil
        }
    }
}
