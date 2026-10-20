import Shared
import SwiftUI
import UIKit

/// The appearance page (`settings_appearance_title`, `AppearanceViewModel`), as Android's `AppearanceScreen`: every
/// choice on the page and applied at once. The app's own screens keep the system's colours on iOS, so the sample shows
/// a widget, the one thing the colours reach (`PlatformCapabilities.appColors`), and the page has no black background.
struct AppearanceScreen: View {
    @State private var model = ObservableViewModel(AppearanceViewModel.self, state: \.uiState)
    @State private var message: SettingsMessage?

    var body: some View {
        let viewModel = model.viewModel
        AppearanceContent(
            state: model.state,
            actions: AppearanceActions(
                accent: { viewModel.selectAccent(accent: $0) },
                customColor: { viewModel.setCustomColor(argb: $0) },
                style: { viewModel.selectStyle(style: $0) },
                contrast: { viewModel.selectContrast(contrast: $0) },
                blackBackground: { viewModel.setBlackBackground(enabled: $0) }
            )
        )
        .itmoDetailHeader(title: AppStrings.string("settings_appearance_title"), subtitle: nil)
        .observing(model)
        .onEvents(of: model, \.events) { event in
            switch onEnum(of: event) {
            case let .showError(failure):
                let text = AppErrorTextsKt.toUiText(failure.error).resolved
                message = SettingsMessage(text: text)
                AccessibilityNotification.Announcement(text).post()
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
        .accessibilityIdentifier("appearance.screen")
    }
}

/// What the page's choices do; snapshot tests pass no-ops.
struct AppearanceActions {
    var accent: (AccentColor) -> Void = { _ in }
    var customColor: (Int32) -> Void = { _ in }
    var style: (ThemeStyle) -> Void = { _ in }
    var contrast: (ThemeContrast) -> Void = { _ in }
    var blackBackground: (Bool) -> Void = { _ in }
}

/// The appearance page for one `AppearanceUiState`, without the ViewModel.
struct AppearanceContent: View {
    let state: AppearanceUiState
    var actions = AppearanceActions()

    @Environment(\.colorScheme) private var colorScheme

    private var dark: Bool { colorScheme == .dark }

    var body: some View {
        ItmoScreenStack {
            if state.loaded {
                sample
                ItmoSectionHeader(title: AppStrings.string("settings_appearance_color"))
                ItmoListGroup(inset: ItmoKit.padding) {
                    swatches
                    if let argb = state.customArgb?.int32Value {
                        ColorPicker(selection: customColor(argb), supportsOpacity: false) {
                            Text(verbatim: AppStrings.string("settings_accent_color_custom")).font(.itmo(.bodyLarge))
                        }
                        .padding(.horizontal, ItmoKit.padding)
                        .padding(.vertical, 11)
                        .accessibilityIdentifier("appearance.picker")
                    }
                }
                if let note = state.accentNote?.resolved { ItmoGroupNote(text: note) }

                ItmoSectionHeader(title: AppStrings.string("settings_appearance_palette"))
                palettes
                if let style = state.style { ItmoGroupNote(text: style.description_.resolved) }

                ItmoSectionHeader(title: AppStrings.string("settings_theme_contrast_title"))
                ItmoSegmentedChoice(
                    label: AppStrings.string("settings_theme_contrast_title"),
                    options: state.contrasts.map(\.contrast),
                    selection: Binding(get: { state.theme.contrast }, set: actions.contrast)
                ) { contrast in state.contrasts.first { $0.contrast == contrast }?.label.resolved ?? contrast.name }
                .accessibilityIdentifier("appearance.contrast")
                ItmoGroupNote(text: AppStrings.string("settings_appearance_contrast_note"))

                if let black = state.blackBackground?.boolValue {
                    ItmoListGroup {
                        ItmoSwitchRow(
                            title: AppStrings.string("settings_dark_black_title"),
                            subtitle: AppStrings.string("settings_dark_black_description"),
                            isOn: Binding(get: { black }, set: actions.blackBackground)
                        )
                    }
                    .padding(.top, ItmoSpacing.compact)
                    .accessibilityIdentifier("appearance.black")
                }
                if let footer = state.footer?.resolved {
                    ItmoGroupNote(text: footer).padding(.top, ItmoSpacing.compact)
                }
            }
        }
    }

    /// The scheme in the light and the dark appearance on a small widget: a title, a card with a tile and a bar, a pill.
    private var sample: some View {
        HStack(spacing: ItmoSpacing.compact) {
            AppearanceSampleWidget(theme: state.theme, dark: false, label: AppStrings.string("settings_appearance_light"))
            AppearanceSampleWidget(theme: state.theme, dark: true, label: AppStrings.string("settings_appearance_dark"))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: AppStrings.string("settings_appearance_sample")))
        .accessibilityIdentifier("appearance.sample")
    }

    /// Every colour as a circle of the primary it gives, named under it, three to a row. Under the monochrome palette they stay in
    /// their hues, dimmed and inert, since the palette ignores them.
    private var swatches: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: ItmoSpacing.related), count: 3),
                  spacing: ItmoSpacing.compact) {
            ForEach(state.accents, id: \.accent) { option in
                let selected = option.accent == state.accent
                Button { actions.accent(option.accent) } label: {
                    VStack(spacing: ItmoSpacing.related) {
                        AppearanceSwatch(
                            rgb: AppearanceColors.shared.swatch(theme: state.theme.with(accent: option.accent), dark: dark),
                            selected: selected
                        )
                        Text(verbatim: option.label.resolved)
                            .font(.itmo(.labelMedium))
                            .foregroundStyle(selected ? .primary : .secondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
                .accessibilityIdentifier("appearance.accent.\(option.accent.name.lowercased())")
            }
        }
        .padding(ItmoKit.padding)
        .disabled(!state.accentApplies)
        .opacity(state.accentApplies ? 1 : 0.38)
    }

    /// Every palette as three dots of what it makes of the picked colour, in a card with its name; the row scrolls
    /// sideways and opens on the picked one.
    private var palettes: some View {
        ScrollViewReader { reader in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: ItmoSpacing.compact) {
                    ForEach(state.styles, id: \.style) { option in
                        palette(option).id(option.style)
                    }
                }
            }
            .onAppear { reader.scrollTo(state.theme.style, anchor: .center) }
        }
    }

    private func palette(_ option: StyleOption) -> some View {
        let selected = option.style == state.theme.style
        let colors = AppearanceColors.shared.palette(theme: state.theme, style: option.style, dark: dark)
        return Button { actions.style(option.style) } label: {
            VStack(spacing: ItmoSpacing.compact) {
                HStack(spacing: -10) {
                    ForEach(Array(colors.enumerated()), id: \.offset) { _, rgb in
                        Circle()
                            .fill(Color(rgb: rgb.int32Value))
                            .frame(width: 28, height: 28)
                            .overlay(Circle().stroke(Color(.secondarySystemGroupedBackground), lineWidth: 2))
                    }
                }
                Text(verbatim: option.label.resolved).font(.itmo(.labelLarge)).lineLimit(1).fixedSize()
            }
            .padding(ItmoSpacing.content)
            .frame(minWidth: 104)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: ItmoKit.cardCorner))
            .overlay {
                if selected {
                    RoundedRectangle(cornerRadius: ItmoKit.cardCorner).stroke(ItmoColor.primary, lineWidth: 2)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("appearance.style.\(option.style.name.lowercased())")
    }

    /// The own colour as the system colour well; a pick is the opaque `0xAARRGGBB` the ViewModel takes.
    private func customColor(_ argb: Int32) -> Binding<Color> {
        Binding(
            get: { Color(rgb: argb) },
            set: { color in
                var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
                UIColor(color).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
                func channel(_ value: CGFloat) -> UInt32 { UInt32((min(max(value, 0), 1) * 255).rounded()) }
                let rgb = 0xFF00_0000 | channel(red) << 16 | channel(green) << 8 | channel(blue)
                actions.customColor(Int32(bitPattern: rgb))
            }
        )
    }
}

/// A colour circle; the picked one is ringed in the primary text colour and checked in black or white, whichever
/// stands out on it.
private struct AppearanceSwatch: View {
    let rgb: Int32
    let selected: Bool

    private var light: Bool {
        let value = UInt32(bitPattern: rgb)
        let channels = [value >> 16, value >> 8, value].map { Double($0 & 0xFF) / 255 }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2] > 0.5
    }

    var body: some View {
        Circle()
            .fill(Color(rgb: rgb))
            .overlay(Circle().stroke(Color(.separator), lineWidth: 1))
            .overlay {
                if selected {
                    AppSymbol.check.image.font(.itmo(.bodyLarge, emphasized: true)).foregroundStyle(light ? .black : .white)
                }
            }
            .padding(selected ? 4 : 0)
            .overlay { if selected { Circle().stroke(Color.primary, lineWidth: 2) } }
            .frame(width: 48, height: 48)
    }
}

/// A small widget in [theme]'s light or dark scheme, as the widgets that follow the theme draw it.
private struct AppearanceSampleWidget: View {
    let theme: ThemeSpec
    let dark: Bool
    let label: String

    var body: some View {
        let roles = AppearanceColors.shared.roles(theme: theme, dark: dark)
        VStack(spacing: ItmoSpacing.related) {
            VStack(alignment: .leading, spacing: 8) {
                Capsule().fill(Color(rgb: roles.onSurface)).frame(width: 64, height: 10)
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 7)
                        .fill(Color(rgb: roles.primaryContainer))
                        .overlay(AppSymbol.star.image.font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color(rgb: roles.onPrimaryContainer)))
                        .frame(width: 28, height: 28)
                    VStack(alignment: .leading, spacing: 4) {
                        Capsule().fill(Color(rgb: roles.onSurface)).frame(height: 6)
                        Capsule().fill(Color(rgb: roles.onSurfaceVariant)).frame(width: 40, height: 6)
                    }
                }
                .padding(8)
                .background(Color(rgb: roles.surfaceContainerHigh), in: .rect(cornerRadius: 10))
                Capsule().fill(Color(rgb: roles.primary)).frame(height: 28)
                    .overlay(Circle().fill(Color(rgb: roles.tertiary)).frame(width: 10).padding(.trailing, 10), alignment: .trailing)
            }
            .padding(12)
            .frame(maxWidth: .infinity)
            .background(Color(rgb: roles.surface), in: .rect(cornerRadius: ItmoKit.cardCorner))
            .overlay(RoundedRectangle(cornerRadius: ItmoKit.cardCorner).stroke(Color(rgb: roles.outlineVariant), lineWidth: 1))
            Text(verbatim: label).font(.itmo(.labelMedium)).foregroundStyle(.secondary)
        }
    }
}

private extension ThemeSpec {
    /// The same choices with [accent]: SKIE bridges no default arguments of `copy`.
    func with(accent: AccentColor) -> ThemeSpec {
        ThemeSpec(accent: accent, customArgb: customArgb, style: style, contrast: contrast, pureBlack: pureBlack)
    }
}

private extension Color {
    /// The opaque colour of a Kotlin `0xRRGGBB` (or `0xAARRGGBB`) int.
    init(rgb: Int32) {
        self.init(uiColor: UIColor(rgb: UInt32(bitPattern: rgb) & 0xFF_FFFF))
    }
}
