import SwiftUI

/// How one QR widget entry looks: its colours, whether the system shows them, and how the code appears after the
/// spoiler. The entry view only draws what this says, so each option is tested here, without rendering.
struct QrWidgetStyle: Equatable {
    /// The tile's background and foreground in full colour.
    let tile: QrWidgetTile
    /// False on the tinted and clear home screens (`widgetRenderingMode` accented): the system keeps only each
    /// pixel's alpha there, so the tile paints no background and the code is drawn as holes in a light plate
    /// (`QrPlateShape`), which keeps it dark on light for a scanner.
    let fullColor: Bool
    /// How the code replaces the spoiler when a tap reveals it.
    let revealTransition: QrRevealTransition

    /// `dark` is the widget's colour scheme; `fullColor` whether the system renders the widget in full colour.
    init(entry: QrWidgetEntry, dark: Bool, fullColor: Bool) {
        switch entry.content {
        case .spoiler, .revealed:
            tile = .pass(dynamicColors: entry.appearance.dynamicColors, dark: dark)
        case .signedOut, .expired:
            tile = dark ? .dark : .light
        }
        self.fullColor = fullColor
        revealTransition = entry.appearance.animation == .none ? .none : .fade
    }
}

/// The reveal between two timeline entries. WidgetKit animates only between entries and draws no custom shape
/// transition, so Android's circle is a fade here as well; `NONE` swaps the two at once.
enum QrRevealTransition: Equatable {
    case fade
    case none

    var transition: AnyTransition {
        switch self {
        case .fade: .opacity
        case .none: .identity
        }
    }
}

/// The two colours of a QR widget tile, `0xRRGGBB`, and the noise shades between them.
struct QrWidgetTile: Equatable {
    let backgroundRGB: UInt32
    let foregroundRGB: UInt32

    /// The code without dynamic colours: black on white in every theme, Android's static colours.
    static let code = QrWidgetTile(backgroundRGB: 0xFFFFFF, foregroundRGB: 0x000000)
    /// The states without a code in the light theme.
    static let light = code
    /// The system's dark grouped surface with white, for the states without a code in the dark theme.
    static let dark = QrWidgetTile(backgroundRGB: 0x1C1C1E, foregroundRGB: 0xFFFFFF)

    /// The code and the spoiler: black on white, or with `dynamicColors` the app's scheme for the widget's theme.
    static func pass(dynamicColors: Bool, dark: Bool) -> QrWidgetTile {
        guard dynamicColors else { return code }
        let roles = dark ? ItmoColorRoles.dark : ItmoColorRoles.light
        return resolve(surface: roles.surface, onSurfaceVariant: roles.onSurfaceVariant, onSurface: roles.onSurface)
    }

    /// `QrColors.resolve` of `:shared:feature-qr` (Android's `QrColorResolver`): `surface` becomes the background and
    /// the darker of `onSurfaceVariant` and `onSurface` the modules; a dark `surface` swaps places with
    /// `onSurfaceVariant` first, so the code stays dark on light in the dark theme. The tokens are opaque, so the
    /// translucent fallback of the Kotlin math never applies.
    static func resolve(surface: UInt32, onSurfaceVariant: UInt32, onSurface: UInt32) -> QrWidgetTile {
        var background = surface
        var foreground = onSurfaceVariant
        if darkness(background) >= 0.5 {
            swap(&background, &foreground)
        }
        // A tie keeps the first, as `maxOf(a, b, comparator)` does in the resolver.
        if darkness(onSurface) > darkness(foreground) {
            foreground = onSurface
        }
        return QrWidgetTile(backgroundRGB: background, foregroundRGB: foreground)
    }

    var background: Color {
        Self.color(Self.channels(backgroundRGB))
    }

    var foreground: Color {
        Self.color(Self.channels(foregroundRGB))
    }

    /// Shade `index` of `QrNoisePattern.shadeCount`, evenly between the background and the foreground.
    func shade(_ index: Int) -> Color {
        let fraction = Double(index + 1) / Double(QrNoisePattern.shadeCount + 1)
        let from = Self.channels(backgroundRGB), to = Self.channels(foregroundRGB)
        func mix(_ from: Double, _ to: Double) -> Double { from + (to - from) * fraction }
        return Self.color((mix(from.red, to.red), mix(from.green, to.green), mix(from.blue, to.blue)))
    }

    /// The resolver's darkness on the 0...255 channels.
    private static func darkness(_ rgb: UInt32) -> Double {
        let (red, green, blue) = channels(rgb)
        return 1 - (0.299 * red + 0.587 * green + 0.114 * blue)
    }

    private static func channels(_ rgb: UInt32) -> (red: Double, green: Double, blue: Double) {
        (Double((rgb >> 16) & 0xFF) / 255, Double((rgb >> 8) & 0xFF) / 255, Double(rgb & 0xFF) / 255)
    }

    private static func color(_ channels: (red: Double, green: Double, blue: Double)) -> Color {
        Color(red: channels.red, green: channels.green, blue: channels.blue)
    }
}
