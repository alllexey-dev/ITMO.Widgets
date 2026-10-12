import SwiftUI
import WidgetKit

/// The widget extension entry point (no Kotlin in this process): the QR widget, the lesson and the day
/// widget and the QR Control.
@main
struct WidgetsBundle: WidgetBundle {
    var body: some Widget {
        QrWidget()
        SingleLessonWidget()
        DayScheduleWidget()
        QrControl()
    }
}
