import AppIntents
import SwiftUI
import WidgetKit

/// The QR pass in Control Center, on the Lock Screen and on the Action button: the iOS counterpart of Android's QR
/// tile (`qr_tile_label`). It opens the app on the pass above home (`OpenRouteIntent`, route `qr_pass`); like the
/// tile it cannot draw the code itself.
struct QrControl: ControlWidget {
    /// A stable identifier (`StableIdentifiersTests`): placed Controls keep it.
    static let kind = "dev.alllexey.itmowidgets.control.qr"

    /// The button's action.
    static var action: OpenRouteIntent { OpenRouteIntent(route: .qrPass) }

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind) {
            ControlWidgetButton(action: Self.action) {
                Label {
                    Text(.qrTileLabel)
                } icon: {
                    Image(systemName: AppSymbol.qrCode.systemName)
                }
            }
        }
        .displayName(.qrTileLabel)
        .description(.iosControlQrDescription)
    }
}
