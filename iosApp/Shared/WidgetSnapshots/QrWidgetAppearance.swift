import Foundation

/// The QR widget options of the settings page (docs/settings.md, QR widget), as the app writes them into
/// qr-pass-v1.json beside the pass. Android keeps them global, so one value serves every placed widget. Each field is
/// optional in the file; an absent one is Android's default.
struct QrWidgetAppearance: Equatable {
    /// `settings_qr_spoiler_title`: the code shows only after a tap.
    var spoiler: Bool
    /// `settings_qr_dynamic_colors_title`: the code and the spoiler take the app's scheme, not black on white.
    var dynamicColors: Bool
    /// `settings_qr_animation_title`.
    var animation: QrRevealAnimation
    /// The app's colours while the widgets follow the theme; with `dynamicColors` they replace the brand scheme.
    var palette: WidgetPalette? = nil

    /// Android's defaults: the spoiler on, dynamic colours on, the circle.
    static let standard = QrWidgetAppearance(spoiler: true, dynamicColors: true, animation: .circle)
}

/// `QrAnimationType` of `:shared:core` by its enum name; a name this build does not know is the default circle.
enum QrRevealAnimation: String, Equatable {
    case circle = "CIRCLE"
    case fade = "FADE"
    case none = "NONE"

    init(name: String?) {
        self = name.flatMap(Self.init(rawValue:)) ?? .circle
    }
}
