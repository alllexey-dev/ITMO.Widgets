import SwiftUI
import WidgetKit

/// The widget extension entry point (no Kotlin in this process): the next-lesson, day, cards, classic list, sport and
/// pass widgets and the pass Control.
@main
struct WidgetsBundle: WidgetBundle {
    var body: some Widget {
        NextLessonWidget()
        DayWidget()
        CardsWidget()
        ClassicListWidget()
        SportWidget()
        QrWidget()
        QrControl()
    }
}
