import Shared
import SwiftUI
import WidgetKit

/// The widgets gallery (`SettingsPage.WIDGETS`, `SettingsUiState.gallery`): a card per format with its live preview
/// on a wallpaper, `settings_widget_on_screen` once one is placed or `settings_widgets_how_to_add` (no app can place
/// a widget on iOS), and the settings button that opens the format's sheet; then the Lock Screen widgets with the
/// pass Control and the page's own rows. Stateless but for what it presents, so snapshot tests render it from fixture
/// states; `SettingsScreen` owns the ViewModel and the previews.
struct WidgetGalleryForm: View {
    let gallery: WidgetGallery
    /// The page's own sections below the cards (`settings_widgets_theme_title` with its note).
    let sections: [SettingSection]
    let previews: WidgetGalleryPreviews
    var pendingToggles: [SettingRowId: Bool] = [:]
    var actions = SettingsFormActions()
    /// The sheet of a format open from the start, for snapshot tests.
    var openFormat: WidgetFormat?

    @State private var sheet: WidgetFormat?
    @State private var showsHowToAdd = false

    var body: some View {
        ItmoScreenStack {
            ForEach(gallery.cards, id: \.format) { card in
                WidgetGalleryCard(
                    card: card,
                    previews: previews,
                    howToAdd: gallery.howToAdd,
                    onHowToAdd: { showsHowToAdd = true },
                    onSettings: { sheet = card.format }
                )
            }
            if gallery.lockScreen {
                ItmoSectionHeader(title: AppStrings.string("settings_widgets_lock_title"))
                    .padding(.top, ItmoSpacing.compact)
                WidgetLockScreenCard(entry: previews.next)
            }
            ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                SettingSectionView(section: section, pendingToggles: pendingToggles, actions: actions)
            }
        }
        .onAppear { if sheet == nil { sheet = openFormat } }
        .sheet(item: $sheet) { format in
            if let card = gallery.cards.first(where: { $0.format == format }) {
                WidgetSettingsSheet(card: card, previews: previews, pendingToggles: pendingToggles, actions: actions)
            }
        }
        .alert(Text(verbatim: AppStrings.string("settings_widgets_how_to_add")), isPresented: $showsHowToAdd) {
            Button(role: .close) {}
        } message: {
            Text(verbatim: AppStrings.string("settings_widgets_how_to_add_text"))
        }
    }
}

extension WidgetFormat: @retroactive Identifiable {
    public var id: String { name }
}

/// One format: the preview, the title and description, the placed state or how to add one, the settings button.
private struct WidgetGalleryCard: View {
    let card: WidgetCard
    let previews: WidgetGalleryPreviews
    let howToAdd: Bool
    let onHowToAdd: () -> Void
    let onSettings: () -> Void

    var body: some View {
        ItmoCard {
            WidgetPreview(format: card.format, previews: previews)
                .accessibilityIdentifier("settings.widgets.\(card.format.name.lowercased()).preview")
            HStack(spacing: ItmoSpacing.content) {
                VStack(alignment: .leading, spacing: ItmoSpacing.related) {
                    // The pill wraps under a long title rather than squeezing it.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: ItmoSpacing.related) { title; beta }
                        VStack(alignment: .leading, spacing: ItmoSpacing.related) { title; beta }
                    }
                    Text(verbatim: card.description_.resolved)
                        .font(.itmo(.bodyMedium))
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: ItmoSpacing.related)
                action
                if !card.settings.isEmpty {
                    Button(action: onSettings) {
                        AppSymbol.tune.image
                            .accessibilityLabel(Text(verbatim: AppStrings.string("settings_widget_settings")))
                    }
                    .buttonStyle(.glass)
                    .buttonBorderShape(.circle)
                    .accessibilityIdentifier("settings.widgets.\(card.format.name.lowercased()).settings")
                }
            }
            .padding(.top, ItmoSpacing.related)
        }
        // A container keeps the children's own identifiers (the preview, the settings button).
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("settings.widgets.\(card.format.name.lowercased())")
    }

    private var title: some View {
        Text(verbatim: card.title.resolved)
            .font(.itmo(.titleMedium, emphasized: true))
    }

    @ViewBuilder
    private var beta: some View {
        if card.beta {
            ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("settings_widget_beta"), tone: .tertiary))
                .accessibilityIdentifier("settings.widgets.\(card.format.name.lowercased()).beta")
        }
    }

    @ViewBuilder
    private var action: some View {
        switch card.action {
        case .onScreen:
            ItmoStatusPill(pill: ItmoPill(text: AppStrings.string("settings_widget_on_screen"), tone: .ok, symbol: .check))
        case .add, .none:
            // iOS places no widget for the user: the hint says how, Android's add button never shows here.
            if howToAdd {
                Button(action: onHowToAdd) {
                    Text(verbatim: AppStrings.string("settings_widgets_how_to_add"))
                }
                .buttonStyle(.glass)
                .controlSize(.small)
            }
        }
    }
}

/// The real widget of `format` on its sample, at its family size, scaled into a wallpaper-like stage.
struct WidgetPreview: View {
    let format: WidgetFormat
    let previews: WidgetGalleryPreviews

    var body: some View {
        switch format {
        case .next:
            WidgetStage(size: WidgetFamilySizes.medium) {
                previews.next.map { NextLessonEntryView(entry: $0, family: .systemMedium) }
            }
        case .day:
            WidgetStage(size: WidgetFamilySizes.large) {
                previews.day.map { DayEntryView(entry: $0) }
            }
        case .list:
            WidgetStage(size: WidgetFamilySizes.medium) {
                previews.list.map { ClassicListEntryView(entry: $0, family: .systemMedium) }
            }
        case .sport:
            WidgetStage(size: WidgetFamilySizes.medium) {
                previews.sport.map { SportEntryView(entry: $0, family: .systemMedium) }
            }
        case .pass:
            WidgetStage(size: WidgetFamilySizes.small) {
                previews.pass.map { QrWidgetEntryView(entry: $0) }
            }
        }
    }
}

/// The family sizes of the pinned phone (`WidgetSizes` in the snapshot tests), the size a preview is laid out at.
enum WidgetFamilySizes {
    static let small = CGSize(width: 170, height: 170)
    static let medium = CGSize(width: 364, height: 170)
    static let large = CGSize(width: 364, height: 382)
}

extension EnvironmentValues {
    /// The system's light or dark mode where the app's theme mode forces one (`ShellView`), nil where the app
    /// follows the system: a placed widget follows the system, so the gallery draws its widgets in it.
    @Entry var systemColorScheme: ColorScheme?
}

/// A widget laid out at `size`, shrunk to the width it gets, with the home screen's corner, on a wallpaper of the
/// app's container roles; the widget in the system's mode, the wallpaper in the app's. Nothing in it is a button: the
/// gallery's own controls are below it.
struct WidgetStage<Content: View>: View {
    let size: CGSize
    var lock = false
    @ViewBuilder let content: Content
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.systemColorScheme) private var systemColorScheme

    var body: some View {
        Color.clear
            .aspectRatio(size.width / size.height, contentMode: .fit)
            .frame(maxWidth: size.width)
            .overlay(alignment: .topLeading) {
                GeometryReader { geometry in
                    let scale = geometry.size.width / size.width
                    content
                        .environment(\.colorScheme, systemColorScheme ?? colorScheme)
                        .frame(width: size.width, height: size.height)
                        .clipShape(.rect(cornerRadius: 22, style: .continuous))
                        .scaleEffect(scale, anchor: .topLeading)
                }
            }
            .allowsHitTesting(false)
            .padding(ItmoSpacing.cardPadding)
            .frame(maxWidth: .infinity)
            .background(WidgetWallpaper.gradient(lock: lock), in: .rect(cornerRadius: 18, style: .continuous))
            .accessibilityElement(children: .combine)
    }
}

/// A home or Lock Screen backdrop in the app's colours.
enum WidgetWallpaper {
    static func gradient(lock: Bool = false) -> LinearGradient {
        LinearGradient(
            colors: lock
                ? [ItmoColor.onPrimaryContainer, ItmoColor.inverseSurface]
                : [ItmoColor.tertiaryContainer, ItmoColor.primaryContainer, ItmoColor.secondaryContainer],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

/// The Lock Screen widgets on the sample (the line beside the date, the rectangle, the ring) and the pass Control.
private struct WidgetLockScreenCard: View {
    let entry: ScheduleWidgetEntry?

    var body: some View {
        ItmoCard {
            if let entry {
                VStack(spacing: ItmoSpacing.content) {
                    NextLessonEntryView(entry: entry, family: .accessoryInline)
                        .font(.headline)
                    HStack(spacing: ItmoSpacing.content) {
                        NextLessonEntryView(entry: entry, family: .accessoryRectangular)
                            .padding(.horizontal, 10)
                            .frame(width: 172, height: 72)
                            .background(.white.opacity(0.18), in: .rect(cornerRadius: 18, style: .continuous))
                        NextLessonEntryView(entry: entry, family: .accessoryCircular)
                            .frame(width: 72, height: 72)
                            .background(.white.opacity(0.18), in: .circle)
                        AppSymbol.qrCode.image
                            .font(.title3.weight(.semibold))
                            .frame(width: 52, height: 52)
                            .background(.white.opacity(0.18), in: .circle)
                            .accessibilityHidden(true)
                    }
                }
                .foregroundStyle(.white)
                .environment(\.colorScheme, .dark)
                .allowsHitTesting(false)
                .padding(ItmoSpacing.cardPadding)
                .frame(maxWidth: .infinity)
                .background(WidgetWallpaper.gradient(lock: true), in: .rect(cornerRadius: 18, style: .continuous))
            }
            Text(verbatim: AppStrings.string("settings_widgets_lock_text"))
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
            Text(verbatim: AppStrings.string("settings_widgets_control_text"))
                .font(.itmo(.bodyMedium))
                .foregroundStyle(.secondary)
        }
        .accessibilityIdentifier("settings.widgets.lock")
    }
}

/// A format's sheet: its live preview on the wallpaper, the note, and its rows; every change applies at once and
/// redraws the preview.
struct WidgetSettingsSheet: View {
    let card: WidgetCard
    let previews: WidgetGalleryPreviews
    var pendingToggles: [SettingRowId: Bool] = [:]
    var actions = SettingsFormActions()

    var body: some View {
        ItmoSheet(title: card.title.resolved, height: .tall) {
            WidgetSettingsSheetContent(card: card, previews: previews, pendingToggles: pendingToggles, actions: actions)
        }
    }
}

/// The body of `WidgetSettingsSheet`, apart from the presentation for snapshot tests.
struct WidgetSettingsSheetContent: View {
    let card: WidgetCard
    let previews: WidgetGalleryPreviews
    var pendingToggles: [SettingRowId: Bool] = [:]
    var actions = SettingsFormActions()

    var body: some View {
        VStack(alignment: .leading, spacing: ItmoSpacing.content) {
            WidgetPreview(format: card.format, previews: previews)
            if let note = card.settingsNote?.resolved {
                Text(verbatim: note)
                    .font(.itmo(.bodySmall))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, ItmoSpacing.content)
            }
            ForEach(Array(card.settings.enumerated()), id: \.offset) { _, section in
                SettingSectionView(section: section, pendingToggles: pendingToggles, actions: actions)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("settings.widgets.sheet.\(card.format.name.lowercased())")
    }
}
