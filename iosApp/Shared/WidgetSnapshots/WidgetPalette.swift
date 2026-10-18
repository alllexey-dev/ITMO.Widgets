import Foundation

/// The app's colours for the widgets while `settings_widgets_theme_title` is on (docs/settings.md, Widgets): the
/// `palette` of every App Group widget file, Kotlin's `WidgetPalette`. Absent while the widgets keep their own colours.
struct WidgetPalette: Equatable, Decodable {
    let light: WidgetColorRoles
    let dark: WidgetColorRoles

    /// The roles of the light or the dark appearance.
    func roles(dark: Bool) -> WidgetColorRoles {
        dark ? self.dark : light
    }
}

/// The scheme roles the widgets draw with, each `0xRRGGBB`. The roles of the UX overhaul (the current lesson's
/// container, the lesson blocks, the ring's bonus arc) are optional: a file without them reads them as their
/// nearest older role, as Kotlin's defaults do.
struct WidgetColorRoles: Equatable, Decodable {
    let surface: UInt32
    let surfaceContainer: UInt32
    let onSurface: UInt32
    let onSurfaceVariant: UInt32
    let outlineVariant: UInt32
    let primary: UInt32
    let primaryContainer: UInt32
    let onPrimaryContainer: UInt32
    let surfaceContainerHigh: UInt32
    let tertiary: UInt32

    init(
        surface: UInt32, surfaceContainer: UInt32, onSurface: UInt32, onSurfaceVariant: UInt32,
        outlineVariant: UInt32, primary: UInt32, primaryContainer: UInt32? = nil, onPrimaryContainer: UInt32? = nil,
        surfaceContainerHigh: UInt32? = nil, tertiary: UInt32? = nil
    ) {
        self.surface = surface
        self.surfaceContainer = surfaceContainer
        self.onSurface = onSurface
        self.onSurfaceVariant = onSurfaceVariant
        self.outlineVariant = outlineVariant
        self.primary = primary
        self.primaryContainer = primaryContainer ?? surfaceContainer
        self.onPrimaryContainer = onPrimaryContainer ?? onSurface
        self.surfaceContainerHigh = surfaceContainerHigh ?? surfaceContainer
        self.tertiary = tertiary ?? primary
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        try self.init(
            surface: values.decode(UInt32.self, forKey: .surface),
            surfaceContainer: values.decode(UInt32.self, forKey: .surfaceContainer),
            onSurface: values.decode(UInt32.self, forKey: .onSurface),
            onSurfaceVariant: values.decode(UInt32.self, forKey: .onSurfaceVariant),
            outlineVariant: values.decode(UInt32.self, forKey: .outlineVariant),
            primary: values.decode(UInt32.self, forKey: .primary),
            primaryContainer: values.decodeIfPresent(UInt32.self, forKey: .primaryContainer),
            onPrimaryContainer: values.decodeIfPresent(UInt32.self, forKey: .onPrimaryContainer),
            surfaceContainerHigh: values.decodeIfPresent(UInt32.self, forKey: .surfaceContainerHigh),
            tertiary: values.decodeIfPresent(UInt32.self, forKey: .tertiary)
        )
    }

    private enum CodingKeys: String, CodingKey {
        case surface, surfaceContainer, onSurface, onSurfaceVariant, outlineVariant, primary
        case primaryContainer, onPrimaryContainer, surfaceContainerHigh, tertiary
    }
}
