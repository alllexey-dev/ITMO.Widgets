import SwiftUI
import WidgetKit

/// The widget extension entry point (no Kotlin in this process, ADR 0023). IO-10a/b and IO-11 add the QR, lesson and
/// day widgets and the QR Control; until then the bundle is empty and the widget gallery offers nothing.
@main
struct WidgetsBundle: WidgetBundle {
    var body: some Widget {}
}
