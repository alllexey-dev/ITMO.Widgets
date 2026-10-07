import SwiftUI
import WidgetKit

/// The widget extension entry point (no Kotlin in this process, ADR 0023): the QR widget and the QR Control. IO-10b
/// adds the lesson and day widgets.
@main
struct WidgetsBundle: WidgetBundle {
    var body: some Widget {
        QrWidget()
        QrControl()
    }
}
