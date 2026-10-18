import SwiftUI
import WidgetKit

/// The widget extension entry point (no Kotlin in this process): the next-lesson, day, sport and pass widgets and
/// the pass Control.
@main
struct WidgetsBundle: WidgetBundle {
    var body: some Widget {
        NextLessonWidget()
        DayWidget()
        SportWidget()
        QrWidget()
        QrControl()
    }
}
