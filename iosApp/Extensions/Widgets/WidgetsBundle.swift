import SwiftUI
import WidgetKit

/// The widget extension entry point (no Kotlin in this process, ADR 0023). IO-10b and IO-11 add the lesson and day
/// widgets and the QR Control beside the QR widget.
@main
struct WidgetsBundle: WidgetBundle {
    var body: some Widget {
        QrWidget()
    }
}
