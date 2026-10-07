import SwiftUI
import WidgetKit

/// The widget extension entry point (no Kotlin in this process, ADR 0023): the QR widget, the lesson and the day
/// widget (IO-10b) and the QR Control (IO-11).
@main
struct WidgetsBundle: WidgetBundle {
    var body: some Widget {
        QrWidget()
        SingleLessonWidget()
        DayScheduleWidget()
        QrControl()
    }
}
