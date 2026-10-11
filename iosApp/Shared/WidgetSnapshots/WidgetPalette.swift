import Foundation

/// The app's colours for the widgets while `settings_widgets_theme_title` is on (docs/settings.md, Widgets): the `palette` of
/// `qr-pass-v1.json` and `schedule-timeline-v1.json`, Kotlin's `WidgetPalette`. Absent while the widgets keep their
/// own colours; an additive field of version 1, so an older widget ignores it.
struct WidgetPalette: Equatable, Decodable {
    let light: WidgetColorRoles
    let dark: WidgetColorRoles

    /// The roles of the light or the dark appearance.
    func roles(dark: Bool) -> WidgetColorRoles {
        dark ? self.dark : light
    }
}

/// The few scheme roles the widgets draw with, each `0xRRGGBB`.
struct WidgetColorRoles: Equatable, Decodable {
    let surface: UInt32
    let surfaceContainer: UInt32
    let onSurface: UInt32
    let onSurfaceVariant: UInt32
    let outlineVariant: UInt32
    let primary: UInt32
}
