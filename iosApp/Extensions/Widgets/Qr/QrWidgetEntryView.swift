import SwiftUI
import WidgetKit

/// One entry of the QR widget. The whole tile is one surface, as Android's widget bitmap is: the code is always dark
/// on white in both themes (turnstile scanners read nothing else), the other states follow the system theme. A tap
/// outside the spoiler button opens the QR pass in the app (`QrWidget.passURL`).
///
/// Degradations against Android: the reveal has no circle animation (WidgetKit animates only between entries), and
/// a custom spoiler image is v2.4.
struct QrWidgetEntryView: View {
    let entry: QrWidgetEntry

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(tile.background)
            .containerBackground(for: .widget) { tile.background }
            .widgetURL(QrWidget.passURL)
    }

    @ViewBuilder
    private var content: some View {
        switch entry.content {
        case .signedOut:
            message(symbol: .qrCode, text: Text(.scheduleWidgetSignedOut))
        case .spoiler:
            Button(intent: RevealQrIntent()) { spoiler }
                .buttonStyle(.plain)
        case let .revealed(matrix, demo):
            code(matrix, demo: demo)
        case .expired:
            message(symbol: .refresh, text: Text(.iosWidgetQrExpired))
        }
    }

    private var spoiler: some View {
        VStack(spacing: Layout.captionPadding / 2) {
            noise
            caption(Text(.iosWidgetQrReveal))
        }
        .padding(Layout.codePadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
    }

    private var noise: some View {
        let pattern = QrNoisePattern(seed: UInt64(max(0, entry.date.timeIntervalSince1970) / 3600))
        return ZStack {
            ForEach(pattern.shades.indices, id: \.self) { shade in
                QrNoiseShape(squares: pattern.shades[shade]).fill(tile.shade(shade))
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    private func code(_ matrix: [[Bool]], demo: Bool) -> some View {
        VStack(spacing: Layout.captionPadding / 2) {
            QrModulesShape(matrix: matrix)
                .fill(tile.foreground)
                .aspectRatio(1, contentMode: .fit)
                .accessibilityElement()
                .accessibilityLabel(Text(.widgetQrCodeDescription))
            if demo {
                caption(Text(.StringsAuth.demoEntered))
            }
        }
        .padding(Layout.codePadding)
    }

    private func message(symbol: AppSymbol, text: Text) -> some View {
        VStack(spacing: Layout.captionPadding) {
            Image(systemName: symbol.systemName)
                .font(.title2)
                .accessibilityHidden(true)
            caption(text)
        }
        .padding(Layout.codePadding)
        .accessibilityElement(children: .combine)
    }

    private func caption(_ text: Text) -> some View {
        text
            .font(.caption.weight(.semibold))
            .foregroundStyle(tile.foreground)
            .multilineTextAlignment(.center)
            .lineLimit(3)
            .minimumScaleFactor(0.8)
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
    }

    private var tile: QrWidgetTile {
        if case .revealed = entry.content { return .code }
        return colorScheme == .dark ? .dark : .light
    }

    private enum Layout {
        /// Around the code: Android's 8 dp widget padding plus the code's quiet zone.
        static let codePadding: CGFloat = 14
        static let captionPadding: CGFloat = 8
    }
}

/// The two colours of a QR widget tile and the noise shades between them.
struct QrWidgetTile {
    let backgroundRGB: (red: Double, green: Double, blue: Double)
    let foregroundRGB: (red: Double, green: Double, blue: Double)

    /// The code: black on white in every theme.
    static let code = QrWidgetTile(backgroundRGB: (1, 1, 1), foregroundRGB: (0, 0, 0))
    static let light = code
    /// The system's dark grouped surface with white, for the states that show no code.
    static let dark = QrWidgetTile(backgroundRGB: (0.11, 0.11, 0.118), foregroundRGB: (1, 1, 1))

    var background: Color {
        Color(red: backgroundRGB.red, green: backgroundRGB.green, blue: backgroundRGB.blue)
    }

    var foreground: Color {
        Color(red: foregroundRGB.red, green: foregroundRGB.green, blue: foregroundRGB.blue)
    }

    /// Shade `index` of `QrNoisePattern.shadeCount`, evenly between the background and the foreground.
    func shade(_ index: Int) -> Color {
        let fraction = Double(index + 1) / Double(QrNoisePattern.shadeCount + 1)
        func mix(_ from: Double, _ to: Double) -> Double { from + (to - from) * fraction }
        return Color(
            red: mix(backgroundRGB.red, foregroundRGB.red),
            green: mix(backgroundRGB.green, foregroundRGB.green),
            blue: mix(backgroundRGB.blue, foregroundRGB.blue)
        )
    }
}
