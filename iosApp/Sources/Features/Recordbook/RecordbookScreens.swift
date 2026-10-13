import Shared
import SwiftUI

/// The scores sheet (`AppRoutes.SheetScores`, `sheet_scores_title`, from the subject page's sheet total): the Compose
/// sheet content (`sheetScoresViewController`). It closes itself once the total is saved; a failed save says
/// why over it.
struct SheetScoresSheetView: View {
    let scores: SheetScoresArgs
    @Environment(\.dismiss) private var dismiss
    @State private var messages = ScheduleMessages()

    var body: some View {
        ComposeHost { [scores, dismiss, messages] in
            sheetScoresViewController(
                scores: scores,
                onClose: { dismiss() },
                saveFailed: { text in messages.show(text) }
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("recordbook.sheetScores")
        .scheduleMessages(messages)
        .recordbookSheet()
    }
}

extension View {
    /// A recordbook sheet: half height first and full height on a drag (Android's default sheet height), with the
    /// system's drag indicator over the Compose title row; a drag down closes it. The see-through Compose content shows
    /// the grouped background its `SheetScaffold` draws in the iOS style around itself.
    func recordbookSheet() -> some View {
        presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
            .presentationBackground(Color(uiColor: .systemGroupedBackground))
    }
}

extension ScheduleMessages {
    /// A message whose text the shared content resolved (a failed save of the scores sheet), read aloud too.
    func show(_ text: String) {
        message = SettingsMessage(text: text)
        AccessibilityNotification.Announcement(text).post()
    }
}
