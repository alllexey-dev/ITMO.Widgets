import SwiftUI
import WidgetKit

/// One entry of the QR widget. The whole tile is one surface, as Android's widget bitmap is: the code and the spoiler
/// take the QR widget options (`QrWidgetStyle`), dark on light in both themes (turnstile scanners read nothing else);
/// the other states follow the system theme. A tap outside the spoiler button opens the QR pass in the app
/// (`QrWidget.passURL`).
///
/// Degradations against Android: the reveal fades instead of the circle (WidgetKit animates only between entries),
/// and there is no custom spoiler image (iOS has no picker for it).
struct QrWidgetEntryView: View {
    let entry: QrWidgetEntry

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.widgetRenderingMode) private var renderingMode

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(style.fullColor ? tile.background : .clear)
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
                .transition(style.revealTransition.transition)
        case let .revealed(matrix, demo):
            code(matrix, demo: demo)
                .transition(style.revealTransition.transition)
        case .expired:
            message(symbol: .refresh, text: Text(.iosWidgetQrExpired))
        }
    }

    /// The pass under its spoiler: the code's symbol in a soft circle and its title; a tap reveals the code.
    private var spoiler: some View {
        VStack(spacing: Layout.captionPadding) {
            Image(systemName: AppSymbol.qrCode.systemName)
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(tile.foreground)
                .frame(width: 56, height: 56)
                .background(tile.foreground.opacity(0.12), in: .circle)
                .accessibilityHidden(true)
            caption(Text(.StringsSettings.settingsWidgetPassTitle))
        }
        .padding(Layout.codePadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint(Text(.iosWidgetQrReveal))
    }

    private func code(_ matrix: [[Bool]], demo: Bool) -> some View {
        VStack(spacing: Layout.captionPadding / 2) {
            modules(matrix)
                .aspectRatio(1, contentMode: .fit)
                .accessibilityElement()
                .accessibilityLabel(Text(.widgetQrCodeDescription))
            if demo {
                caption(Text(.StringsAuth.demoEntered))
            }
        }
        .padding(Layout.codePadding)
    }

    /// The code itself; on a tinted or clear home screen the modules are holes in a light plate.
    @ViewBuilder
    private func modules(_ matrix: [[Bool]]) -> some View {
        if style.fullColor {
            QrModulesShape(matrix: matrix).fill(tile.foreground)
        } else {
            QrPlateShape(matrix: matrix).fill(Color.white, style: FillStyle(eoFill: true))
        }
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

    private var style: QrWidgetStyle {
        QrWidgetStyle(entry: entry, dark: colorScheme == .dark, fullColor: renderingMode == .fullColor)
    }

    private var tile: QrWidgetTile {
        style.tile
    }

    private enum Layout {
        /// Around the code: Android's 8 dp widget padding plus the code's quiet zone.
        static let codePadding: CGFloat = 14
        static let captionPadding: CGFloat = 8
    }
}
